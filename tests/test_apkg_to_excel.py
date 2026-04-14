import unittest

import apkg_to_excel as converter


class ApkgToExcelTests(unittest.TestCase):
    def test_extract_topic_and_chapter(self) -> None:
        topic, chapter = converter.extract_topic_and_chapter(
            "22版《肖秀荣1000题》::02毛中特::第10章“05位01体”总体布局::多选"
        )

        self.assertEqual("毛泽东思想和中国特色社会主义", topic)
        self.assertEqual("第10章“05位01体”总体布局", chapter)

    def test_normalize_apkg_chapter_name_formats_chapter_consistently(self) -> None:
        normalized = converter.normalize_apkg_chapter_name("第06章“03个代表”重要思想")

        self.assertEqual("第六章 “三个代表”重要思想", normalized)

    def test_parse_decrypted_note_builds_mock_record(self) -> None:
        note = {
            "note_id": 1,
            "deck_name": "22版《肖秀荣1000题》::01马原::第02章世界的物质性及发展规律::单选",
            "question": "1.测试题干",
            "options": ["选项一", "选项二", "选项三", "选项四"],
            "answer": "B",
            "analysis": "【简析】解析内容",
            "bodyText": "",
            "errorText": "",
        }

        record = converter.parse_decrypted_note(
            note=note,
            source="22版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("测试题干", record["title"])
        self.assertEqual("单选", record["type"])
        self.assertEqual("A.选项一|B.选项二|C.选项三|D.选项四", record["xuan_text"])
        self.assertEqual("B", record["right_text"])
        self.assertEqual("解析内容", record["jiexi"])
        self.assertEqual("模拟题", record["sourceType"])
        self.assertEqual("马克思主义基本原理概论", record["top_kaodian_text"])
        self.assertEqual("第二章 世界的物质性及发展规律", record["p_kaodian_text"])

    def test_parse_decrypted_note_falls_back_to_inline_options(self) -> None:
        note = {
            "note_id": 2,
            "deck_name": "22版《肖秀荣1000题》::01马原::第06章资本主义的发展及其趋势::单选",
            "question": "187.随着资本输出不断增加，当代国际垄断同盟的主要形式是A.国家垄断资本主义的国际联盟|B.国际卡特尔|C.国际托拉斯|D.国际康采恩",
            "options": [],
            "answer": "A",
            "analysis": "【简析】解析",
            "bodyText": "同上",
            "errorText": "",
        }

        record = converter.parse_decrypted_note(
            note=note,
            source="22版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("随着资本输出不断增加，当代国际垄断同盟的主要形式是", record["title"])
        self.assertEqual("A.国家垄断资本主义的国际联盟|B.国际卡特尔|C.国际托拉斯|D.国际康采恩", record["xuan_text"])

    def test_parse_decrypted_note_uses_canonical_existing_category_when_configured(self) -> None:
        note = {
            "note_id": 3,
            "deck_name": "22版《肖秀荣1000题》::02毛中特::第06章“03个代表”重要思想::多选",
            "question": "1.测试题干",
            "options": ["甲", "乙", "丙", "丁"],
            "answer": "AC",
            "analysis": "【简析】解析内容",
            "bodyText": "",
            "errorText": "",
        }

        record = converter.parse_decrypted_note(
            note=note,
            source="22版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("第六章 “三个代表”重要思想", record["p_kaodian_text"])
        self.assertEqual(2010, record["__resolved_category_id__"])

    def test_build_namespaced_category_catalog_avoids_default_chapter_id_range(self) -> None:
        records = [
            {
                "title": "题目",
                "top_kaodian_text": "马克思主义基本原理概论",
                "p_kaodian_text": "第02章世界的物质性及发展规律",
            }
        ]

        catalog = converter.build_namespaced_category_catalog(records, default_category_id=99, namespace="22版《肖秀荣1000题》")
        chapter = catalog[1][0]

        self.assertGreater(chapter["id"], 1000000)
        self.assertIn("22版《肖秀荣1000题》", chapter["description"])


if __name__ == "__main__":
    unittest.main()
