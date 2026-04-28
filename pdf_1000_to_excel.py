#!/usr/bin/env python3
"""
Convert 肖秀荣1000题-style PDFs (question booklet + analysis booklet) into the
project's importable Excel format.

This parser targets objective questions only (single-choice + multiple-choice).
Material analysis questions are intentionally skipped because the website does
not support them.
"""

from __future__ import annotations

import argparse
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable

import dataset_to_excel as excel_converter
import politics_syllabus
from pypdf import PdfReader


SECTION_CONFIG = {
    "marxism": {
        "root_id": 1,
        "patterns": (r"^(?:第[一1]部分\s*)?马克思主义基本原理(?:第[一1]部分)?$", r"^第[一1]部分.*马克思主义基本原理$"),
        "chapters": {
            0: "目录",
            1: "专题一：马克思主义观",
            2: "专题二：辩证唯物主义世界观",
            3: "专题三：辩证唯物主义认识论",
            4: "专题四：唯物史观",
            5: "专题五：资本主义论（上）",
            6: "专题六：资本主义论（下）",
            7: "专题七：社会主义论",
            8: "专题八：共产主义论",
        },
    },
    "maozhongte": {
        "root_id": 2,
        "patterns": (r"^毛泽.*第二部分$", r"^第二部分.*毛泽.*$", r"^毛泽东思想和中国特色社会主义理论体系概论.*$"),
        "chapters": {
            0: "毛概：导论",
            1: "第一章 毛泽东思想及其历史地位",
            2: "第二章 新民主主义革命理论",
            3: "第三章 社会主义改造理论",
            4: "第四章 社会主义建设道路初步探索的理论成果",
            5: "第五章 中国特色社会主义理论体系的形成发展",
            6: "第六章 邓小平理论",
            7: "第七章 “三个代表”重要思想",
            8: "第八章 科学发展观",
        },
    },
    "xijinping": {
        "root_id": 2,
        "patterns": (r"^习近平新时代中国特色社会主义思想概论$", r"^第三部分.*习近平.*$", r"^第三部分.*新时.*思想.*$"),
        "chapters": {
            0: "习思：导论",
            1: "第1章 新时代坚持和发展中国特色社会主义",
            2: "第2章 以中国式现代化全面推进中华民族伟大复兴",
            3: "第3章 坚持党的全面领导",
            4: "第4章 坚持以人民为中心",
            5: "第5章 全面深化改革开放",
            6: "第6章 推动高质量发展",
            7: "第7章 社会主义现代化建设的教育、科技、人才战略",
            8: "第8章 发展全过程人民民主",
            9: "第9章 全面依法治国",
            10: "第10章 建设社会主义文化强国",
            11: "第11章 以保障和改善民生为重点加强社会建设",
            12: "第12章 建设社会主义生态文明",
            13: "第13章 维护和塑造国家安全",
            14: "第14章 建设巩固国防和强大人民军队",
            15: "第15章 坚持“一国两制”和推进祖国完全统一",
            16: "第16章 中国特色大国外交和推动构建人类命运共同体",
            17: "第17章 全面从严治党",
        },
    },
    "history": {
        "root_id": 3,
        "patterns": (r"^(?:第四部分\s*)?中国近现代史纲要(?:第四部分)?$", r"^第四部分.*中国近现代史纲要$"),
        "chapters": {
            0: "导言",
            1: "第一章 进入近代后中华民族的磨难与抗争",
            2: "第二章 不同社会力量对国家出路的早期探索",
            3: "第三章 辛亥革命与君主专制制度的终结",
            4: "第四章 中国共产党成立和中国革命新局面",
            5: "第五章 中国革命的新道路",
            6: "第六章 中华民族的抗日战争",
            7: "第七章 为建立新中国而奋斗",
            8: "第八章 中华人民共和国的成立与中国社会主义建设道路的探索",
            9: "第九章 改革开放与中国特色社会主义的开创和发展",
            10: "第十章 中国特色社会主义进入新时代",
        },
    },
    "ethics": {
        "root_id": 4,
        "patterns": (r"^(?:第五部分\s*)?思想道德与法治(?:第五部分)?$", r"^思想道德与法治.*$"),
        "chapters": {
            0: "绪论",
            1: "领悟人生真谛 把握人生方向",
            2: "追求远大理想 坚定崇高信念",
            3: "继承优良传统 弘扬中国精神",
            4: "明确价值要求 践行价值准则",
            5: "遵守道德规范 锤炼道德品格",
            6: "学习法治思想 提升法治素养",
        },
    },
}

SECTION_ORDER = ("marxism", "maozhongte", "xijinping", "history", "ethics")
MATERIAL_ANALYSIS_MARKERS = ("三、材料分析题", "材料分析题")

TEXT_REPLACEMENTS = (
    ("导 论", "导论"),
    ("第一童", "第一章"),
    ("第二童", "第二章"),
    ("第三童", "第三章"),
    ("第四童", "第四章"),
    ("第五童", "第五章"),
    ("第六童", "第六章"),
    ("第七童", "第七章"),
    ("第八童", "第八章"),
    ("第九童", "第九章"),
    ("第十童", "第十章"),
    ("第十一童", "第十一章"),
    ("第十二童", "第十二章"),
    ("第十三童", "第十三章"),
    ("第十四童", "第十四章"),
    ("第九章］", "第九章 "),
    ("第十三章、", "第十三章 "),
    ("导 论", "导论"),
    ("京应平新岛代中国猫曲会主义思就概论", "习近平新时代中国特色社会主义思想概论"),
    ("毛泽岁思想那国瘾爸在近义理论体系概Q", "毛泽东思想和中国特色社会主义理论体系概论"),
)


@dataclass
class ParsedQuestion:
    section: str
    type_value: int
    number: int
    chapter: str
    stem: str
    options: list[str]


@dataclass
class ParsedAnalysis:
    section: str
    type_value: int
    number: int
    chapter: str
    answer: str
    analysis: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert 肖秀荣1000题 PDF booklet + analysis to importable Excel.")
    parser.add_argument("--question-pdf", required=True, help="Question booklet PDF")
    parser.add_argument("--analysis-pdf", required=True, help="Analysis booklet PDF")
    parser.add_argument("--output", required=True, help="Output .xlsx path")
    parser.add_argument("--source", default="26版《肖秀荣1000题》", help="Question source")
    parser.add_argument("--source-type", default="模拟题", help="Source type label")
    parser.add_argument("--difficulty", default="medium", help="Fallback difficulty")
    parser.add_argument("--status", type=int, default=1, help="Fallback status")
    parser.add_argument("--category-id", type=int, default=1, help="Fallback root category")
    return parser.parse_args()


def extract_pdf_lines(pdf_path: Path, *, layout: bool = False) -> list[str]:
    reader = PdfReader(str(pdf_path))
    lines: list[str] = []
    for page_number, page in enumerate(reader.pages, start=1):
        if layout:
            try:
                text = page.extract_text(extraction_mode="layout") or ""
            except TypeError:
                text = page.extract_text() or ""
        else:
            text = page.extract_text() or ""
        lines.append(f"=== PAGE {page_number} ===")
        lines.extend(text.splitlines())
    return lines


def normalize_line(line: str) -> str:
    normalized = normalize_buffer_line(line)
    normalized = re.sub(r"\s+", " ", normalized)
    return normalized.strip()


def normalize_buffer_line(line: str) -> str:
    normalized = line.strip()
    for old, new in TEXT_REPLACEMENTS:
        normalized = normalized.replace(old, new)
    normalized = normalized.replace("\u3000", " ")
    normalized = re.sub(r"^(\d{1,2})s(?=[.．、，,\-\s])", r"\g<1>3", normalized, flags=re.IGNORECASE)
    normalized = re.sub(r"[·•◆★☆■□]+", "", normalized)
    return normalized.strip()


def detect_section(line: str, current_section: str | None) -> str | None:
    if len(line) > 60:
        return None
    for key in SECTION_ORDER:
        for pattern in SECTION_CONFIG[key]["patterns"]:
            if re.match(pattern, line):
                return key
    return None


def detect_type(line: str) -> int | None:
    if len(line) <= 90 and "单项选择题" in line:
        return 1
    if len(line) <= 90 and "多项选择题" in line:
        return 5
    return None


def chinese_to_int(text: str) -> int | None:
    numerals = {"零": 0, "一": 1, "二": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9}
    text = text.strip()
    if text.isdigit():
        return int(text)
    if text == "十":
        return 10
    if "十" in text:
        left, right = text.split("十", 1)
        tens = 1 if not left else numerals.get(left)
        ones = 0 if not right else numerals.get(right)
        if tens is None or ones is None:
            return None
        return tens * 10 + ones
    return numerals.get(text)


def build_expected_chapter(section: str, number: int) -> str | None:
    return SECTION_CONFIG.get(section, {}).get("chapters", {}).get(number)


def detect_chapter(line: str, section: str | None) -> str | None:
    if not section:
        return None
    if len(line) > 40:
        return None
    if line in {"导论", "绪论"}:
        return line
    match = re.match(r"^第([一二三四五六七八九十0-9]+)章\s*(.*)$", line)
    if match:
        number = chinese_to_int(match.group(1))
        title = match.group(2).strip()
        if title:
            return title
        return build_expected_chapter(section, number or 0)
    expected_titles = SECTION_CONFIG.get(section, {}).get("chapters", {}).values()
    for title in expected_titles:
        if title and title in line:
            return title
    return None


def parse_question_blocks(lines: Iterable[str], valid_numbers: dict[tuple[str, int], set[int]] | None = None) -> list[ParsedQuestion]:
    parsed: list[ParsedQuestion] = []
    current_section: str | None = None
    current_type: int | None = None
    current_chapter = ""
    current_number: int | None = None
    buffer: list[str] = []
    expected_next: int | None = None

    def flush() -> None:
        nonlocal buffer, current_number
        if current_section and current_type and current_number is not None and buffer:
            stem, options = parse_question_text(buffer)
            if stem and len(options) >= 2:
                parsed.append(
                    ParsedQuestion(
                        section=current_section,
                        type_value=current_type,
                        number=current_number,
                        chapter=current_chapter,
                        stem=stem,
                        options=options[:4],
                    )
                )
        buffer = []
        current_number = None

    for raw_line in lines:
        line = normalize_line(raw_line)
        buffer_line = normalize_buffer_line(raw_line)
        if not line or line.startswith("=== PAGE"):
            continue

        section = detect_section(line, current_section)
        if section:
            flush()
            current_section = section
            current_type = None
            current_chapter = ""
            expected_next = None
            continue

        if any(marker in line for marker in MATERIAL_ANALYSIS_MARKERS):
            flush()
            current_type = None
            expected_next = None
            continue

        type_value = detect_type(line)
        if type_value:
            flush()
            current_type = type_value
            expected_next = 1
            current_chapter = ""
            continue

        chapter = detect_chapter(line, current_section)
        if chapter:
            flush()
            current_chapter = chapter
            continue

        question_match = re.match(r"^(\d{1,3})\s*[.．、，,\-]?\s*(.*)$", line)
        buffer_match = re.match(r"^(\d{1,3})\s*[.．、，,\-]?\s*(.*)$", buffer_line)
        if current_type and question_match:
            number = int(question_match.group(1))
            valid_for_section = valid_numbers.get((current_section, current_type), set()) if valid_numbers and current_section else None
            is_valid_number = valid_for_section is None or number in valid_for_section
            should_start_question = False
            if valid_for_section is not None:
                should_start_question = is_valid_number and number != current_number
            else:
                should_start_question = is_valid_number and (
                    expected_next is None
                    or number == expected_next
                    or (current_number is not None and number > current_number)
                )
            if should_start_question:
                flush()
                current_number = number
                expected_next = number + 1
                remainder = (buffer_match.group(2) if buffer_match else question_match.group(2)).strip()
                buffer = [remainder] if remainder else []
                continue

        if current_number is not None:
            buffer.append(buffer_line)

    flush()
    return parsed


def parse_question_text(lines: list[str]) -> tuple[str, list[str]]:
    cleaned = [line for line in lines if line and not line.startswith("题目考查知识点")]
    if not cleaned:
        return "", []

    joined = " ".join(cleaned)
    joined = re.sub(r"(?<![A-Z])([A-D6])[,，:：]\s*", r"\1. ", joined)
    joined = re.sub(r"\s+", " ", joined).strip()

    inline_labels = list(re.finditer(r"([A-D6])[.．]\s*", joined))
    if len(inline_labels) >= 2:
        relevant = inline_labels[:4]
        stem = joined[: relevant[0].start()].strip()
        options: list[str] = []
        for index, match in enumerate(relevant):
            end = relevant[index + 1].start() if index + 1 < len(relevant) else len(joined)
            option_text = joined[match.end() : end].strip()
            options.append(re.sub(r"\s+", " ", option_text))
        if len([item for item in options if item]) >= 2:
            return stem, options

    label_only_index = None
    label_count = 0
    for idx, line in enumerate(cleaned):
        if re.fullmatch(r"(?:[A-D6][.．]\s*){2,4}", line.replace(" ", "")):
            label_only_index = idx
            label_count = len(re.findall(r"[A-D6][.．]", line.replace(" ", "")))
            break
    if label_only_index is not None:
        prefix_lines = cleaned[:label_only_index]
        max_option_line_count = min(4, len(prefix_lines))
        for option_line_count in range(1, max_option_line_count + 1):
            stem_lines = prefix_lines[:-option_line_count]
            option_lines = prefix_lines[-option_line_count:]
            if not stem_lines:
                continue
            option_parts: list[str] = []
            for option_line in option_lines:
                option_parts.extend(split_label_only_option_line(option_line))
            if len(option_parts) != label_count:
                continue
            stem = " ".join(re.sub(r"\s+", " ", value).strip() for value in stem_lines).strip()
            options = [re.sub(r"\s+", " ", value).strip() for value in option_parts]
            return stem, options

    return " ".join(cleaned).strip(), []


def split_label_only_option_line(line: str) -> list[str]:
    parts = [item.strip() for item in re.split(r"\s{2,}", line) if item.strip()]
    if parts:
        return parts
    return [line.strip()] if line.strip() else []


def parse_analysis_blocks(lines: Iterable[str]) -> dict[tuple[str, int, int], ParsedAnalysis]:
    parsed: dict[tuple[str, int, int], ParsedAnalysis] = {}
    current_section: str | None = None
    current_type: int | None = None
    current_chapter = ""
    current_key: tuple[str, int, int] | None = None
    current_answer = ""
    buffer: list[str] = []
    expected_next: int | None = None

    def flush() -> None:
        nonlocal current_key, buffer
        if current_key:
            analysis = clean_analysis_text(buffer)
            parsed[current_key] = ParsedAnalysis(
                section=current_key[0],
                type_value=current_key[1],
                number=current_key[2],
                chapter=current_chapter,
                answer=current_answer,
                analysis=analysis,
            )
        current_key = None
        buffer = []

    for raw_line in lines:
        line = normalize_line(raw_line)
        if not line or line.startswith("=== PAGE"):
            continue

        section = detect_section(line, current_section)
        if section:
            flush()
            current_section = section
            current_type = None
            current_chapter = ""
            expected_next = None
            continue

        if any(marker in line for marker in MATERIAL_ANALYSIS_MARKERS):
            flush()
            current_type = None
            expected_next = None
            continue

        type_value = detect_type(line)
        if type_value:
            flush()
            current_type = type_value
            expected_next = 1
            current_chapter = ""
            continue

        chapter = detect_chapter(line, current_section)
        if chapter:
            flush()
            current_chapter = chapter
            continue

        answer_match = re.match(r"^(\d{1,3})\s*[.．]?\s*答案\s*([A-D]+)", line)
        if current_section and current_type and answer_match:
            number = int(answer_match.group(1))
            if expected_next is None or number == expected_next:
                flush()
                current_answer = answer_match.group(2).upper()
                current_key = (current_section, current_type, number)
                expected_next = number + 1
                continue

        if current_key:
            buffer.append(line)

    flush()
    return parsed


def clean_analysis_text(lines: list[str]) -> str:
    kept: list[str] = []
    for line in lines:
        if line.startswith("出处"):
            continue
        if line.startswith("点拨"):
            continue
        if not kept and line.startswith("简析"):
            line = re.sub(r"^简析[:：]?\s*", "", line)
        kept.append(line)
    return "\n".join(kept).strip()


def repair_number_only_chapter(section: str, chapter_name: str, stem: str, analysis: str) -> str:
    if section != "maozhongte":
        if section != "ethics":
            return chapter_name
        if chapter_name.strip() != "i":
            return chapter_name
        combined = f"{stem}\n{analysis}"
        if any(keyword in combined for keyword in ("民族精神", "时代精神", "爱国主义", "中国精神")):
            return "第三章 继承优良传统 弘扬中国精神"
        if any(keyword in combined for keyword in ("核心价值观", "富强、民主、文明、和谐", "自由、平等、公正、法治")):
            return "第四章 明确价值要求 践行价值准则"
        return chapter_name
    if not re.fullmatch(r"第[一二三四五六七八九十0-9]+章", chapter_name.strip()):
        return chapter_name

    combined = f"{stem}\n{analysis}"
    if any(keyword in combined for keyword in ("政治发展道路", "人民当家作主", "依法治国有机统一")):
        return "第8章 发展全过程人民民主"
    if "科学发展观" in combined:
        return "第八章 科学发展观"
    if "三个代表" in combined or "执政兴国的第一要务" in combined:
        return "第七章 “三个代表”重要思想"
    if any(keyword in combined for keyword in ("改革、发展、稳定", "邓小平理论", "社会主义本质", "社会主义初级阶段")):
        return "第六章 邓小平理论"
    return chapter_name


def build_records(
    questions: list[ParsedQuestion],
    analysis_map: dict[tuple[str, int, int], ParsedAnalysis],
    source: str,
    source_type: str,
    fallback_category_id: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for question in questions:
        key = (question.section, question.type_value, question.number)
        analysis = analysis_map.get(key)
        if not analysis:
            continue

        root_id = SECTION_CONFIG[question.section]["root_id"]
        raw_chapter_name = repair_number_only_chapter(
            question.section,
            analysis.chapter or question.chapter,
            question.stem,
            analysis.analysis,
        )
        chapter_name = politics_syllabus.resolve_canonical_chapter_name(
            root_id,
            raw_chapter_name,
            section_hint=question.section,
        ) or raw_chapter_name
        chapter_id = politics_syllabus.resolve_canonical_category_id(
            root_id,
            raw_chapter_name,
            section_hint=question.section,
        )

        record: dict[str, Any] = {
            "title": question.stem,
            "type": "多选" if question.type_value == 5 else "单选",
            "xuan_text": "|".join(f"{label}.{value}" for label, value in zip(("A", "B", "C", "D"), question.options, strict=False)),
            "right_text": analysis.answer,
            "jiexi": analysis.analysis,
            "source": source,
            "sourceType": source_type,
            "categoryId": chapter_id or root_id or fallback_category_id,
            "__source_stem__": source,
        }
        if chapter_name:
            record["p_kaodian_text"] = chapter_name
        records.append(record)
    return records


def score_question_block(question: ParsedQuestion) -> tuple[int, int, int]:
    return (
        sum(1 for option in question.options if option),
        len(question.stem),
        len(question.chapter or ""),
    )


def merge_question_blocks(*block_groups: list[ParsedQuestion]) -> list[ParsedQuestion]:
    merged: dict[tuple[str, int, int], ParsedQuestion] = {}
    for block_group in block_groups:
        for question in block_group:
            key = (question.section, question.type_value, question.number)
            current = merged.get(key)
            if current is None or score_question_block(question) > score_question_block(current):
                merged[key] = question
    return list(merged.values())


def main() -> int:
    args = parse_args()

    analysis_lines = extract_pdf_lines(Path(args.analysis_pdf), layout=False)
    analysis_blocks = parse_analysis_blocks(analysis_lines)
    valid_numbers: dict[tuple[str, int], set[int]] = {}
    for section, type_value, number in analysis_blocks:
        valid_numbers.setdefault((section, type_value), set()).add(number)
    question_lines_layout = extract_pdf_lines(Path(args.question_pdf), layout=True)
    question_lines_default = extract_pdf_lines(Path(args.question_pdf), layout=False)
    question_blocks = merge_question_blocks(
        parse_question_blocks(question_lines_layout, valid_numbers),
        parse_question_blocks(question_lines_default, valid_numbers),
    )
    source_type = excel_converter.normalize_source_type(args.source_type, args.source, "")
    default_difficulty = excel_converter.normalize_difficulty(args.difficulty, 2)

    records = build_records(
        questions=question_blocks,
        analysis_map=analysis_blocks,
        source=args.source,
        source_type=source_type,
        fallback_category_id=args.category_id,
    )

    rows = [excel_converter.HEADERS]
    for record in records:
        row = excel_converter.convert_record(
            record=record,
            mapping={},
            default_category_id=args.category_id,
            default_source=args.source,
            default_source_type=source_type,
            default_difficulty=default_difficulty,
            default_status=args.status,
            category_mode="top",
        )
        if row:
            rows.append(row)

    output_path = Path(args.output).resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    excel_converter.write_xlsx(output_path, rows)

    print(f"Question blocks: {len(question_blocks)}")
    print(f"Analysis blocks: {len(analysis_blocks)}")
    print(f"Matched rows: {len(rows) - 1}")
    print(f"Output: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
