#!/usr/bin/env python3
"""
Convert the TsekaLuk/Kaoyan-Politics-Papers markdown solutions into the
project's importable Excel format.

Notes:
- Objective questions (1-33) are sourced from local kyzz data for stable
  stems/options/chapter metadata, then enriched with markdown answers/analysis.
- Material questions (34-38) are parsed directly from the markdown files.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import sys
from pathlib import Path
from typing import Any

import dataset_to_excel as excel_converter
import politics_syllabus


ROOT_CATEGORY_NAMES = {
    1: "马克思主义基本原理概论",
    2: "毛泽东思想和中国特色社会主义理论体系概论",
    3: "中国近现代史纲要",
    4: "思想道德与法治",
    5: "形势与政策以及当代世界经济与政治",
}

QUESTION_ROOT_MAP = {
    **{number: 1 for number in range(1, 5)},
    **{number: 2 for number in range(5, 9)},
    **{number: 3 for number in range(9, 13)},
    **{number: 4 for number in range(13, 15)},
    **{number: 5 for number in range(15, 17)},
    **{number: 1 for number in range(17, 22)},
    **{number: 2 for number in range(22, 27)},
    **{number: 3 for number in range(27, 30)},
    **{number: 4 for number in range(30, 32)},
    **{number: 5 for number in range(32, 34)},
    34: 1,
    35: 2,
    36: 3,
    37: 4,
    38: 5,
}

QUESTION_START_RE = re.compile(r"^\s*(\d{1,2})[.．、]\s*(.*)$")
INLINE_ANALYSIS_ANSWER_RE = re.compile(r"^\s*\d{1,2}[.．、]\s*([A-D]{1,4})\s*【解析】\s*(.*)$", re.S)
MARKED_ANSWER_RE = re.compile(r"(?:【答案】|答案[:：])\s*([A-D]{1,4})\b", re.I)
SUBQUESTION_RE = re.compile(r"(?m)^[（(]\d+[)）]")
NOISE_PATTERNS = (
    re.compile(r"找研讯"),
    re.compile(r"聚创考研网"),
    re.compile(r"政治试题及解析\s*第\s*\d+\s*页"),
    re.compile(r"^\s*\d+\s*$"),
)
ANALYSIS_MARKERS = ("【解析】", "【参考答案】", "【答案要点】")
PRIMARY_SUBQUESTION_RE = re.compile(r"(?m)^[（(]1[)）]")

MATERIAL_CONTENT_FALLBACKS: dict[int, dict[int, str]] = {
    2022: {
        34: (
            "结合材料回答问题：\n"
            "材料内容：传统安全和非传统安全威胁相互交织，“黑天鹅”“灰犀牛”事件可能在政治、经济、自然等领域形成系统性风险。\n"
            "(1) 运用必然和偶然的辩证关系，说明为什么小概率事件并非零概率事件？\n"
            "(2) 面对复杂局面，如何运用好辩证思维？"
        ),
        35: (
            "结合材料回答问题：\n"
            "材料内容：党的百年奋斗是一部不断推进理论创新、进行理论创造的历史；中国特色社会主义进入新时代，习近平新时代中国特色社会主义思想实现了马克思主义中国化新的飞跃。\n"
            "(1) 为什么说党的百年奋斗是一部不断推动理论创新、进行理论创造的历史？\n"
            "(2) 如何从中国特色社会主义发展战略全局以及世界百年未有之大变局，阐述习近平新时代中国特色社会主义思想是马克思主义中国化新的飞跃？"
        ),
        36: (
            "结合材料回答问题：\n"
            "材料内容：围绕孙中山振兴中华的宏大理想难以在旧中国实现，以及中国共产党百年奋斗的历史意义展开。\n"
            "(1) 为什么在旧中国的政治经济社会条件下，孙中山振兴中华的宏大理想难以实现？\n"
            "(2) 中国共产党百年奋斗的历史意义是什么？"
        ),
        37: (
            "结合材料回答问题：\n"
            "材料内容：黄文秀先进事迹与新时代青年使命担当。\n"
            "(1) 黄文秀的先进事迹启示新时代青年应该有怎样的人生态度？\n"
            "(2) 新时代中国青年应当承担什么样的历史重任？"
        ),
        38: (
            "结合材料回答问题：\n"
            "材料内容：中华人民共和国恢复联合国合法席位 50 周年，中国坚持真正的多边主义、维护联合国权威和国际公平正义。\n"
            "(1) 为什么说中华人民共和国恢复联合国合法席位既是中国人民的胜利，也是世界各国人民的胜利？\n"
            "(2) “一个体系、一个秩序、一个规则”对于推动构建人类命运共同体有何重要意义？"
        ),
    }
}

MATERIAL_CHAPTER_OVERRIDES: dict[tuple[int, int], str] = {
    (2021, 35): "第2章 以中国式现代化全面推进中华民族伟大复兴",
    (2021, 36): "第八章 中华人民共和国的成立与中国社会主义建设道路的探索",
    (2021, 37): "第三章 继承优良传统 弘扬中国精神",
    (2022, 35): "习思：导论",
    (2022, 37): "第一章 领悟人生真谛 把握人生方向",
    (2023, 34): "专题三：辩证唯物主义认识论",
    (2023, 35): "第12章 建设社会主义生态文明",
    (2023, 36): "第四章 中国共产党成立和中国革命新局面",
    (2024, 34): "专题三：辩证唯物主义认识论",
    (2024, 35): "第12章 建设社会主义生态文明",
    (2024, 36): "第四章 中国共产党成立和中国革命新局面",
    (2024, 37): "第二章 追求远大理想 坚定崇高信念",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert Kaoyan-Politics-Papers markdown solutions to importable Excel.")
    parser.add_argument(
        "--solutions-dir",
        default="dataset/Kaoyan-Politics-Papers/solutions",
        help="Directory containing yearly markdown solutions",
    )
    parser.add_argument(
        "--kyzz-dir",
        default="dataset/kyzz/data",
        help="Directory containing local kyzz objective-question JSON files",
    )
    parser.add_argument("--output", required=True, help="Output .xlsx path")
    parser.add_argument(
        "--mode",
        choices=("all", "objective", "materials"),
        default="all",
        help="Export all parsed questions, only objective questions, or only material questions",
    )
    parser.add_argument(
        "--category-mode",
        choices=("top", "chapter"),
        default="chapter",
        help="Whether to export root categories or canonical chapter categories",
    )
    parser.add_argument(
        "--category-seed-output",
        help="Optional SQL output path for generated chapter categories",
    )
    return parser.parse_args()


def compact_text(value: str) -> str:
    lines: list[str] = []
    previous_blank = False
    for raw_line in value.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        line = re.sub(r"\s+", " ", raw_line.strip())
        if not line:
            if lines and not previous_blank:
                lines.append("")
            previous_blank = True
            continue
        previous_blank = False
        lines.append(line)
    while lines and not lines[-1]:
        lines.pop()
    return "\n".join(lines).strip()


def strip_html_markup(value: str) -> str:
    if not value:
        return ""
    clean = html.unescape(value)
    clean = re.sub(r"<br\s*/?>", "\n", clean, flags=re.I)
    clean = re.sub(r"</p\s*>", "\n", clean, flags=re.I)
    clean = re.sub(r"<[^>]+>", "", clean)
    return compact_text(clean)


def normalize_answer(value: str) -> str:
    return "".join(ch for ch in value.upper() if "A" <= ch <= "D")


def cleaned_solution_lines(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    text = text.replace("\ufeff", "").replace("\u3000", " ").replace("\x0c", "\n")
    lines: list[str] = []
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if any(pattern.search(line) for pattern in NOISE_PATTERNS):
            continue
        lines.append(line)
    return lines


def split_question_blocks(lines: list[str]) -> dict[int, str]:
    blocks: dict[int, list[str]] = {}
    current_number: int | None = None

    for line in lines:
        match = QUESTION_START_RE.match(line)
        if match:
            number = int(match.group(1))
            if 1 <= number <= 38:
                current_number = number
                blocks[current_number] = [f"{number}.{match.group(2).strip()}"]
                continue
        if current_number is not None:
            blocks[current_number].append(line)

    return {number: compact_text("\n".join(parts)) for number, parts in blocks.items()}


def extract_analysis_text(block: str) -> str:
    for marker in ANALYSIS_MARKERS:
        position = block.find(marker)
        if position != -1:
            return compact_text(block[position:])
    return ""


def extract_objective_answer(block: str) -> str:
    inline_match = INLINE_ANALYSIS_ANSWER_RE.match(block)
    if inline_match:
        return normalize_answer(inline_match.group(1))

    marked_match = MARKED_ANSWER_RE.search(block)
    if marked_match:
        return normalize_answer(marked_match.group(1))
    return ""


def parse_objective_supplements(blocks: dict[int, str]) -> dict[int, dict[str, str]]:
    supplements: dict[int, dict[str, str]] = {}
    for number in range(1, 34):
        block = blocks.get(number)
        if not block:
            continue
        answer = extract_objective_answer(block)
        analysis = extract_analysis_text(block)
        if answer or analysis:
            supplements[number] = {
                "correctAnswer": answer,
                "analysis": analysis,
            }
    return supplements


def split_material_content_and_analysis(block: str) -> tuple[str, str]:
    body = re.sub(r"^\s*\d{1,2}[.．、]\s*", "", block, count=1)

    marker_positions = [body.find(marker) for marker in ANALYSIS_MARKERS if body.find(marker) != -1]
    if marker_positions:
        split_index = min(marker_positions)
        return compact_text(body[:split_index]), compact_text(body[split_index:])

    subquestion_positions = [match.start() for match in PRIMARY_SUBQUESTION_RE.finditer(body)]
    if len(subquestion_positions) >= 2:
        split_index = subquestion_positions[1]
        return compact_text(body[:split_index]), compact_text(body[split_index:])

    return compact_text(body), ""


def infer_material_chapter_name(year: int, number: int, root_id: int, content: str, analysis: str) -> str:
    override = MATERIAL_CHAPTER_OVERRIDES.get((year, number))
    if override:
        return override

    if root_id == 5:
        return ""

    record = {
        "title": content,
        "analysis": analysis,
    }
    chapter_name = excel_converter.infer_kyzz_chapter_name(record, root_id)
    if chapter_name:
        return chapter_name

    if root_id == 2:
        merged = f"{content}\n{analysis}"
        if "新时代中国特色社会主义" in merged or "中国式现代化" in merged or "习近平生态文明思想" in merged:
            return "第1章 新时代坚持和发展中国特色社会主义"
        return "第五章 中国特色社会主义理论体系的形成发展"
    return ""


def build_theory_root_name(chapter_name: str, content: str, analysis: str) -> str:
    if chapter_name:
        chapter_id = politics_syllabus.resolve_canonical_category_id(2, chapter_name)
        if chapter_id is not None and chapter_id >= 2101:
            return "新时代中国特色社会主义思想概论"
        if chapter_id is not None:
            return "毛泽东思想和中国特色社会主义理论体系概论"

    merged = f"{content}\n{analysis}"
    if "新时代中国特色社会主义" in merged or "中国式现代化" in merged or "全面从严治党" in merged:
        return "新时代中国特色社会主义思想概论"
    return "毛泽东思想和中国特色社会主义理论体系概论"


def build_material_record(year: int, number: int, block: str) -> dict[str, Any]:
    root_id = QUESTION_ROOT_MAP[number]
    content, analysis = split_material_content_and_analysis(block)
    if not content:
        content = MATERIAL_CONTENT_FALLBACKS.get(year, {}).get(number, "")
    chapter_name = infer_material_chapter_name(year, number, root_id, content, analysis)

    top_name = ROOT_CATEGORY_NAMES[root_id]
    if root_id == 2:
        top_name = build_theory_root_name(chapter_name, content, analysis)

    return {
        "content": content,
        "num": number,
        "type": "short-answer",
        "difficulty": "medium",
        "source": f"{year}考研真题",
        "sourceType": "真题",
        "analysis": analysis,
        "categoryId": root_id,
        "top_kaodian_text": top_name,
        "p_kaodian_text": chapter_name,
        "__source_stem__": str(year),
    }


def parse_material_records(year: int, blocks: dict[int, str]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for number in range(34, 39):
        block = blocks.get(number)
        if not block:
            continue
        records.append(build_material_record(year, number, block))
    return records


def iter_solution_files(solutions_dir: Path) -> list[tuple[int, Path]]:
    result: list[tuple[int, Path]] = []
    for path in sorted(solutions_dir.glob("*/politics_*.md")):
        match = re.search(r"(\d{4})", path.as_posix())
        if not match:
            continue
        result.append((int(match.group(1)), path))
    return result


def load_year_blocks(solutions_dir: Path) -> dict[int, dict[int, str]]:
    year_blocks: dict[int, dict[int, str]] = {}
    for year, path in iter_solution_files(solutions_dir):
        year_blocks[year] = split_question_blocks(cleaned_solution_lines(path))
    return year_blocks


def load_kyzz_lookup(kyzz_dir: Path) -> dict[tuple[int, int], dict[str, Any]]:
    lookup: dict[tuple[int, int], dict[str, Any]] = {}
    for year in range(2021, 2025):
        short_year = str(year)[-2:]
        path = kyzz_dir / f"{short_year}.json"
        if not path.exists():
            continue
        records = excel_converter.load_records(path)
        for record in excel_converter.annotate_records(records, path):
            number = excel_converter.parse_int(record.get("num"))
            if number is None or not 1 <= number <= 33:
                continue
            lookup[(year, number)] = dict(record)
    return lookup


def merge_analysis(repo_analysis: str, kyzz_analysis: str) -> str:
    clean_repo = strip_html_markup(repo_analysis)
    if clean_repo:
        return clean_repo
    return strip_html_markup(kyzz_analysis)


def build_objective_records(
    years: list[int],
    kyzz_lookup: dict[tuple[int, int], dict[str, Any]],
    objective_supplements: dict[int, dict[int, dict[str, str]]],
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    missing: list[str] = []

    for year in years:
        for number in range(1, 34):
            base = kyzz_lookup.get((year, number))
            if base is None:
                missing.append(f"{year}-{number}")
                continue

            supplement = objective_supplements.get(year, {}).get(number, {})
            record = dict(base)
            if supplement.get("correctAnswer"):
                record["right_text"] = supplement["correctAnswer"]
            record["jiexi"] = merge_analysis(supplement.get("analysis", ""), excel_converter.stringify(base.get("jiexi")))
            record["chuchu"] = f"{year}考研真题"
            record["sourceType"] = "真题"
            record["__source_stem__"] = str(year)
            records.append(record)

    if missing:
        print(f"Warning: missing kyzz objective records for {', '.join(missing[:8])}", file=sys.stderr)
    return records


def build_records(solutions_dir: Path, kyzz_dir: Path, mode: str) -> list[dict[str, Any]]:
    year_blocks = load_year_blocks(solutions_dir)
    years = sorted(year_blocks)

    records: list[dict[str, Any]] = []
    if mode in {"all", "objective"}:
        objective_supplements = {
            year: parse_objective_supplements(blocks)
            for year, blocks in year_blocks.items()
        }
        records.extend(build_objective_records(years, load_kyzz_lookup(kyzz_dir), objective_supplements))

    if mode in {"all", "materials"}:
        for year in years:
            records.extend(parse_material_records(year, year_blocks[year]))

    return sorted(
        records,
        key=lambda item: (
            int(excel_converter.stringify(item.get("__source_stem__")) or 0),
            excel_converter.parse_int(item.get("num")) or 0,
            excel_converter.stringify(item.get("content")),
        ),
    )


def build_rows(
    records: list[dict[str, Any]],
    category_mode: str,
    category_seed_output: Path | None,
) -> list[list[str]]:
    catalog = excel_converter.build_category_catalog(records, default_category_id=1)
    if category_seed_output:
        excel_converter.write_category_seed_sql(category_seed_output, catalog)

    chapter_category_id_map = None
    if category_mode == "chapter":
        chapter_category_id_map = excel_converter.build_chapter_category_id_map(catalog)

    rows = [excel_converter.HEADERS]
    for record in records:
        row = excel_converter.convert_record(
            record=record,
            mapping={},
            default_category_id=1,
            default_source="考研政治真题",
            default_source_type="真题",
            default_difficulty=2,
            default_status=1,
            category_mode=category_mode,
            chapter_category_id_map=chapter_category_id_map,
        )
        if row is not None:
            rows.append(row)
    return rows


def main() -> None:
    args = parse_args()
    solutions_dir = Path(args.solutions_dir).resolve()
    kyzz_dir = Path(args.kyzz_dir).resolve()
    output_path = Path(args.output).resolve()
    category_seed_output = Path(args.category_seed_output).resolve() if args.category_seed_output else None

    if not solutions_dir.is_dir():
        raise ValueError(f"Solutions directory not found: {solutions_dir}")
    if args.mode in {"all", "objective"} and not kyzz_dir.is_dir():
        raise ValueError(f"kyzz directory not found: {kyzz_dir}")

    records = build_records(solutions_dir, kyzz_dir, mode=args.mode)
    rows = build_rows(records, category_mode=args.category_mode, category_seed_output=category_seed_output)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    excel_converter.write_xlsx(output_path, rows)

    root_fallback = sum(1 for row in rows[1:] if row[13] in {"1", "2", "3", "4"})
    print(
        json.dumps(
            {
                "records": len(rows) - 1,
                "mode": args.mode,
                "output": str(output_path),
                "rootFallbackCount": root_fallback,
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
