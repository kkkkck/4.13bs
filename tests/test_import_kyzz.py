import unittest
from pathlib import Path

from deploy import import_kyzz


class ImportKyzzScriptTests(unittest.TestCase):
    def test_build_generate_command_includes_chapter_seed_when_provided(self) -> None:
        command = import_kyzz.build_generate_command(
            python_executable="python",
            dataset_script=Path("dataset_to_excel.py"),
            input_path=Path("dataset/kyzz/data"),
            output_path=Path("dataset/exports/kyzz-2010-2024-chapter.xlsx"),
            category_mode="chapter",
            category_seed_output=Path("deploy/seed_kyzz_categories.sql"),
        )

        self.assertEqual("python", command[0])
        self.assertIn("--category-mode", command)
        self.assertIn("chapter", command)
        self.assertIn("--category-seed-output", command)
        self.assertIn(str(Path("deploy/seed_kyzz_categories.sql")), command)

    def test_build_mysql_command_only_adds_database_when_requested(self) -> None:
        config = import_kyzz.MysqlConfig(
            binary="mysql",
            host="127.0.0.1",
            port=3306,
            user="root",
            password="secret",
            database="question_bank",
        )

        init_command = import_kyzz.build_mysql_command(config, use_database=False)
        seed_command = import_kyzz.build_mysql_command(config, use_database=True)

        self.assertNotIn("--database=question_bank", init_command)
        self.assertIn("--database=question_bank", seed_command)
        self.assertIn("--password=secret", seed_command)

    def test_build_multipart_form_data_embeds_uploaded_file(self) -> None:
        content_type, body = import_kyzz.build_multipart_form_data(
            fields={"mode": "chapter"},
            files={
                "file": (
                    "kyzz.xlsx",
                    b"binary-xlsx-content",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                )
            },
            boundary="TestBoundary",
        )

        self.assertEqual("multipart/form-data; boundary=TestBoundary", content_type)
        self.assertIn(b'name="mode"', body)
        self.assertIn(b"chapter", body)
        self.assertIn(b'name="file"; filename="kyzz.xlsx"', body)
        self.assertIn(b"binary-xlsx-content", body)
        self.assertTrue(body.endswith(b"--TestBoundary--\r\n"))

    def test_rewrite_init_sql_database_retargets_create_and_use(self) -> None:
        sql = (
            "CREATE DATABASE IF NOT EXISTS `question_bank` DEFAULT CHARACTER SET utf8mb4;\n"
            "USE `question_bank`;\n"
            "CREATE TABLE `demo` (`id` BIGINT);\n"
        )

        rewritten = import_kyzz.rewrite_init_sql_database(sql, "question_bank_verify")

        self.assertIn("CREATE DATABASE IF NOT EXISTS `question_bank_verify`", rewritten)
        self.assertIn("USE `question_bank_verify`;", rewritten)
        self.assertNotIn("USE `question_bank`;", rewritten)

    def test_unwrap_result_returns_data_for_success(self) -> None:
        payload = {"code": 200, "message": "success", "data": {"token": "abc"}}

        data = import_kyzz.unwrap_result(payload, context="Admin login")

        self.assertEqual({"token": "abc"}, data)

    def test_unwrap_result_raises_for_failed_payload(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "Admin login failed: unauthorized"):
            import_kyzz.unwrap_result(
                {"code": 401, "message": "unauthorized", "data": None},
                context="Admin login",
            )


if __name__ == "__main__":
    unittest.main()
