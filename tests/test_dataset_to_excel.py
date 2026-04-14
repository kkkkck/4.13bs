import json
import tempfile
import unittest
from pathlib import Path

import dataset_to_excel as converter


class DatasetToExcelTests(unittest.TestCase):
    def test_load_records_from_nested_kyzz_json(self) -> None:
        payload = {
            "code": 1,
            "detail": {
                "timu": [
                    {"title": "Question 1", "type": "单选"},
                    {"title": "Question 2", "type": "多选"},
                ],
                "pdati": [],
            },
        }

        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "24.json"
            path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")

            records = converter.load_records(path)

        self.assertEqual(2, len(records))
        self.assertEqual("Question 1", records[0]["title"])
        self.assertEqual("24", records[0]["__source_stem__"])

    def test_convert_kyzz_record_parses_options_answers_and_root_category(self) -> None:
        record = {
            "title": "马克思主义真题示例",
            "type": "多选",
            "xuanxiang": json.dumps(
                {
                    "A": "A.实践观点",
                    "B": "B.形而上学",
                    "C": "C.联系观点",
                    "D": "D.静止观点",
                },
                ensure_ascii=False,
            ),
            "right_text": "AC",
            "jiexi": "实践和联系都属于正确表述。",
            "chuchu": "2024考研真题",
            "top_kaodian_text": "马克思主义基本原理概论",
            "p_kaodian_text": "第一章 辩证唯物论",
            "__source_stem__": "24",
        }

        row = converter.convert_record(
            record=record,
            mapping={},
            default_category_id=99,
            default_source="fallback",
            default_source_type="真题",
            default_difficulty=2,
            default_status=1,
        )

        self.assertIsNotNone(row)
        assert row is not None
        self.assertEqual("multiple-choice", row[1])
        self.assertEqual("真题", row[5])
        self.assertEqual("实践观点", row[6])
        self.assertEqual("联系观点", row[8])
        self.assertEqual("A,C", row[10])
        self.assertEqual("2024考研真题", row[4])
        self.assertEqual("1", row[13])
        self.assertIn("马克思主义基本原理概论", row[3])
        self.assertIn("第一章 辩证唯物论", row[3])

    def test_collect_input_paths_directory_ignores_tabular_derivatives(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            base = Path(temp_dir)
            (base / "10.json").write_text("[]", encoding="utf-8")
            (base / "11.jsonl").write_text("{}", encoding="utf-8")
            (base / "count.csv").write_text("a,b\n1,2\n", encoding="utf-8")

            paths = converter.collect_input_paths(base)

        self.assertEqual(["10.json", "11.jsonl"], [path.name for path in paths])

    def test_build_category_catalog_generates_stable_chapter_ids(self) -> None:
        records = [
            {
                "title": "题目 A",
                "top_kaodian_text": "马克思主义基本原理概论",
                "p_kaodian_text": "导论",
            },
            {
                "title": "题目 B",
                "top_kaodian_text": "马克思主义基本原理概论",
                "p_kaodian_text": "第二章 唯物辩证法",
            },
            {
                "title": "题目 C",
                "top_kaodian_text": "马克思主义基本原理概论",
                "p_kaodian_text": "第一章 辩证唯物论",
            },
        ]

        catalog = converter.build_category_catalog(records, default_category_id=99)
        chapter_map = converter.build_chapter_category_id_map(catalog)

        self.assertEqual(1001, chapter_map[(1, "导论")])
        self.assertEqual(1002, chapter_map[(1, "第一章 辩证唯物论")])
        self.assertEqual(1003, chapter_map[(1, "第二章 唯物辩证法")])

    def test_convert_record_uses_chapter_category_mapping(self) -> None:
        record = {
            "title": "章节题目",
            "type": "单选",
            "xuan_text": "A.选项一|B.选项二|C.选项三|D.选项四",
            "right_text": "B",
            "top_kaodian_text": "中国近现代史纲要",
            "p_kaodian_text": "第一章 反对外国侵略的斗争",
        }
        catalog = converter.build_category_catalog([record], default_category_id=99)
        chapter_map = converter.build_chapter_category_id_map(catalog)

        row = converter.convert_record(
            record=record,
            mapping={},
            default_category_id=99,
            default_source="fallback",
            default_source_type="真题",
            default_difficulty=2,
            default_status=1,
            category_mode="chapter",
            chapter_category_id_map=chapter_map,
        )

        self.assertIsNotNone(row)
        assert row is not None
        self.assertEqual("3001", row[13])

    def test_render_category_seed_sql_contains_upsert_rows(self) -> None:
        catalog = {
            4: [
                {
                    "id": 4001,
                    "name": "第一章 人生的青春之问",
                    "description": "Imported from kyzz dataset",
                    "sort": 1,
                    "parent_id": 4,
                    "practice_mode": 2,
                    "status": 1,
                }
            ]
        }

        sql = converter.render_category_seed_sql(catalog)

        self.assertIn("INSERT INTO `category`", sql)
        self.assertIn("(4001, '第一章 人生的青春之问'", sql)
        self.assertIn("ON DUPLICATE KEY UPDATE", sql)

    def test_normalize_source_type_marks_xiao_xiurong_1000_as_mock(self) -> None:
        source_type = converter.normalize_source_type(None, "22版《肖秀荣1000题》", "")

        self.assertEqual("模拟题", source_type)


if __name__ == "__main__":
    unittest.main()
