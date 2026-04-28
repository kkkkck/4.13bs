#!/usr/bin/env python3
"""
Convert Anki .apkg packages into the Excel template accepted by this project.

This converter currently targets single-choice / multiple-choice Anki decks that
store encrypted content and can be rendered/decrypted in a local Chromium-based
browser (Edge/Chrome) through the bundled Node helper.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import subprocess
import tempfile
import zipfile
import zlib
from pathlib import Path
from typing import Any

import dataset_to_excel as excel_converter
import politics_syllabus


APKG_FILE_NAMES = ("collection.anki2", "collection.anki21", "collection.anki21b")
TOPIC_NAME_MAP = {
    "马原": "马克思主义基本原理概论",
    "毛中特": "毛泽东思想和中国特色社会主义",
    "史纲": "中国近现代史纲要",
    "思修": "思想道德修养与法律基础",
}

TEXT_REPLACEMENTS = (
    ("“03个代表”", "“三个代表”"),
    ("“05位01体”", "“五位一体”"),
    ("“04个全面”", "“四个全面”"),
    ("公徳", "公德"),
    ("尊法学法守法用法", "遵法学法守法用法"),
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert an Anki .apkg package to the project's Excel import format.")
    parser.add_argument("--input", required=True, help="Input .apkg path")
    parser.add_argument("--output", required=True, help="Output .xlsx path")
    parser.add_argument("--category-mode", choices=("top", "chapter"), default="top", help="Top category or generated chapter category output")
    parser.add_argument("--category-seed-output", help="Optional SQL path for generated chapter category seed data")
    parser.add_argument("--source", default="", help="Optional source label override")
    parser.add_argument("--source-type", default="模拟题", help="Source type for imported questions, default 模拟题")
    parser.add_argument("--difficulty", default="medium", help="Fallback difficulty for imported questions")
    parser.add_argument("--status", type=int, default=1, help="Fallback question status")
    parser.add_argument("--category-id", type=int, default=1, help="Fallback category when deck mapping fails")
    parser.add_argument("--node-binary", default="node", help="Node.js binary used by the decrypt helper")
    parser.add_argument("--browser-path", default="", help="Optional browser path override for the decrypt helper")
    parser.add_argument("--decrypt-timeout-ms", type=int, default=20000, help="Per-note decrypt timeout")
    return parser.parse_args()


def normalize_filename_source(path: Path) -> str:
    return path.stem


def int_to_chinese(value: int) -> str:
    numerals = "零一二三四五六七八九"
    if value < 10:
        return numerals[value]
    if value == 10:
        return "十"
    if value < 20:
        return "十" + numerals[value - 10]
    tens, ones = divmod(value, 10)
    return numerals[tens] + "十" + (numerals[ones] if ones else "")


def find_collection_db(extract_dir: Path) -> Path:
    for name in APKG_FILE_NAMES:
        candidate = extract_dir / name
        if candidate.exists():
            return candidate
    raise FileNotFoundError("No Anki collection database found in the .apkg package")


def load_decks(conn: sqlite3.Connection) -> dict[int, str]:
    raw = conn.execute("select decks from col").fetchone()[0]
    decks = json.loads(raw)
    return {int(deck_id): deck["name"] for deck_id, deck in decks.items()}


def load_model(conn: sqlite3.Connection) -> tuple[str, list[str]]:
    raw = conn.execute("select models from col").fetchone()[0]
    models = json.loads(raw)
    model = next(iter(models.values()))
    return model["css"], model["tmpls"][0]["afmt"], [field["name"] for field in model["flds"]]


def load_note_rows(conn: sqlite3.Connection) -> list[dict[str, Any]]:
    rows = conn.execute(
        """
        select notes.id, notes.flds, cards.did
        from notes
        join cards on cards.nid = notes.id
        group by notes.id
        order by cards.id
        """
    ).fetchall()
    return [
        {
            "note_id": int(note_id),
            "fields": str(flds).split(chr(31)),
            "deck_id": int(deck_id),
        }
        for note_id, flds, deck_id in rows
    ]


def extract_topic_and_chapter(deck_name: str) -> tuple[str, str]:
    segments = [segment.strip() for segment in deck_name.split("::") if segment.strip()]
    topic = ""
    chapter = ""

    for segment in segments:
        for key, mapped in TOPIC_NAME_MAP.items():
            if key in segment:
                topic = mapped
                break
        if topic:
            break

    for segment in segments:
        if re.match(r"^第\d+章", segment) or re.match(r"^第[0-9一二三四五六七八九十零]+章", segment):
            chapter = segment
            break
        if "绪论" in segment or "导论" in segment:
            chapter = segment
            break

    return topic, chapter


def normalize_apkg_chapter_name(name: str) -> str:
    normalized = name.strip()
    for old, new in TEXT_REPLACEMENTS:
        normalized = normalized.replace(old, new)

    if not normalized:
        return normalized
    if "绪论" in normalized or "导论" in normalized:
        return "绪论"

    match = re.match(r"^第0*([0-9]+)章(.+)$", normalized)
    if not match:
        return normalized

    chapter_number = int(match.group(1))
    title = match.group(2).strip()
    if chapter_number == 0:
        return "绪论" if "绪论" in title or not title else title
    return f"第{int_to_chinese(chapter_number)}章 {title}"


def resolve_canonical_chapter(root_id: int | None, chapter_name: str) -> dict[str, Any] | None:
    if root_id is None:
        return None
    canonical_name = politics_syllabus.resolve_canonical_chapter_name(root_id, chapter_name)
    canonical_id = politics_syllabus.resolve_canonical_category_id(root_id, chapter_name)
    if canonical_name is None or canonical_id is None:
        return None
    return {"id": canonical_id, "name": canonical_name}


def infer_modern_theory_chapter(question: str, analysis: str, chapter_name: str) -> dict[str, Any] | None:
    normalized_chapter = chapter_name.strip()
    if normalized_chapter not in {"第十章 “五位一体”总体布局", "第十一章 “四个全面”战略布局"}:
        return None

    text = f"{question}\n{analysis}"

    keyword_groups = (
        ("第16章 中国特色大国外交和推动构建人类命运共同体", ("人类命运共同体", "中国特色大国外交", "一带一路", "对外开放新格局中的外交", "全球治理", "和平发展道路")),
        ("第15章 坚持“一国两制”和推进祖国完全统一", ("一国两制", "港澳", "澳门", "香港", "台湾", "祖国完全统一", "和平统一")),
        ("第14章 建设巩固国防和强大人民军队", ("国防", "强军", "军队", "人民军队", "国防和军队现代化")),
        ("第17章 全面从严治党", ("全面从严治党", "党的自我革命", "党要管党", "从严治党", "党的建设", "反腐败", "巡视巡察")),
        ("第13章 维护和塑造国家安全", ("总体国家安全观", "国家安全", "平安中国", "政治安全", "人民安全", "国家利益至上")),
        ("第12章 建设社会主义生态文明", ("生态文明", "美丽中国", "环境保护", "污染防治", "人与自然和谐共生", "绿色发展", "碳达峰", "碳中和")),
        ("第11章 以保障和改善民生为重点加强社会建设", ("保障和改善民生", "民生", "社会保障", "就业", "六稳", "六保", "健康中国", "人民健康")),
        ("第10章 建设社会主义文化强国", ("文化强国", "文化自信", "社会主义核心价值观", "社会主义核心价值体系", "中华优秀传统文化", "文物", "博物馆", "意识形态", "文化事业", "文化产业")),
        ("第9章 全面依法治国", ("全面依法治国", "法治", "法治国家", "法治政府", "法治社会")),
        ("第8章 发展全过程人民民主", ("全过程人民民主", "人民当家作主", "政治发展道路", "协商民主", "民主政治", "统一战线")),
        ("第7章 社会主义现代化建设的教育、科技、人才战略", ("教育", "科技", "人才", "科教兴国", "创新驱动", "战略支撑")),
        ("第6章 推动高质量发展", ("高质量发展", "新发展理念", "现代化经济体系", "实体经济", "双循环", "乡村振兴", "三农", "农业农村", "供给侧", "经济发展", "发展理念", "区域协调发展")),
        ("第5章 全面深化改革开放", ("全面深化改革", "改革开放", "自贸港", "外商投资法", "改革", "制度型开放")),
        ("第4章 坚持以人民为中心", ("以人民为中心", "人民立场", "共同富裕")),
        ("第3章 坚持党的全面领导", ("坚持党的全面领导", "党的领导", "党中央集中统一领导", "党总揽全局", "党的领导制度")),
        ("第2章 以中国式现代化全面推进中华民族伟大复兴", ("中国式现代化", "中华民族伟大复兴", "中国梦", "强国建设", "民族复兴", "全面建成小康社会", "脱贫攻坚", "现代化强国")),
        ("第1章 新时代坚持和发展中国特色社会主义", ("新时代坚持和发展中国特色社会主义",)),
    )

    for canonical_name, keywords in keyword_groups:
        if any(keyword in text for keyword in keywords):
            canonical_id = politics_syllabus.resolve_canonical_category_id(2, canonical_name)
            if canonical_id is not None:
                return {"id": canonical_id, "name": canonical_name}

    fallback_name = "第6章 推动高质量发展" if normalized_chapter.startswith("第十章") else "第5章 全面深化改革开放"
    fallback_id = politics_syllabus.resolve_canonical_category_id(2, fallback_name)
    if fallback_id is None:
        return None
    return {"id": fallback_id, "name": fallback_name}


def infer_question_type(deck_name: str, answer: str) -> str:
    if "多选" in deck_name:
        return "多选"
    if "单选" in deck_name:
        return "单选"
    if len(answer.replace(",", "").strip()) > 1:
        return "多选"
    return "单选"


def clean_analysis(text: str) -> str:
    text = text.strip()
    if text.startswith("【简析】"):
        return text.removeprefix("【简析】").strip()
    if text.startswith("解析："):
        return text.removeprefix("解析：").strip()
    if text.startswith("解析:"):
        return text.removeprefix("解析:").strip()
    return text


def normalize_question_text(text: str) -> str:
    return re.sub(r"^\s*\d+[.、．]\s*", "", text.strip())


def extract_inline_options(text: str) -> tuple[str, list[str]]:
    normalized = text.replace("|", "\n")
    matches = list(re.finditer(r"([A-D])[.．](.+?)(?=(?:\n?[A-D][.．])|$)", normalized, flags=re.S))
    if len(matches) < 2:
        return text.strip(), []

    question = normalized[: matches[0].start()].strip()
    options = [match.group(2).strip().replace("\n", " ") for match in matches]
    return question, options


def build_option_text(options: list[str]) -> str:
    labels = ("A", "B", "C", "D")
    return "|".join(f"{label}.{value}" for label, value in zip(labels, options, strict=False))


def build_namespaced_category_catalog(records: list[dict[str, Any]], default_category_id: int, namespace: str) -> dict[int, list[dict[str, Any]]]:
    base_catalog = excel_converter.build_category_catalog(records, default_category_id)
    namespace_seed = zlib.crc32(namespace.encode("utf-8")) % 900 + 100
    namespaced_catalog: dict[int, list[dict[str, Any]]] = {}

    for root_id, chapters in base_catalog.items():
        namespaced_catalog[root_id] = []
        for index, chapter in enumerate(chapters, start=1):
            item = dict(chapter)
            item["id"] = root_id * 1_000_000 + namespace_seed * 1000 + index
            item["description"] = f"Imported from {namespace}"
            namespaced_catalog[root_id].append(item)

    return namespaced_catalog


def parse_decrypted_note(note: dict[str, Any], source: str, source_type: str, fallback_category_id: int) -> dict[str, Any]:
    if note.get("errorText"):
        raise RuntimeError(f"Decrypt failed for note {note.get('note_id')}: {note['errorText'][:200]}")

    question = str(note.get("question") or "").strip()
    options = [str(item).strip() for item in note.get("options") or [] if str(item).strip()]
    answer = str(note.get("answer") or "").strip().upper()
    analysis = clean_analysis(str(note.get("analysis") or "").strip())
    body_text = str(note.get("bodyText") or "").strip()

    if not options and body_text:
        question, options = extract_inline_options(question or body_text)

    question = normalize_question_text(question)

    if not question:
        raise RuntimeError(f"Missing question text for note {note.get('note_id')}")
    if len(options) < 2:
        raise RuntimeError(f"Missing options for note {note.get('note_id')}")
    if not answer:
        raise RuntimeError(f"Missing answer for note {note.get('note_id')}")

    topic, chapter = extract_topic_and_chapter(str(note.get("deck_name") or ""))
    normalized_chapter = normalize_apkg_chapter_name(chapter)
    root_id = excel_converter.EXACT_CATEGORY_MAP.get(topic)
    canonical = resolve_canonical_chapter(root_id, normalized_chapter)
    if canonical is None and root_id == 2:
        canonical = infer_modern_theory_chapter(question, analysis, normalized_chapter)
    record = {
        "title": question,
        "type": infer_question_type(str(note.get("deck_name") or ""), answer),
        "xuan_text": build_option_text(options[:4]),
        "right_text": answer,
        "jiexi": analysis,
        "source": source,
        "sourceType": source_type,
        "top_kaodian_text": topic,
        "p_kaodian_text": canonical["name"] if canonical else normalized_chapter,
        "categoryId": fallback_category_id if not topic else None,
        "__source_stem__": source,
    }
    if canonical:
        record["__resolved_category_id__"] = canonical["id"]
    if record["categoryId"] is None:
        record.pop("categoryId")
    return record


def decrypt_notes_with_browser(
    node_binary: str,
    helper_path: Path,
    css: str,
    template: str,
    field_names: list[str],
    note_rows: list[dict[str, Any]],
    browser_path: str,
    timeout_ms: int,
) -> list[dict[str, Any]]:
    helper_input = {
        "css": css,
        "template": template,
        "fieldNames": field_names,
        "timeoutMs": timeout_ms,
        "notes": [
            {
                "fields": row["fields"],
                "meta": {
                    "note_id": row["note_id"],
                    "deck_id": row["deck_id"],
                    "deck_name": row["deck_name"],
                },
            }
            for row in note_rows
        ],
    }

    with tempfile.NamedTemporaryFile("w", encoding="utf-8", suffix=".json", delete=False) as handle:
        json.dump(helper_input, handle, ensure_ascii=False)
        input_path = Path(handle.name)

    env = os.environ.copy()
    if browser_path:
        env["APKG_BROWSER"] = browser_path

    try:
        completed = subprocess.run(
            [node_binary, str(helper_path), str(input_path)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
            env=env,
        )
        if completed.returncode != 0:
            raise RuntimeError((completed.stderr or completed.stdout or "decrypt helper failed").strip())
        payload = json.loads(completed.stdout)
        return payload["notes"]
    finally:
        input_path.unlink(missing_ok=True)


def main() -> int:
    args = parse_args()
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    category_seed_output = Path(args.category_seed_output).resolve() if args.category_seed_output else None
    helper_path = Path(__file__).resolve().parent / "tools" / "decrypt_apkg_notes.js"

    if input_path.suffix.lower() != ".apkg":
        raise SystemExit("Input must be an .apkg file")
    if not input_path.exists():
        raise SystemExit(f"Input file not found: {input_path}")
    if not helper_path.exists():
        raise SystemExit(f"Decrypt helper not found: {helper_path}")

    source = args.source.strip() or normalize_filename_source(input_path)
    source_type = excel_converter.normalize_source_type(args.source_type, source, "")
    default_difficulty = excel_converter.normalize_difficulty(args.difficulty, 2)

    with tempfile.TemporaryDirectory() as temp_dir:
        extract_dir = Path(temp_dir)
        with zipfile.ZipFile(input_path) as archive:
            archive.extractall(extract_dir)

        db_path = find_collection_db(extract_dir)
        conn = sqlite3.connect(db_path)
        try:
            decks = load_decks(conn)
            css, template, field_names = load_model(conn)
            note_rows = load_note_rows(conn)
        finally:
            conn.close()

        for row in note_rows:
            row["deck_name"] = decks.get(row["deck_id"], "")

        decrypted_notes = decrypt_notes_with_browser(
            node_binary=args.node_binary,
            helper_path=helper_path,
            css=css,
            template=template,
            field_names=field_names,
            note_rows=note_rows,
            browser_path=args.browser_path.strip(),
            timeout_ms=args.decrypt_timeout_ms,
        )

    records = [
        parse_decrypted_note(
            note=note,
            source=source,
            source_type=source_type,
            fallback_category_id=args.category_id,
        )
        for note in decrypted_notes
    ]

    unmatched_records = [record for record in records if "__resolved_category_id__" not in record]

    category_catalog = (
        excel_converter.build_category_catalog(records, args.category_id)
        if (args.category_mode == "chapter" or category_seed_output)
        else {}
    )
    chapter_category_id_map = excel_converter.build_chapter_category_id_map(category_catalog) if category_catalog else None

    rows = [excel_converter.HEADERS]
    for record in records:
        row = excel_converter.convert_record(
            record=record,
            mapping={},
            default_category_id=args.category_id,
            default_source=source,
            default_source_type=source_type,
            default_difficulty=default_difficulty,
            default_status=args.status,
            category_mode=args.category_mode,
            chapter_category_id_map=chapter_category_id_map,
        )
        if row:
            rows.append(row)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    excel_converter.write_xlsx(output_path, rows)
    if category_seed_output:
        excel_converter.write_category_seed_sql(category_seed_output, category_catalog)

    print(f"Input: {input_path}")
    print(f"Decrypted rows: {len(records)}")
    print(f"Output: {output_path}")
    if category_seed_output:
        chapter_count = sum(len(chapters) for chapters in category_catalog.values())
        print(f"Category seed: {category_seed_output} ({chapter_count} chapters)")
    print("Template columns: " + ", ".join(excel_converter.HEADERS))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
