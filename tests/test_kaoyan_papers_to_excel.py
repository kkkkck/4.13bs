import unittest
from pathlib import Path

import kaoyan_papers_to_excel as converter


class KaoyanPapersToExcelTests(unittest.TestCase):
    def test_parse_objective_supplements_reads_inline_answer_analysis(self) -> None:
        blocks = {
            1: "1.C【解析】马克思之所以“行”，根本原因在于它是科学的唯物主义。",
        }

        supplements = converter.parse_objective_supplements(blocks)

        self.assertEqual("C", supplements[1]["correctAnswer"])
        self.assertIn("科学的唯物主义", supplements[1]["analysis"])

    def test_split_material_content_and_analysis_handles_repeated_subquestions(self) -> None:
        block = """
34.结合材料回答问题：
(1)分析题目一。
(2)分析题目二。

(1)分析题目一。
这是第一问答案。

(2)分析题目二。
这是第二问答案。
""".strip()

        content, analysis = converter.split_material_content_and_analysis(block)

        self.assertIn("(1)分析题目一。", content)
        self.assertIn("(2)分析题目二。", content)
        self.assertIn("这是第一问答案。", analysis)
        self.assertIn("这是第二问答案。", analysis)

    def test_build_records_returns_expected_total(self) -> None:
        records = converter.build_records(
            solutions_dir=Path("dataset/Kaoyan-Politics-Papers/solutions"),
            kyzz_dir=Path("dataset/kyzz/data"),
            mode="all",
        )

        self.assertEqual(152, len(records))
        self.assertEqual(20, sum(1 for item in records if item.get("type") == "short-answer"))
        self.assertEqual(132, sum(1 for item in records if item.get("type") != "short-answer"))

    def test_build_records_objective_mode_excludes_materials(self) -> None:
        records = converter.build_records(
            solutions_dir=Path("dataset/Kaoyan-Politics-Papers/solutions"),
            kyzz_dir=Path("dataset/kyzz/data"),
            mode="objective",
        )

        self.assertEqual(132, len(records))
        self.assertTrue(all(item.get("type") != "short-answer" for item in records))


if __name__ == "__main__":
    unittest.main()
