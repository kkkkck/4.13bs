#!/usr/bin/env python3
"""
Repair malformed single-choice / multiple-choice option fields in the local
question bank.

Targets rows where one or more of optionA/B/C/D is empty, and attempts to
reconstruct the four options from:
- option text fields containing embedded B/C/D labels
- stems whose trailing segment accidentally captured option A

Some irreducible OCR cases are handled with explicit overrides.
"""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass


MYSQL_CMD = [
    "cmd",
    "/c",
    "mysql --default-character-set=utf8mb4 -N -B -uroot -pyoungKCK20 question_bank",
]

OPTION_LABELS = ("A", "B", "C", "D")
LABEL_VARIANTS = {
    "B": r"[BR8]",
    "C": r"C",
    "D": r"(?:D|6|1\))",
}

MANUAL_OVERRIDES: dict[int, dict[str, str]] = {
    1639: {
        "content": "支持重大改革、护航国家发展，新时代我国立法更具针对性、有效性、可操作性。制定国家安全法、香港国安法、网络安全法等，坚决维护国家安全与核心利益，以宪法为统帅的中国特色社会主义法律体系不断完善。立法是",
        "optionA": "法治的龙头环节",
        "optionB": "法治的生命线",
        "optionC": "治国理政的基本方式",
        "optionD": "法律实施和实现的基本途径和重要环节",
    },
    2258: {
        "content": "党的十八大以来，我们党对经济社会发展提出了许多重大理论和战略，其中最重要、最主要的是",
        "optionA": "供给侧结构性改革",
        "optionB": "以人民为中心的发展思想",
        "optionC": "新发展理念",
        "optionD": "推动高质量发展",
    },
    2288: {
        "content": "哲学社会科学是人们认识世界、改造世界的重要工具，是推动历史发展和社会进步的重要力量。当代中国哲学社会科学区别于其他哲学社会科学的根本标志是",
        "optionA": "坚持以马克思主义为指导",
        "optionB": "坚持正确的政治方向",
        "optionC": "坚持党性原则",
        "optionD": "坚持以人民为中心",
    },
}


@dataclass
class QuestionRow:
    id: int
    content: str
    option_a: str
    option_b: str
    option_c: str
    option_d: str


def mysql_query(sql: str) -> str:
    completed = subprocess.run(
        MYSQL_CMD,
        input=sql,
        text=True,
        capture_output=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError((completed.stderr or completed.stdout).strip())
    return completed.stdout


def fetch_rows() -> list[QuestionRow]:
    output = mysql_query(
        """
USE question_bank;
SELECT id, content, COALESCE(option_a,''), COALESCE(option_b,''), COALESCE(option_c,''), COALESCE(option_d,'')
FROM question
WHERE type IN (1,5)
  AND (option_a IS NULL OR option_a='' OR option_b IS NULL OR option_b='' OR option_c IS NULL OR option_c='' OR option_d IS NULL OR option_d='')
ORDER BY id;
"""
    )
    rows: list[QuestionRow] = []
    for line in output.splitlines():
        parts = line.split("\t")
        if len(parts) != 6:
            continue
        rows.append(
            QuestionRow(
                id=int(parts[0]),
                content=parts[1],
                option_a=parts[2],
                option_b=parts[3],
                option_c=parts[4],
                option_d=parts[5],
            )
        )
    return rows


def clean_text(value: str) -> str:
    normalized = re.sub(r"\s+", " ", value or "")
    normalized = normalized.strip().strip("\"' ")
    return re.sub(r"^[,，、.．:：;；)\]]+\s*", "", normalized)


def split_content_head(content: str) -> tuple[str, str]:
    normalized = clean_text(content)
    match = re.search(r"(?<![A-Z0-9])A\s*[-\.．,，、:：]\s*", normalized)
    if not match:
        return normalized, ""
    return clean_text(normalized[: match.start()]), clean_text(normalized[match.end() :])


def find_next_marker(text: str, labels: list[str]) -> tuple[int, str, re.Match[str]] | None:
    matches: list[tuple[int, str, re.Match[str]]] = []
    for label in labels:
        variants = LABEL_VARIANTS.get(label, label)
        pattern = re.compile(rf"(?<![A-Z0-9])({variants})\s*[-\.．,，、:：]?\s*")
        match = pattern.search(text)
        if match:
            matches.append((match.start(), label, match))
    if not matches:
        return None
    return min(matches, key=lambda item: item[0])


def recover_options(row: QuestionRow) -> tuple[str, list[str]] | None:
    if row.id in MANUAL_OVERRIDES:
        override = MANUAL_OVERRIDES[row.id]
        return (
            override["content"],
            [override["optionA"], override["optionB"], override["optionC"], override["optionD"]],
        )

    stem, leading_option = split_content_head(row.content)
    segments = [
        segment
        for segment in [
            leading_option,
            clean_text(row.option_a),
            clean_text(row.option_b),
            clean_text(row.option_c),
            clean_text(row.option_d),
        ]
        if segment
    ]
    remaining = list(OPTION_LABELS)
    options: dict[str, str] = {}

    while segments and remaining:
        current_label = remaining.pop(0)
        text = clean_text(segments.pop(0))
        marker = find_next_marker(text, remaining)
        if marker:
            _, _, match = marker
            current_value = clean_text(text[: match.start()])
            if current_value:
                options[current_label] = current_value
            remainder = clean_text(text[match.end() :])
            if remainder:
                segments.insert(0, remainder)
        else:
            options[current_label] = text

    resolved_options = [clean_text(options.get(label, "")) for label in OPTION_LABELS]
    if not stem or not all(resolved_options):
        return None
    return stem, resolved_options


def escape_sql(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def apply_updates(rows: list[tuple[int, str, list[str]]]) -> None:
    if not rows:
        return

    statements = ["USE question_bank;"]
    for question_id, content, options in rows:
        statements.append(
            "UPDATE question SET content='{content}', option_a='{a}', option_b='{b}', option_c='{c}', option_d='{d}' WHERE id={id};".format(
                id=question_id,
                content=escape_sql(content),
                a=escape_sql(options[0]),
                b=escape_sql(options[1]),
                c=escape_sql(options[2]),
                d=escape_sql(options[3]),
            )
        )
    mysql_query("\n".join(statements))


def main() -> int:
    rows = fetch_rows()
    repaired: list[tuple[int, str, list[str]]] = []
    unresolved: list[int] = []

    for row in rows:
        resolved = recover_options(row)
        if not resolved:
            unresolved.append(row.id)
            continue
        repaired.append((row.id, resolved[0], resolved[1]))

    apply_updates(repaired)

    print(f"Scanned rows: {len(rows)}")
    print(f"Repaired rows: {len(repaired)}")
    print(f"Unresolved rows: {len(unresolved)}")
    if unresolved:
        print("Unresolved IDs:", ",".join(str(item) for item in unresolved))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
