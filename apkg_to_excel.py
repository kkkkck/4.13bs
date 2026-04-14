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


APKG_FILE_NAMES = ("collection.anki2", "collection.anki21", "collection.anki21b")
TOPIC_NAME_MAP = {
    "马原": "马克思主义基本原理概论",
    "毛中特": "毛泽东思想和中国特色社会主义",
    "史纲": "中国近现代史纲要",
    "思修": "思想道德修养与法律基础",
}

CANONICAL_CHAPTER_MAP = {
    1: {
        "第一章 马克思主义是关于无产阶级和人类解放的科学": {"id": 1001, "name": "导论"},
        "第三章 实践与认识及其发展规律": {"id": 1004, "name": "第三章 认识论"},
        "第四章 人类社会及其发展规律": {"id": 1005, "name": "第四章 唯物史观"},
        "第五章 资本主义的本质及规律": {"id": 1006, "name": "第五章 资本主义的本质及规律"},
        "第六章 资本主义的发展及其趋势": {"id": 1007, "name": "第六章 资本主义的发展及趋势"},
        "第七章 社会主义的发展及其规律": {"id": 1008, "name": "第七章 社会主义社会的发展及其规律"},
        "第八章 共产主义崇高理想及其最终实现": {"id": 1009, "name": "第八章 共产主义崇高理想及其最终实现"},
    },
    2: {
        "第一章 毛泽东思想及其历史地位": {"id": 2003, "name": "第一章 毛泽东思想及其历史地位"},
        "第二章 新民主主义革命理论": {"id": 2005, "name": "第二章 新民主主义革命理论"},
        "第三章 社会主义改造理论": {"id": 2006, "name": "第三章 社会主义改造理论"},
        "第四章 社会主义建设道路初步探索的理论成果": {"id": 2007, "name": "第四章 社会主义建设道路初步探索的理论成果"},
        "第五章 邓小平理论": {"id": 2009, "name": "第五章 邓小平理论"},
        "第六章 “三个代表”重要思想": {"id": 2010, "name": "第六章 “三个代表”重要思想"},
        "第七章 科学发展观": {"id": 2013, "name": "第七章 科学发展观"},
        "第十三章 中国特色大国外交": {"id": 2020, "name": "第十六章 中国特色大国外交和推动构建人类命运共同体"},
    },
    3: {
        "第一章 反对外国侵略的斗争": {"id": 3001, "name": "第一章 反对外国侵略的斗争"},
        "第二章 对国家出路的早期探索": {"id": 3002, "name": "第二章 对国家出路的早期探索"},
        "第三章 辛亥革命与君主专制制度的终结": {"id": 3003, "name": "第三章 辛亥革命与君主专制制度的终结"},
        "第四章 开天辟地的大事变": {"id": 3004, "name": "第四章中国共产党成立和中国革命新局面"},
        "第五章 中国革命的新道路": {"id": 3005, "name": "第五章 中国革命的新道路"},
        "第六章 中华民族的抗日战争": {"id": 3006, "name": "第六章 中华民族的抗日战争"},
        "第七章 为新中国而奋斗": {"id": 3007, "name": "第七章 为新中国而奋斗"},
        "第十章 中国特色社会主义的开创与接续发展": {"id": 3009, "name": "第九章中国特色社会主义的开创与接续发展"},
    },
    4: {
        "第一章 人生的青春之问": {"id": 4001, "name": "第一章 人生的青春之问"},
        "第二章 坚定理想信念": {"id": 4002, "name": "第二章 坚定理想信念"},
        "第三章 弘扬中国精神": {"id": 4003, "name": "第三章 弘扬中国精神"},
        "第四章 践行社会主义核心价值观": {"id": 4004, "name": "第四章 践行社会主义核心价值观"},
        "第五章 明大德守公德严私德": {"id": 4005, "name": "第五章 明大德守公德严私德"},
        "第六章 遵法学法守法用法": {"id": 4006, "name": "第六章 遵法学法守法用法"},
    },
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
    return CANONICAL_CHAPTER_MAP.get(root_id, {}).get(chapter_name)


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
        build_namespaced_category_catalog(unmatched_records, args.category_id, source)
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
