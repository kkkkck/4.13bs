#!/usr/bin/env python3
"""
Convert common question datasets into the Excel template accepted by this project.

Supported input formats:
- .json      JSON array, or nested object containing a list of records
- .jsonl     One JSON object per line
- .csv/.tsv  Header-based tabular files
- directory  Recursively aggregates .json/.jsonl files

Output:
- .xlsx file with columns:
  content, type, difficulty, tags, source, sourceType, optionA, optionB, optionC, optionD,
  correctAnswer, analysis, solutionStrategy, categoryId, status

Examples:
  python dataset_to_excel.py --input data.json --output questions.xlsx --category-id 1
  python dataset_to_excel.py --input data.csv --output questions.xlsx --category-id 3 --source "External dataset"
  python dataset_to_excel.py --input dataset/kyzz/data --output dataset/exports/kyzz.xlsx

Optional mapping file format:
{
  "content": ["question", "stem"],
  "options": ["choices"],
  "correctAnswer": ["answer"],
  "analysis": ["explanation"]
}
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable
from xml.sax.saxutils import escape

import politics_syllabus


HEADERS = [
    "content",
    "type",
    "difficulty",
    "tags",
    "source",
    "sourceType",
    "optionA",
    "optionB",
    "optionC",
    "optionD",
    "correctAnswer",
    "analysis",
    "solutionStrategy",
    "categoryId",
    "status",
]

TYPE_OUTPUT = {
    1: "single-choice",
    2: "blank",
    4: "short-answer",
    5: "multiple-choice",
}

DIFFICULTY_OUTPUT = {
    1: "basic",
    2: "medium",
    3: "hard",
}

FIELD_ALIASES = {
    "content": ["content", "question", "stem", "title", "prompt", "body", "text"],
    "type": ["type", "question_type", "questionType", "kind", "format"],
    "difficulty": ["difficulty", "level", "diff", "hardness"],
    "tags": [
        "tags",
        "tag",
        "labels",
        "label",
        "topics",
        "topic",
        "knowledge",
        "knowledge_points",
        "kaodian",
        "p_kaodian_text",
        "top_kaodian_text",
        "kaodian_level_text",
    ],
    "source": ["source", "origin", "paper", "exam", "dataset", "book", "chuchu"],
    "sourceType": ["sourceType", "source_type", "originType", "origin_type", "question_origin"],
    "optionA": ["optionA", "option_a", "a", "choiceA", "choice_a", "answer_a"],
    "optionB": ["optionB", "option_b", "b", "choiceB", "choice_b", "answer_b"],
    "optionC": ["optionC", "option_c", "c", "choiceC", "choice_c", "answer_c"],
    "optionD": ["optionD", "option_d", "d", "choiceD", "choice_d", "answer_d"],
    "options": ["options", "choices", "items", "selections", "xuanxiang", "xuan_text"],
    "correctAnswer": [
        "correctAnswer",
        "correct_answer",
        "answer",
        "answers",
        "correct",
        "solution",
        "key",
        "right_answer",
        "right_text",
    ],
    "analysis": ["analysis", "explanation", "reason", "rationale", "commentary", "jiexi"],
    "solutionStrategy": ["solutionStrategy", "solution_strategy", "strategy", "approach", "method", "tips"],
    "categoryId": ["categoryId", "category_id", "topicId", "topic_id"],
    "status": ["status", "enabled", "active"],
}

LIST_CONTAINER_KEYS = ("data", "items", "records", "questions", "rows", "result")
KNOWN_RECORD_PATHS = (("detail", "timu"),)
OPTION_DISPLAY_KEYS = ("A", "B", "C", "D")
SUPPORTED_FILE_SUFFIXES = {".json", ".jsonl", ".csv", ".tsv", ".txt"}
DIRECTORY_INPUT_SUFFIXES = {".json", ".jsonl"}

EXACT_CATEGORY_MAP = {
    "马克思主义基本原理": 1,
    "马克思主义基本原理概论": 1,
    "毛泽东思想和中国特色社会主义理论体系概论": 2,
    "新时代中国特色社会主义思想概论": 2,
    "毛泽东思想和中国特色社会主义": 2,
    "中国近现代史纲要": 3,
    "思想道德与法治": 4,
    "思想道德修养与法律基础": 4,
    "形势与政策以及当代世界经济与政治": 5,
}

CATEGORY_KEYWORD_MAP = {
    1: ("马克思主义", "辩证唯物", "唯物史观", "认识论", "资本主义"),
    2: ("新时代中国特色社会主义", "毛泽东思想", "中国特色社会主义", "邓小平", "三个代表", "科学发展观"),
    3: ("中国近现代史", "中国革命", "抗日战争", "新中国", "外国侵略"),
    4: ("思想道德", "法律", "法治", "守法", "公民"),
    5: ("时政", "形势与政策", "当代世界", "国际形势"),
}

CHINESE_NUMERAL_MAP = {
    "零": 0,
    "一": 1,
    "二": 2,
    "三": 3,
    "四": 4,
    "五": 5,
    "六": 6,
    "七": 7,
    "八": 8,
    "九": 9,
}


def normalize_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.strip().lower())


def load_mapping(path: Path | None) -> dict[str, list[str]]:
    if not path:
        return {}

    raw = json.loads(path.read_text(encoding="utf-8-sig"))
    mapping: dict[str, list[str]] = {}
    for key, value in raw.items():
        if isinstance(value, list):
            mapping[key] = [str(item) for item in value]
        else:
            mapping[key] = [str(value)]
    return mapping


def build_record_index(record: dict[str, Any]) -> dict[str, tuple[str, Any]]:
    index: dict[str, tuple[str, Any]] = {}
    for original_key, value in record.items():
        index[normalize_key(str(original_key))] = (str(original_key), value)
    return index


def pick_value(record: dict[str, Any], mapping: dict[str, list[str]], field: str) -> Any:
    index = build_record_index(record)
    candidates = mapping.get(field) or FIELD_ALIASES.get(field, [])
    for candidate in candidates:
        hit = index.get(normalize_key(candidate))
        if hit is not None:
            return hit[1]
    return None


def stringify(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        if isinstance(value, float) and value.is_integer():
            return str(int(value))
        return str(value)
    if isinstance(value, list):
        return ",".join(stringify(item) for item in value if stringify(item))
    return str(value).strip()


def split_tag_fragments(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, list):
        fragments: list[str] = []
        for item in value:
            fragments.extend(split_tag_fragments(item))
        return fragments

    text = stringify(value)
    if not text:
        return []
    return [part.strip() for part in re.split(r"[,;\n\r\u3001]+", text) if part.strip()]


def parse_int(value: Any) -> int | None:
    text = stringify(value)
    if not text:
        return None
    try:
        return int(float(text))
    except ValueError:
        return None


def chinese_numeral_to_int(value: str) -> int | None:
    value = value.strip()
    if not value:
        return None
    if value.isdigit():
        return int(value)
    if value == "十":
        return 10
    if "十" in value:
        left, right = value.split("十", 1)
        tens = 1 if not left else CHINESE_NUMERAL_MAP.get(left)
        ones = 0 if not right else CHINESE_NUMERAL_MAP.get(right)
        if tens is None or ones is None:
            return None
        return tens * 10 + ones
    return CHINESE_NUMERAL_MAP.get(value)


def normalize_type(value: Any, option_count: int, correct_answer: str, content: str) -> int:
    numeric = parse_int(value)
    if numeric in TYPE_OUTPUT:
        return numeric

    normalized = stringify(value).lower()
    if normalized in {"single", "singlechoice", "single-choice", "单选", "单选题"}:
        return 1
    if normalized in {"blank", "fillblank", "fill-in-blank", "填空", "填空题"}:
        return 2
    if normalized in {"shortanswer", "short-answer", "essay", "简答", "简答题"}:
        return 4
    if normalized in {"multiple", "multiplechoice", "multiple-choice", "多选", "多选题"}:
        return 5

    if option_count >= 2:
        return 5 if "," in correct_answer else 1
    if re.search(r"_{3,}|\(\s*\)|\uff08\s*\uff09", content):
        return 2
    return 4


def normalize_difficulty(value: Any, default_value: int) -> int:
    numeric = parse_int(value)
    if numeric in DIFFICULTY_OUTPUT:
        return numeric

    normalized = stringify(value).lower()
    if normalized in {"basic", "easy", "基础", "简单"}:
        return 1
    if normalized in {"medium", "normal", "提高", "中等"}:
        return 2
    if normalized in {"hard", "advanced", "冲刺", "困难"}:
        return 3
    return default_value


def normalize_status(value: Any, default_value: int) -> int:
    numeric = parse_int(value)
    if numeric in {0, 1}:
        return numeric
    normalized = stringify(value).lower()
    if normalized in {"true", "enabled", "active", "yes"}:
        return 1
    if normalized in {"false", "disabled", "inactive", "no"}:
        return 0
    return default_value


def normalize_source(value: Any, default_value: str) -> str:
    text = stringify(value)
    return text or default_value


def infer_source_type_from_source(source: str) -> str:
    normalized = source.strip().lower()
    if (
        "模拟" in normalized
        or "mock" in normalized
        or "1000题" in normalized
        or "肖秀荣" in normalized
    ):
        return "模拟题"
    return "真题"


def normalize_source_type(value: Any, source_value: str, default_value: str) -> str:
    text = stringify(value).lower()
    if text in {"1", "真题", "真题卷", "real", "real-exam", "past-paper", "exam"}:
        return "真题"
    if text in {"2", "模拟题", "模拟卷", "mock", "mock-exam"}:
        return "模拟题"

    if source_value:
        return infer_source_type_from_source(source_value)
    if default_value:
        return infer_source_type_from_source(default_value)
    return "真题"


def normalize_category_override(record: dict[str, Any]) -> int | None:
    return parse_int(record.get("__resolved_category_id__"))


def collect_input_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        if input_path.suffix.lower() not in SUPPORTED_FILE_SUFFIXES:
            raise ValueError(f"Unsupported input format: {input_path.suffix}")
        return [input_path]

    if not input_path.is_dir():
        raise ValueError(f"Input path is neither a file nor a directory: {input_path}")

    paths = sorted(
        path
        for path in input_path.rglob("*")
        if path.is_file() and path.suffix.lower() in DIRECTORY_INPUT_SUFFIXES
    )
    if not paths:
        raise ValueError(f"No supported dataset files found in directory: {input_path}")
    return paths


def annotate_records(records: list[dict[str, Any]], source_path: Path) -> list[dict[str, Any]]:
    annotated: list[dict[str, Any]] = []
    for index, record in enumerate(records, start=1):
        item = dict(record)
        item.setdefault("__source_file__", source_path.name)
        item.setdefault("__source_stem__", source_path.stem)
        item.setdefault("__source_path__", source_path.as_posix())
        item.setdefault("__record_index__", index)
        annotated.append(item)
    return annotated


def extract_json_records(raw: Any) -> list[dict[str, Any]]:
    if isinstance(raw, list):
        records = [item for item in raw if isinstance(item, dict)]
        if records:
            return records
        raise ValueError("JSON array does not contain object records")

    if not isinstance(raw, dict):
        raise ValueError("JSON input must be an array or object")

    for path in KNOWN_RECORD_PATHS:
        value: Any = raw
        for key in path:
            if not isinstance(value, dict):
                value = None
                break
            value = value.get(key)
        if isinstance(value, list):
            records = [item for item in value if isinstance(item, dict)]
            if records:
                return records

    for key in LIST_CONTAINER_KEYS:
        value = raw.get(key)
        if isinstance(value, list):
            records = [item for item in value if isinstance(item, dict)]
            if records:
                return records

    record_lists: list[list[dict[str, Any]]] = []

    def walk(value: Any) -> None:
        if isinstance(value, list):
            records = [item for item in value if isinstance(item, dict)]
            if records and len(records) == len(value):
                record_lists.append(records)
            for item in value:
                walk(item)
            return
        if isinstance(value, dict):
            for nested in value.values():
                walk(nested)

    walk(raw)
    if record_lists:
        return max(record_lists, key=len)

    raise ValueError("JSON input must contain a list of object records")


def load_records(path: Path) -> list[dict[str, Any]]:
    suffix = path.suffix.lower()
    if suffix == ".json":
        raw = json.loads(path.read_text(encoding="utf-8-sig"))
        return annotate_records(extract_json_records(raw), path)

    if suffix == ".jsonl":
        records: list[dict[str, Any]] = []
        for line in path.read_text(encoding="utf-8-sig").splitlines():
            line = line.strip()
            if not line:
                continue
            item = json.loads(line)
            if isinstance(item, dict):
                records.append(item)
        return annotate_records(records, path)

    if suffix in {".csv", ".tsv", ".txt"}:
        sample = path.read_text(encoding="utf-8-sig", errors="ignore")
        delimiter = "\t" if suffix == ".tsv" else None
        if delimiter is None:
            try:
                delimiter = csv.Sniffer().sniff(sample[:4096], delimiters=",\t;|").delimiter
            except csv.Error:
                delimiter = ","
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle, delimiter=delimiter)
            return annotate_records([dict(row) for row in reader], path)

    raise ValueError(f"Unsupported input format: {path.suffix}")


def normalize_option_label(value: Any) -> str | None:
    text = stringify(value).strip().upper()
    if not text:
        return None
    if text in OPTION_DISPLAY_KEYS:
        return text

    numeric = parse_int(text)
    if numeric is not None:
        if 0 <= numeric < len(OPTION_DISPLAY_KEYS):
            return OPTION_DISPLAY_KEYS[numeric]
        if 1 <= numeric <= len(OPTION_DISPLAY_KEYS):
            return OPTION_DISPLAY_KEYS[numeric - 1]
    return None


def clean_option_text(label: str, value: Any) -> str:
    text = stringify(value).strip()
    if not text:
        return ""
    prefix = re.compile(rf"^\s*{re.escape(label)}[.\u3001:：)\]]\s*", flags=re.IGNORECASE)
    return prefix.sub("", text, count=1).strip()


def parse_option_text_entries(raw_text: str) -> list[tuple[str, str]]:
    parts = [part.strip() for part in re.split(r"[\n|]+", raw_text) if part.strip()]
    entries: list[tuple[str, str]] = []
    for index, part in enumerate(parts[:4]):
        match = re.match(r"^\s*([A-D])[.\u3001:：)\]]\s*(.+)$", part, flags=re.IGNORECASE)
        label = OPTION_DISPLAY_KEYS[index]
        text = part
        if match:
            label = match.group(1).upper()
            text = match.group(2)
        cleaned = clean_option_text(label, text)
        if cleaned:
            entries.append((label, cleaned))

    entries.sort(key=lambda pair: OPTION_DISPLAY_KEYS.index(pair[0]) if pair[0] in OPTION_DISPLAY_KEYS else 99)
    return entries


def extract_option_entries(record: dict[str, Any], mapping: dict[str, list[str]]) -> tuple[list[tuple[str, str]], str]:
    explicit = []
    for label, field in zip(OPTION_DISPLAY_KEYS, ("optionA", "optionB", "optionC", "optionD")):
        value = clean_option_text(label, pick_value(record, mapping, field))
        if value:
            explicit.append((label, value))
    if explicit:
        return explicit, ""

    raw_options = pick_value(record, mapping, "options")
    if raw_options is None:
        return [], ""

    if isinstance(raw_options, str):
        text = raw_options.strip()
        if text.startswith("{") or text.startswith("["):
            try:
                raw_options = json.loads(text)
            except json.JSONDecodeError:
                return parse_option_text_entries(text), ""
        else:
            return parse_option_text_entries(text), ""

    if isinstance(raw_options, dict):
        entries: list[tuple[str, str]] = []
        for key, value in raw_options.items():
            label = normalize_option_label(key)
            if not label:
                continue
            cleaned = clean_option_text(label, value)
            if cleaned:
                entries.append((label, cleaned))
        entries.sort(key=lambda pair: OPTION_DISPLAY_KEYS.index(pair[0]) if pair[0] in OPTION_DISPLAY_KEYS else 99)
        return entries, ""

    if not isinstance(raw_options, list):
        return [], ""

    entries: list[tuple[str, str]] = []
    inferred_answers: list[str] = []
    for index, item in enumerate(raw_options[:4]):
        label = OPTION_DISPLAY_KEYS[index]
        if isinstance(item, dict):
            text = stringify(
                item.get("text")
                or item.get("content")
                or item.get("value")
                or item.get("option")
                or item.get("label")
            )
            entry_label = stringify(item.get("key") or item.get("id") or label).upper()
            if len(entry_label) == 1 and entry_label in OPTION_DISPLAY_KEYS:
                label = entry_label
            cleaned = clean_option_text(label, text)
            if cleaned:
                entries.append((label, cleaned))
            if item.get("is_correct") or item.get("correct") or item.get("answer"):
                inferred_answers.append(label)
        else:
            cleaned = clean_option_text(label, item)
            if cleaned:
                entries.append((label, cleaned))

    inferred = ",".join(sorted(set(inferred_answers)))
    entries.sort(key=lambda pair: OPTION_DISPLAY_KEYS.index(pair[0]) if pair[0] in OPTION_DISPLAY_KEYS else 99)
    return entries, inferred


def normalize_answer_token(token: str, options: list[tuple[str, str]]) -> str | None:
    token = token.strip()
    if not token:
        return None

    upper = token.upper()
    if upper in OPTION_DISPLAY_KEYS:
        return upper

    numeric = parse_int(token)
    if numeric is not None:
        if 0 <= numeric < len(options):
            return options[numeric][0]
        if 1 <= numeric <= len(options):
            return options[numeric - 1][0]

    letter_match = re.match(r"^([A-D])[).:\s-]", upper)
    if letter_match:
        return letter_match.group(1)

    for label, text in options:
        if token.strip().lower() == text.strip().lower():
            return label
    return None


def normalize_correct_answer(raw: Any, options: list[tuple[str, str]], inferred: str) -> str:
    if inferred:
        return inferred

    if raw is None:
        return ""

    values: Iterable[Any]
    if isinstance(raw, list):
        values = raw
    else:
        text = stringify(raw)
        if not text:
            return ""
        upper_text = text.upper().replace(" ", "")
        if re.fullmatch(r"[A-D]+", upper_text):
            values = list(upper_text)
        else:
            values = re.split(r"[,/\u3001;\s]+", text)

    resolved: list[str] = []
    for item in values:
        token = normalize_answer_token(stringify(item), options)
        if token:
            resolved.append(token)

    unique = sorted(set(resolved))
    return ",".join(unique)


def build_tags(record: dict[str, Any], mapping: dict[str, list[str]]) -> str:
    fragments: list[str] = []
    seen: set[str] = set()
    for value in (
        pick_value(record, mapping, "tags"),
        record.get("top_kaodian_text"),
        record.get("p_kaodian_text"),
        record.get("kaodian_level_text"),
        record.get("kaodian"),
    ):
        for fragment in split_tag_fragments(value):
            if fragment not in seen:
                fragments.append(fragment)
                seen.add(fragment)
    return ",".join(fragments)


def is_kyzz_record(record: dict[str, Any]) -> bool:
    source_path = stringify(record.get("__source_path__")).replace("\\", "/").lower()
    return "dataset/kyzz/data/" in source_path


def infer_kyzz_question_number(record: dict[str, Any]) -> int | None:
    return parse_int(record.get("num") or record.get("__record_index__"))


def infer_kyzz_root_category_id(record: dict[str, Any]) -> int | None:
    question_number = infer_kyzz_question_number(record)
    if question_number is None:
        return None

    if 1 <= question_number <= 4:
        return 1
    if 5 <= question_number <= 8:
        return 2
    if 9 <= question_number <= 12:
        return 3
    if 13 <= question_number <= 14:
        return 4
    if 15 <= question_number <= 16:
        return 5
    if 17 <= question_number <= 21:
        return 1
    if 22 <= question_number <= 26:
        return 2
    if 27 <= question_number <= 29:
        return 3
    if 30 <= question_number <= 31:
        return 4
    if 32 <= question_number <= 33:
        return 5
    return None


def infer_kyzz_chapter_name(record: dict[str, Any], root_id: int) -> str:
    text = "\n".join(
        value
        for value in (
            stringify(record.get("title")),
            stringify(record.get("jiexi")),
            stringify(record.get("analysis")),
        )
        if value
    )

    keyword_groups: dict[int, list[tuple[str, tuple[str, ...]]]] = {
        1: [
            ("专题一：马克思主义观", ("马克思主义", "恩格斯", "马克思", "共产主义社会的基本特征", "人的自由而全面的发展", "马克思主义中国化")),
            ("专题二：辩证唯物主义世界观", ("物质", "意识", "运动", "静止", "时空", "唯物辩证法", "量变", "质变", "对立统一", "否定之否定", "规律", "必然", "偶然")),
            ("专题三：辩证唯物主义认识论", ("实践", "认识", "真理", "价值", "感性认识", "理性认识", "认识过程", "主观能动性")),
            ("专题四：唯物史观", ("社会存在", "社会意识", "生产力", "生产关系", "经济基础", "上层建筑", "人民群众", "历史人物", "社会形态", "科学技术", "历史经验", "唯物史观", "历史创造者")),
            ("专题五：资本主义论（上）", ("剩余价值", "劳动力", "货币转化为资本", "资本主义的本质", "商品", "价值规律", "资本积累", "雇佣劳动")),
            ("专题六：资本主义论（下）", ("垄断", "帝国主义", "经济全球化", "国家垄断资本主义", "金融资本", "国际垄断同盟", "资本主义的发展及其趋势")),
            ("专题七：社会主义论", ("社会主义", "苏联", "社会主义制度", "社会主义改革", "科学社会主义", "社会主义建设")),
            ("专题八：共产主义论", ("共产主义", "自由王国", "人类解放")),
        ],
        2: [
            ("第一章 毛泽东思想及其历史地位", ("毛泽东思想", "实事求是", "群众路线", "独立自主")),
            ("第二章 新民主主义革命理论", ("新民主主义", "农村包围城市", "统一战线", "武装斗争", "党的建设", "三大法宝", "中国革命", "革命道路")),
            ("第三章 社会主义改造理论", ("社会主义改造", "三大改造", "过渡时期总路线", "新民主主义社会")),
            ("第四章 社会主义建设道路初步探索的理论成果", ("论十大关系", "人民内部矛盾", "社会主义建设道路", "第二次结合")),
            ("第五章 中国特色社会主义理论体系的形成发展", ("中国特色社会主义理论体系", "马克思主义中国化时代化")),
            ("第六章 邓小平理论", ("邓小平理论", "社会主义初级阶段", "三个有利于", "发展才是硬道理", "改革发展稳定", "共同富裕", "社会主义本质")),
            ("第七章 “三个代表”重要思想", ("三个代表", "执政兴国的第一要务", "先进生产力", "先进文化", "最广大人民根本利益")),
            ("第八章 科学发展观", ("科学发展观", "以人为本", "全面协调可持续")),
            ("第1章 新时代坚持和发展中国特色社会主义", ("新时代中国特色社会主义思想", "坚持和发展中国特色社会主义", "核心要义", "新时代坚持和发展中国特色社会主义")),
            ("第2章 以中国式现代化全面推进中华民族伟大复兴", ("中国式现代化", "中华民族伟大复兴", "中国梦", "强国建设", "脱贫攻坚", "全面建成小康社会", "小康社会")),
            ("第3章 坚持党的全面领导", ("坚持党的全面领导", "党的领导制度", "党中央集中统一领导", "党的建设新的伟大工程", "群众路线", "执政本领")),
            ("第4章 坚持以人民为中心", ("以人民为中心", "人民至上", "人民立场", "共享发展成果", "增进民生福祉")),
            ("第5章 全面深化改革开放", ("全面深化改革", "改革开放", "制度型开放", "外商投资法", "自贸港", "对外开放", "走出去")),
            ("第6章 推动高质量发展", ("高质量发展", "新发展理念", "现代化经济体系", "双循环", "乡村振兴", "三农", "实体经济", "区域协调发展", "创新、协调、绿色、开放、共享", "创新驱动", "数字经济", "粮食安全", "藏粮于地", "现代化产业体系", "土地流转", "三权分置", "农村基本经营制度", "农业经营方式", "农业专业合作社")),
            ("第7章 社会主义现代化建设的教育、科技、人才战略", ("教育", "科技", "人才", "科教兴国", "战略支撑")),
            ("第8章 发展全过程人民民主", ("全过程人民民主", "人民当家作主", "社会主义民主政治", "基层民主", "政治发展道路", "基层群众自治", "协商民主")),
            ("第9章 全面依法治国", ("全面依法治国", "法治国家", "法治政府", "法治社会")),
            ("第10章 建设社会主义文化强国", ("文化强国", "文化自信", "社会主义核心价值观", "社会主义核心价值体系", "公共文化服务", "文化体制改革", "文化事业", "文化产业")),
            ("第11章 以保障和改善民生为重点加强社会建设", ("民生", "社会保障", "就业", "健康中国", "群众利益", "人民健康", "社会建设", "社会治理", "枫桥经验")),
            ("第12章 建设社会主义生态文明", ("生态文明", "资源节约型", "环境友好型", "环境保护", "美丽中国", "绿色发展", "节约资源", "生态环境")),
            ("第13章 维护和塑造国家安全", ("总体国家安全观", "国家安全", "平安中国", "政治安全", "人民安全", "国家利益至上")),
            ("第14章 建设巩固国防和强大人民军队", ("国防", "军队", "强军", "人民军队", "国防和军队现代化")),
            ("第15章 坚持“一国两制”和推进祖国完全统一", ("一国两制", "祖国完全统一", "和平统一", "港澳", "台湾", "西藏", "民族问题")),
            ("第16章 中国特色大国外交和推动构建人类命运共同体", ("人类命运共同体", "中国特色大国外交", "和平发展道路", "东盟", "二十国", "国际社会", "全球经济失衡")),
            ("第17章 全面从严治党", ("全面从严治党", "党要管党", "党的建设", "学习型政党", "党的自我革命")),
        ],
        3: [
            ("第一章 进入近代后中华民族的磨难与抗争", ("鸦片战争", "南京条约", "望厦条约", "黄埔条约", "列强侵略", "外国侵略", "民族危机", "资本—帝国主义列强对中国的侵略")),
            ("第二章 不同社会力量对国家出路的早期探索", ("洋务运动", "太平天国", "戊戌维新", "早期探索")),
            ("第三章 辛亥革命与君主专制制度的终结", ("辛亥革命", "三民主义", "君主专制", "临时约法")),
            ("第四章 中国共产党成立和中国革命新局面", ("五四运动", "十月革命", "马克思主义在中国", "中国共产党成立", "新文化运动", "国民革命")),
            ("第五章 中国革命的新道路", ("农村包围城市", "土地革命", "长征", "武装夺取政权", "中国革命的新道路")),
            ("第六章 中华民族的抗日战争", ("抗日战争", "皖南事变", "抗日民族统一战线", "敌后战场", "游击战争")),
            ("第七章 为建立新中国而奋斗", ("解放战争", "第二条战线", "新中国成立前夕", "为建立新中国而奋斗", "政治协商会议", "和平建国", "政协协议")),
            ("第八章 中华人民共和国的成立与中国社会主义建设道路的探索", ("中华人民共和国成立", "人民代表大会制度", "社会主义基本制度", "论十大关系", "人民内部矛盾", "社会主义建设道路的探索")),
            ("第九章 改革开放与中国特色社会主义的开创和发展", ("改革开放", "十一届三中全会", "中国特色社会主义道路", "中国特色社会主义理论体系", "农村改革", "人民公社", "突破性进展")),
            ("第十章 中国特色社会主义进入新时代", ("中国特色社会主义进入新时代", "新时代")),
        ],
        4: [
            ("第一章 领悟人生真谛 把握人生方向", ("人生观", "人生真谛", "幸福观", "价值观")),
            ("第二章 追求远大理想 坚定崇高信念", ("理想信念", "远大理想", "崇高信念")),
            ("第三章 继承优良传统 弘扬中国精神", ("爱国主义", "民族精神", "时代精神", "中国精神", "祖国", "民族复兴")),
            ("第四章 明确价值要求 践行价值准则", ("社会主义核心价值观", "核心价值体系", "价值准则")),
            ("第五章 遵守道德规范 锤炼道德品格", ("道德", "品格", "公民道德", "职业道德", "家庭美德", "品德", "诚实守信")),
            ("第六章 学习法治思想 提升法治素养", ("法治", "依法治国", "宪法", "法律权利", "法律义务", "社会主义法治理念", "刑法", "诉讼", "调解", "法律权威")),
        ],
    }

    for chapter_name, keywords in keyword_groups.get(root_id, []):
        if any(keyword in text for keyword in keywords):
            return chapter_name
    return ""


def infer_kyzz_content_root_category_id(record: dict[str, Any]) -> int | None:
    text = "\n".join(
        value
        for value in (
            stringify(record.get("title")),
            stringify(record.get("jiexi")),
            stringify(record.get("analysis")),
        )
        if value
    )

    root_keywords = (
        (5, ("世界贸易组织", "中俄建交", "国际进口博览会", "进博会", "国际经济论坛", "二十国领导人", "德班", "联合国气候变化框架公约", "国际社会", "峰会", "世界500强", "贸易伙伴")),
        (3, ("鸦片战争", "南京条约", "望厦条约", "黄埔条约", "太平天国", "洋务运动", "戊戌维新", "辛亥革命", "五四运动", "中国共产党成立", "抗日战争", "解放战争", "新民主主义社会", "列强侵略", "农村改革", "人民公社", "政治协商会议", "和平建国")),
        (4, ("人生观", "理想信念", "爱国主义", "民族精神", "时代精神", "公民道德", "职业道德", "诚实守信", "法律权威", "刑法", "诉讼", "调解", "依法治国", "社会主义法治理念")),
        (2, ("中国特色社会主义理论体系", "共同富裕", "改革开放", "新发展理念", "高质量发展", "现代化经济体系", "文化强国", "生态文明", "国家安全", "一国两制", "全面从严治党", "社会主义初级阶段", "邓小平理论", "三个代表", "科学发展观", "群众路线", "执政本领")),
        (1, ("马克思主义", "物质", "意识", "实践", "认识", "真理", "社会存在", "生产力", "剩余价值", "资本主义", "社会主义", "共产主义", "必然", "偶然", "人民群众", "唯物史观")),
    )
    for root_id, keywords in root_keywords:
        if any(keyword in text for keyword in keywords):
            return root_id

    matched_roots = [root_id for root_id in (1, 2, 3, 4) if infer_kyzz_chapter_name(record, root_id)]
    if len(matched_roots) == 1:
        return matched_roots[0]
    return None


def infer_root_category_id(record: dict[str, Any], default_category_id: int) -> int:
    explicit = parse_int(record.get("categoryId"))
    if explicit is not None:
        return explicit

    canonical_root = infer_root_from_canonical_chapter_name(record)
    if canonical_root is not None:
        return canonical_root

    topic_candidates = [
        stringify(record.get("top_kaodian_text")),
        stringify(record.get("p_kaodian_text")),
        stringify(record.get("kaodian_level_text")),
        stringify(record.get("kaodian")),
    ]

    for topic in topic_candidates:
        if not topic:
            continue
        exact_match = EXACT_CATEGORY_MAP.get(topic)
        if exact_match is not None:
            return exact_match

    merged = " ".join(topic for topic in topic_candidates if topic)
    for category_id, keywords in CATEGORY_KEYWORD_MAP.items():
        if any(keyword in merged for keyword in keywords):
            return category_id

    if is_kyzz_record(record):
        content_root_id = infer_kyzz_content_root_category_id(record)
        if content_root_id is not None:
            return content_root_id
        kyzz_root_id = infer_kyzz_root_category_id(record)
        if kyzz_root_id is not None:
            return kyzz_root_id
    return default_category_id


def infer_theory_section_hint(record: dict[str, Any]) -> str | None:
    merged = " ".join(
        value
        for value in (
            stringify(record.get("top_kaodian_text")),
            stringify(record.get("kaodian_level_text")),
            stringify(record.get("kaodian")),
            stringify(record.get("tags")),
        )
        if value
    )
    if "新时代中国特色社会主义思想" in merged or "习近平新时代中国特色社会主义思想" in merged:
        return "xijinping"
    if "毛泽东思想和中国特色社会主义理论体系概论" in merged or "毛泽东思想" in merged:
        return "maozhongte"
    return None


def infer_root_from_canonical_chapter_name(record: dict[str, Any]) -> int | None:
    chapter_name = stringify(record.get("p_kaodian_text"))
    if not chapter_name:
        return None

    matched_roots = [
        root_id
        for root_id in (1, 2, 3, 4)
        if politics_syllabus.resolve_canonical_category_id(
            root_id,
            chapter_name,
            section_hint=infer_theory_section_hint(record),
        ) is not None
    ]
    if len(matched_roots) == 1:
        return matched_roots[0]
    return None


def chapter_sort_key(name: str) -> tuple[int, int, str]:
    name = name.strip()
    if not name:
        return (9, 999, name)
    if "导论" in name:
        return (0, 0, name)

    match = re.search(r"第([一二三四五六七八九十零0-9]+)章", name)
    if match:
        number = chinese_numeral_to_int(match.group(1))
        if number is not None:
            return (1, number, name)
    return (2, 999, name)


def build_category_catalog(records: list[dict[str, Any]], default_category_id: int) -> dict[int, list[dict[str, Any]]]:
    present_roots = {
        infer_root_category_id(record, default_category_id)
        for record in records
    }
    chapter_names_by_root: dict[int, set[str]] = {}
    for record in records:
        root_id = infer_root_category_id(record, default_category_id)
        chapter_name = stringify(record.get("p_kaodian_text"))
        if not chapter_name:
            continue
        chapter_names_by_root.setdefault(root_id, set()).add(chapter_name)

    catalog: dict[int, list[dict[str, Any]]] = {}
    for root_id in sorted(present_roots):
        if politics_syllabus.has_canonical_catalog(root_id):
            catalog[root_id] = politics_syllabus.build_canonical_category_catalog(root_id)
            continue

        chapter_names = chapter_names_by_root.get(root_id, set())
        if not chapter_names:
            continue
        sorted_names = sorted(chapter_names, key=chapter_sort_key)
        catalog[root_id] = [
            {
                "id": root_id * 1000 + index,
                "name": name,
                "description": "Imported from kyzz dataset",
                "sort": index,
                "parent_id": root_id,
                "practice_mode": 2,
                "status": 1,
            }
            for index, name in enumerate(sorted_names, start=1)
        ]
    return catalog


def build_chapter_category_id_map(catalog: dict[int, list[dict[str, Any]]]) -> dict[tuple[int, str], int]:
    category_id_map: dict[tuple[int, str], int] = {}
    for root_id, chapters in catalog.items():
        for chapter in chapters:
            category_id_map[(root_id, chapter["name"])] = chapter["id"]
    return category_id_map


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def render_category_seed_sql(catalog: dict[int, list[dict[str, Any]]]) -> str:
    rows = [chapter for chapters in catalog.values() for chapter in chapters]
    if not rows:
        return "-- No chapter categories detected.\n"

    values = []
    for row in sorted(rows, key=lambda item: item["id"]):
        values.append(
            "  ({id}, '{name}', '{description}', {sort}, {parent_id}, {practice_mode}, {status})".format(
                id=row["id"],
                name=sql_escape(row["name"]),
                description=sql_escape(row["description"]),
                sort=row["sort"],
                parent_id=row["parent_id"],
                practice_mode=row["practice_mode"],
                status=row["status"],
            )
        )

    return (
        "-- Generated by dataset_to_excel.py\n"
        "-- Import this after deploy/init_database.sql so root categories 1-5 already exist.\n\n"
        "SET NAMES utf8mb4;\n\n"
        "INSERT INTO `category` (`id`, `name`, `description`, `sort`, `parent_id`, `practice_mode`, `status`) VALUES\n"
        + ",\n".join(values)
        + "\nON DUPLICATE KEY UPDATE\n"
        "  `name` = VALUES(`name`),\n"
        "  `description` = VALUES(`description`),\n"
        "  `sort` = VALUES(`sort`),\n"
        "  `parent_id` = VALUES(`parent_id`),\n"
        "  `practice_mode` = VALUES(`practice_mode`),\n"
        "  `status` = VALUES(`status`);\n"
    )


def write_category_seed_sql(path: Path, catalog: dict[int, list[dict[str, Any]]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(render_category_seed_sql(catalog), encoding="utf-8")


def resolve_category_id(
    record: dict[str, Any],
    default_category_id: int,
    category_mode: str,
    chapter_category_id_map: dict[tuple[int, str], int] | None,
) -> int:
    root_id = infer_root_category_id(record, default_category_id)
    if category_mode != "chapter" or not chapter_category_id_map:
        return root_id

    chapter_name = stringify(record.get("p_kaodian_text"))
    if chapter_name:
        canonical_category_id = politics_syllabus.resolve_canonical_category_id(
            root_id,
            chapter_name,
            section_hint=infer_theory_section_hint(record),
        )
        if canonical_category_id is not None:
            return canonical_category_id
        category_id = chapter_category_id_map.get((root_id, chapter_name))
        if category_id is not None:
            return category_id
    return root_id


def convert_record(
    record: dict[str, Any],
    mapping: dict[str, list[str]],
    default_category_id: int,
    default_source: str,
    default_source_type: str,
    default_difficulty: int,
    default_status: int,
    category_mode: str = "top",
    chapter_category_id_map: dict[tuple[int, str], int] | None = None,
) -> list[str] | None:
    content = stringify(pick_value(record, mapping, "content"))
    if not content:
        return None

    root_id = infer_root_category_id(record, default_category_id)
    normalized_record = record
    raw_chapter_name = stringify(record.get("p_kaodian_text"))
    if not raw_chapter_name and is_kyzz_record(record):
        inferred_chapter_name = infer_kyzz_chapter_name(record, root_id)
        if inferred_chapter_name:
            normalized_record = dict(normalized_record)
            normalized_record["p_kaodian_text"] = inferred_chapter_name
            raw_chapter_name = inferred_chapter_name
    section_hint = infer_theory_section_hint(record)
    canonical_chapter_name = politics_syllabus.resolve_canonical_chapter_name(
        root_id,
        raw_chapter_name,
        section_hint=section_hint,
    )
    if canonical_chapter_name and canonical_chapter_name != raw_chapter_name:
        normalized_record = dict(record)
        normalized_record["p_kaodian_text"] = canonical_chapter_name

    options, inferred_answer = extract_option_entries(record, mapping)
    correct_answer = normalize_correct_answer(pick_value(record, mapping, "correctAnswer"), options, inferred_answer)
    type_value = normalize_type(pick_value(record, mapping, "type"), len(options), correct_answer, content)
    difficulty_value = normalize_difficulty(pick_value(record, mapping, "difficulty"), default_difficulty)

    if "categoryId" not in normalized_record:
        explicit_category_id = pick_value(normalized_record, mapping, "categoryId")
        if explicit_category_id is not None:
            normalized_record = dict(normalized_record)
            normalized_record["categoryId"] = explicit_category_id
    category_override = normalize_category_override(normalized_record)
    category_id = category_override if category_override is not None else resolve_category_id(normalized_record, default_category_id, category_mode, chapter_category_id_map)
    status_value = normalize_status(pick_value(normalized_record, mapping, "status"), default_status)

    option_map = {label: text for label, text in options}
    source_default = normalized_record.get("__source_stem__") or default_source
    source_value = normalize_source(pick_value(normalized_record, mapping, "source"), source_default)

    return [
        content,
        TYPE_OUTPUT[type_value],
        DIFFICULTY_OUTPUT[difficulty_value],
        build_tags(normalized_record, mapping),
        source_value,
        normalize_source_type(pick_value(normalized_record, mapping, "sourceType"), source_value, default_source_type),
        option_map.get("A", ""),
        option_map.get("B", ""),
        option_map.get("C", ""),
        option_map.get("D", ""),
        correct_answer,
        stringify(pick_value(normalized_record, mapping, "analysis")),
        stringify(pick_value(normalized_record, mapping, "solutionStrategy")),
        str(category_id),
        str(status_value),
    ]


def xml_text(value: str) -> str:
    clean = "".join(ch for ch in value if ch == "\t" or ch == "\n" or ch == "\r" or ord(ch) >= 32)
    return escape(clean, {'"': "&quot;"})


def column_name(index: int) -> str:
    result = ""
    current = index
    while current:
        current, remainder = divmod(current - 1, 26)
        result = chr(65 + remainder) + result
    return result


def inline_cell(ref: str, value: str) -> str:
    return (
        f'<c r="{ref}" t="inlineStr">'
        f'<is><t xml:space="preserve">{xml_text(value)}</t></is>'
        f"</c>"
    )


def build_sheet_xml(rows: list[list[str]]) -> str:
    xml_rows = []
    for row_index, row in enumerate(rows, start=1):
        cells = [
            inline_cell(f"{column_name(col_index)}{row_index}", stringify(value))
            for col_index, value in enumerate(row, start=1)
        ]
        xml_rows.append(f'<row r="{row_index}">{"".join(cells)}</row>')

    return (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
        "<sheetData>"
        + "".join(xml_rows)
        + "</sheetData></worksheet>"
    )


def write_xlsx(path: Path, rows: list[list[str]]) -> None:
    created = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(
            "[Content_Types].xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
            '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
            '<Default Extension="xml" ContentType="application/xml"/>'
            '<Override PartName="/xl/workbook.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
            '<Override PartName="/xl/worksheets/sheet1.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
            '<Override PartName="/xl/styles.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
            '<Override PartName="/docProps/core.xml" '
            'ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>'
            '<Override PartName="/docProps/app.xml" '
            'ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>'
            "</Types>",
        )
        archive.writestr(
            "_rels/.rels",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>'
            '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>'
            '<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>'
            "</Relationships>",
        )
        archive.writestr(
            "docProps/app.xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" '
            'xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">'
            "<Application>dataset_to_excel.py</Application></Properties>",
        )
        archive.writestr(
            "docProps/core.xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" '
            'xmlns:dc="http://purl.org/dc/elements/1.1/" '
            'xmlns:dcterms="http://purl.org/dc/terms/" '
            'xmlns:dcmitype="http://purl.org/dc/dcmitype/" '
            'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            "<dc:creator>Codex</dc:creator>"
            "<cp:lastModifiedBy>Codex</cp:lastModifiedBy>"
            f'<dcterms:created xsi:type="dcterms:W3CDTF">{created}</dcterms:created>'
            f'<dcterms:modified xsi:type="dcterms:W3CDTF">{created}</dcterms:modified>'
            "</cp:coreProperties>",
        )
        archive.writestr(
            "xl/workbook.xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
            'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
            '<sheets><sheet name="Questions" sheetId="1" r:id="rId1"/></sheets></workbook>',
        )
        archive.writestr(
            "xl/_rels/workbook.xml.rels",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
            '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
            "</Relationships>",
        )
        archive.writestr(
            "xl/styles.xml",
            '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            '<fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>'
            '<fills count="1"><fill><patternFill patternType="none"/></fill></fills>'
            '<borders count="1"><border/></borders>'
            '<cellStyleXfs count="1"><xf/></cellStyleXfs>'
            '<cellXfs count="1"><xf xfId="0"/></cellXfs>'
            '<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>'
            "</styleSheet>",
        )
        archive.writestr("xl/worksheets/sheet1.xml", build_sheet_xml(rows))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert dataset files into the project's importable .xlsx template.")
    parser.add_argument("--input", required=True, help="Input dataset path or directory")
    parser.add_argument("--output", required=True, help="Output .xlsx path")
    parser.add_argument("--category-id", type=int, default=1, help="Fallback categoryId when the source does not provide one")
    parser.add_argument("--category-mode", choices=("top", "chapter"), default="top", help="Map records to top categories or generated chapter categories")
    parser.add_argument("--category-seed-output", help="Optional SQL path for generated chapter category seed data")
    parser.add_argument("--source", default="", help="Fallback source value")
    parser.add_argument("--source-type", default="", help="Fallback source type: 真题/模拟题 or real-exam/mock-exam")
    parser.add_argument("--difficulty", default="medium", help="Fallback difficulty: basic/medium/hard or 1/2/3")
    parser.add_argument("--status", type=int, default=1, help="Fallback status value, usually 1")
    parser.add_argument("--mapping", help="Optional JSON mapping file")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    mapping_path = Path(args.mapping).resolve() if args.mapping else None

    if not input_path.exists():
        print(f"Input file not found: {input_path}", file=sys.stderr)
        return 1

    try:
        mapping = load_mapping(mapping_path)
        input_paths = collect_input_paths(input_path)
        default_difficulty = normalize_difficulty(args.difficulty, 2)
        default_source = args.source.strip() or input_path.stem
        default_source_type = normalize_source_type(args.source_type, default_source, "")

        records: list[dict[str, Any]] = []
        for source_path in input_paths:
            records.extend(load_records(source_path))

        category_catalog = build_category_catalog(records, args.category_id) if (args.category_mode == "chapter" or args.category_seed_output) else {}
        chapter_category_id_map = build_chapter_category_id_map(category_catalog) if category_catalog else None

        rows = [HEADERS]
        converted = 0
        skipped = 0
        for record in records:
            row = convert_record(
                record=record,
                mapping=mapping,
                default_category_id=args.category_id,
                default_source=default_source,
                default_source_type=default_source_type,
                default_difficulty=default_difficulty,
                default_status=args.status,
                category_mode=args.category_mode,
                chapter_category_id_map=chapter_category_id_map,
            )
            if row is None:
                skipped += 1
                continue
            rows.append(row)
            converted += 1

        output_path.parent.mkdir(parents=True, exist_ok=True)
        write_xlsx(output_path, rows)
        if args.category_seed_output:
            write_category_seed_sql(Path(args.category_seed_output).resolve(), category_catalog)

        print(f"Input files: {len(input_paths)}")
        print(f"Converted {converted} rows")
        print(f"Skipped {skipped} rows")
        print(f"Output: {output_path}")
        if args.category_seed_output:
            chapter_count = sum(len(chapters) for chapters in category_catalog.values())
            print(f"Category seed: {Path(args.category_seed_output).resolve()} ({chapter_count} chapters)")
        print("Template columns: " + ", ".join(HEADERS))
        return 0
    except Exception as exc:  # noqa: BLE001
        print(f"Conversion failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
