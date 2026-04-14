#!/usr/bin/env python3
"""
Generate, seed, and import the kyzz dataset into the local question bank.

This script intentionally stays in the standard library so it can run in
minimal environments where the backend and MySQL are already available.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from urllib import error, request


DEFAULT_BACKEND_BASE_URL = "http://127.0.0.1:8080"
DEFAULT_ADMIN_ACCOUNT = "admin@example.com"
DEFAULT_ADMIN_PASSWORD = "admin123"
DEFAULT_MYSQL_HOST = "127.0.0.1"
DEFAULT_MYSQL_PORT = 3306
DEFAULT_MYSQL_USER = "root"
DEFAULT_MYSQL_DATABASE = "question_bank"


@dataclass(frozen=True)
class MysqlConfig:
    binary: str
    host: str
    port: int
    user: str
    password: str
    database: str


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def resolve_repo_path(raw_path: str) -> Path:
    path = Path(raw_path)
    if path.is_absolute():
        return path
    return repo_root() / path


def print_step(message: str) -> None:
    print(f"[kyzz] {message}")


def write_stream_text(stream: Any, text: str) -> None:
    if not text:
        return
    encoding = getattr(stream, "encoding", None) or "utf-8"
    buffer = getattr(stream, "buffer", None)
    if buffer is not None:
        buffer.write(text.encode(encoding, errors="replace"))
        stream.flush()
        return
    stream.write(text.encode(encoding, errors="replace").decode(encoding, errors="replace"))
    stream.flush()


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="One-command kyzz dataset generation and import workflow.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--input", default="dataset/kyzz/data", help="kyzz dataset directory or file")
    parser.add_argument(
        "--output",
        default="dataset/exports/kyzz-2010-2024-chapter.xlsx",
        help="Generated Excel file path",
    )
    parser.add_argument(
        "--category-mode",
        choices=("top", "chapter"),
        default="chapter",
        help="Category mapping mode for the generated import file",
    )
    parser.add_argument(
        "--category-seed-output",
        default="deploy/seed_kyzz_categories.sql",
        help="Generated category seed SQL path",
    )
    parser.add_argument(
        "--init-sql",
        default="deploy/init_database.sql",
        help="Database initialization SQL path",
    )
    parser.add_argument("--skip-generate", action="store_true", help="Skip running dataset_to_excel.py")
    parser.add_argument("--skip-db-init", action="store_true", help="Skip applying the init SQL")
    parser.add_argument("--skip-category-seed", action="store_true", help="Skip applying the category seed SQL")
    parser.add_argument("--skip-api-import", action="store_true", help="Skip the backend login and import call")
    parser.add_argument(
        "--allow-partial-import",
        action="store_true",
        help="Do not fail when the import endpoint reports failed rows",
    )
    parser.add_argument("--mysql-binary", default="mysql", help="MySQL client binary")
    parser.add_argument("--mysql-host", default=os.getenv("MYSQL_HOST", DEFAULT_MYSQL_HOST), help="MySQL host")
    parser.add_argument(
        "--mysql-port",
        type=int,
        default=int(os.getenv("MYSQL_PORT", str(DEFAULT_MYSQL_PORT))),
        help="MySQL port",
    )
    parser.add_argument("--mysql-user", default=os.getenv("MYSQL_USERNAME", DEFAULT_MYSQL_USER), help="MySQL user")
    parser.add_argument(
        "--mysql-password",
        default=os.getenv("MYSQL_PASSWORD", ""),
        help="MySQL password. Empty means no password flag will be passed.",
    )
    parser.add_argument(
        "--mysql-database",
        default=os.getenv("MYSQL_DATABASE", DEFAULT_MYSQL_DATABASE),
        help="MySQL database name",
    )
    parser.add_argument(
        "--backend-base-url",
        default=os.getenv("KYZZ_BACKEND_BASE_URL", DEFAULT_BACKEND_BASE_URL),
        help="Backend base URL, without the /api suffix",
    )
    parser.add_argument(
        "--admin-account",
        default=os.getenv("KYZZ_ADMIN_ACCOUNT", DEFAULT_ADMIN_ACCOUNT),
        help="Admin login account",
    )
    parser.add_argument(
        "--admin-password",
        default=os.getenv("KYZZ_ADMIN_PASSWORD", DEFAULT_ADMIN_PASSWORD),
        help="Admin login password",
    )
    parser.add_argument(
        "--backend-timeout-seconds",
        type=float,
        default=120.0,
        help="How long to wait for /api/health before failing",
    )
    parser.add_argument(
        "--backend-poll-interval-seconds",
        type=float,
        default=2.0,
        help="Polling interval while waiting for /api/health",
    )
    parser.add_argument(
        "--request-timeout-seconds",
        type=float,
        default=30.0,
        help="HTTP request timeout for login and import calls",
    )
    return parser.parse_args(argv)


def build_generate_command(
    python_executable: str,
    dataset_script: Path,
    input_path: Path,
    output_path: Path,
    category_mode: str,
    category_seed_output: Path | None,
) -> list[str]:
    command = [
        python_executable,
        str(dataset_script),
        "--input",
        str(input_path),
        "--output",
        str(output_path),
        "--category-mode",
        category_mode,
    ]
    if category_seed_output is not None:
        command.extend(["--category-seed-output", str(category_seed_output)])
    return command


def run_command(
    command: list[str],
    *,
    cwd: Path | None = None,
    input_text: str | None = None,
    description: str,
) -> None:
    completed = subprocess.run(
        command,
        cwd=str(cwd) if cwd else None,
        input=input_text,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
        check=False,
    )
    write_stream_text(sys.stdout, completed.stdout)
    write_stream_text(sys.stderr, completed.stderr)
    if completed.returncode != 0:
        raise RuntimeError(f"{description} failed with exit code {completed.returncode}")


def build_mysql_command(config: MysqlConfig, *, use_database: bool) -> list[str]:
    command = [
        config.binary,
        f"--host={config.host}",
        f"--port={config.port}",
        f"--user={config.user}",
        "--default-character-set=utf8mb4",
    ]
    if config.password:
        command.append(f"--password={config.password}")
    if use_database:
        command.append(f"--database={config.database}")
    return command


def apply_sql_file(sql_path: Path, config: MysqlConfig, *, use_database: bool) -> None:
    if not sql_path.exists():
        raise FileNotFoundError(f"SQL file not found: {sql_path}")

    sql_text = sql_path.read_text(encoding="utf-8")
    if not use_database:
        sql_text = rewrite_init_sql_database(sql_text, config.database)
    command = build_mysql_command(config, use_database=use_database)
    run_command(command, input_text=sql_text, description=f"Apply SQL {sql_path.name}")


def rewrite_init_sql_database(sql_text: str, database_name: str) -> str:
    rewritten = re.sub(
        r"CREATE DATABASE IF NOT EXISTS\s+`[^`]+`",
        f"CREATE DATABASE IF NOT EXISTS `{database_name}`",
        sql_text,
        count=1,
    )
    rewritten = re.sub(
        r"USE\s+`[^`]+`;",
        f"USE `{database_name}`;",
        rewritten,
        count=1,
    )
    return rewritten


def normalize_base_url(base_url: str) -> str:
    return base_url.rstrip("/")


def request_json(
    method: str,
    url: str,
    *,
    payload: Any | None = None,
    data: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout_seconds: float,
) -> Any:
    request_headers = dict(headers or {})
    body: bytes | None = data
    if payload is not None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request_headers.setdefault("Content-Type", "application/json; charset=utf-8")
    req = request.Request(url, data=body, headers=request_headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout_seconds) as response:
            charset = response.headers.get_content_charset("utf-8")
            text = response.read().decode(charset)
            if not text:
                return None
            return json.loads(text)
    except error.HTTPError as exc:
        charset = exc.headers.get_content_charset("utf-8")
        response_text = exc.read().decode(charset, errors="replace")
        message = response_text.strip() or exc.reason
        raise RuntimeError(f"HTTP {exc.code} for {url}: {message}") from exc
    except error.URLError as exc:
        raise RuntimeError(f"Network error for {url}: {exc.reason}") from exc


def unwrap_result(payload: Any, *, context: str) -> Any:
    if not isinstance(payload, dict):
        raise RuntimeError(f"{context} returned an unexpected payload: {payload!r}")
    code = payload.get("code")
    if code != 200:
        raise RuntimeError(f"{context} failed: {payload.get('message', 'unknown error')}")
    return payload.get("data")


def wait_for_backend(base_url: str, timeout_seconds: float, poll_interval_seconds: float) -> None:
    health_url = f"{normalize_base_url(base_url)}/api/health"
    deadline = time.monotonic() + timeout_seconds
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        try:
            payload = request_json("GET", health_url, timeout_seconds=min(poll_interval_seconds, 5.0))
            if isinstance(payload, dict) and payload.get("status") == "UP":
                return
            last_error = RuntimeError(f"Unexpected health response: {payload!r}")
        except Exception as exc:  # noqa: BLE001
            last_error = exc
        time.sleep(poll_interval_seconds)
    raise RuntimeError(f"Backend did not become healthy at {health_url}: {last_error}")


def login_admin(base_url: str, account: str, password: str, timeout_seconds: float) -> str:
    payload = request_json(
        "POST",
        f"{normalize_base_url(base_url)}/api/auth/login",
        payload={"account": account, "password": password},
        timeout_seconds=timeout_seconds,
    )
    data = unwrap_result(payload, context="Admin login")
    if not isinstance(data, dict) or not isinstance(data.get("token"), str):
        raise RuntimeError("Admin login response did not include a token")
    return data["token"]


def build_multipart_form_data(
    *,
    fields: dict[str, str] | None = None,
    files: dict[str, tuple[str, bytes, str]] | None = None,
    boundary: str | None = None,
) -> tuple[str, bytes]:
    form_boundary = boundary or f"----CodexBoundary{uuid.uuid4().hex}"
    chunks: list[bytes] = []

    for name, value in (fields or {}).items():
        chunks.extend(
            [
                f"--{form_boundary}\r\n".encode("utf-8"),
                f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode("utf-8"),
                value.encode("utf-8"),
                b"\r\n",
            ]
        )

    for field_name, (filename, content, content_type) in (files or {}).items():
        chunks.extend(
            [
                f"--{form_boundary}\r\n".encode("utf-8"),
                (
                    f'Content-Disposition: form-data; name="{field_name}"; '
                    f'filename="{filename}"\r\n'
                ).encode("utf-8"),
                f"Content-Type: {content_type}\r\n\r\n".encode("utf-8"),
                content,
                b"\r\n",
            ]
        )

    chunks.append(f"--{form_boundary}--\r\n".encode("utf-8"))
    return f"multipart/form-data; boundary={form_boundary}", b"".join(chunks)


def upload_import_file(base_url: str, token: str, file_path: Path, timeout_seconds: float) -> dict[str, Any]:
    if not file_path.exists():
        raise FileNotFoundError(f"Import file not found: {file_path}")

    content_type = mimetypes.guess_type(file_path.name)[0] or (
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )
    multipart_type, body = build_multipart_form_data(
        files={"file": (file_path.name, file_path.read_bytes(), content_type)}
    )
    payload = request_json(
        "POST",
        f"{normalize_base_url(base_url)}/api/admin/questions/import",
        data=body,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": multipart_type,
        },
        timeout_seconds=timeout_seconds,
    )
    if not isinstance(payload, dict):
        raise RuntimeError(f"Import endpoint returned an unexpected payload: {payload!r}")
    return payload


def ensure_file_exists(path: Path, *, label: str) -> None:
    if not path.exists():
        raise FileNotFoundError(f"{label} not found: {path}")


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    root = repo_root()
    input_path = resolve_repo_path(args.input)
    output_path = resolve_repo_path(args.output)
    init_sql_path = resolve_repo_path(args.init_sql)
    category_seed_output = resolve_repo_path(args.category_seed_output)
    dataset_script = root / "dataset_to_excel.py"

    mysql = MysqlConfig(
        binary=args.mysql_binary,
        host=args.mysql_host,
        port=args.mysql_port,
        user=args.mysql_user,
        password=args.mysql_password,
        database=args.mysql_database,
    )

    try:
        if not args.skip_generate:
            print_step("Generating kyzz Excel import file")
            seed_path = category_seed_output if args.category_mode == "chapter" else None
            command = build_generate_command(
                python_executable=sys.executable,
                dataset_script=dataset_script,
                input_path=input_path,
                output_path=output_path,
                category_mode=args.category_mode,
                category_seed_output=seed_path,
            )
            run_command(command, cwd=root, description="Generate kyzz Excel")
        else:
            ensure_file_exists(output_path, label="Generated Excel")

        if not args.skip_db_init:
            print_step(f"Applying init SQL: {init_sql_path}")
            apply_sql_file(init_sql_path, mysql, use_database=False)

        if args.category_mode == "chapter" and not args.skip_category_seed:
            print_step(f"Applying category seed SQL: {category_seed_output}")
            ensure_file_exists(category_seed_output, label="Category seed SQL")
            apply_sql_file(category_seed_output, mysql, use_database=True)

        if not args.skip_api_import:
            print_step(f"Waiting for backend health: {args.backend_base_url}")
            wait_for_backend(
                args.backend_base_url,
                timeout_seconds=args.backend_timeout_seconds,
                poll_interval_seconds=args.backend_poll_interval_seconds,
            )
            print_step("Logging in as admin")
            token = login_admin(
                args.backend_base_url,
                args.admin_account,
                args.admin_password,
                timeout_seconds=args.request_timeout_seconds,
            )
            print_step(f"Uploading import file: {output_path}")
            result = upload_import_file(
                args.backend_base_url,
                token,
                output_path,
                timeout_seconds=args.request_timeout_seconds,
            )
            success_count = int(result.get("successCount", 0))
            duplicate_count = int(result.get("duplicateCount", 0))
            fail_count = int(result.get("failCount", 0))
            total = int(result.get("total", success_count + duplicate_count + fail_count))
            print_step(
                f"Import finished: total={total}, success={success_count}, duplicates={duplicate_count}, fail={fail_count}"
            )
            if fail_count and not args.allow_partial_import:
                raise RuntimeError(f"Import completed with {fail_count} failed rows: {result.get('errors', [])}")

        print_step("kyzz workflow completed")
        return 0
    except Exception as exc:  # noqa: BLE001
        print(f"[kyzz] ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
