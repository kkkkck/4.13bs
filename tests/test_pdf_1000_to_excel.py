import unittest

import pdf_1000_to_excel as converter


class Pdf1000ToExcelTests(unittest.TestCase):
    def test_build_records_maps_marxism_legacy_chapter_to_canonical_topic(self) -> None:
        questions = [
            converter.ParsedQuestion(
                section="marxism",
                type_value=1,
                number=1,
                chapter="第二章 世界的物质性及发展规律",
                stem="测试题干",
                options=["甲", "乙", "丙", "丁"],
            )
        ]
        analysis_map = {
            ("marxism", 1, 1): converter.ParsedAnalysis(
                section="marxism",
                type_value=1,
                number=1,
                chapter="第二章 世界的物质性及发展规律",
                answer="B",
                analysis="解析",
            )
        }

        records = converter.build_records(
            questions=questions,
            analysis_map=analysis_map,
            source="26版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual(1, len(records))
        self.assertEqual("专题二：辩证唯物主义世界观", records[0]["p_kaodian_text"])
        self.assertEqual(1003, records[0]["categoryId"])

    def test_build_records_uses_section_hint_for_split_theory_intro(self) -> None:
        questions = [
            converter.ParsedQuestion(
                section="maozhongte",
                type_value=1,
                number=1,
                chapter="导论",
                stem="毛概导论题",
                options=["甲", "乙", "丙", "丁"],
            ),
            converter.ParsedQuestion(
                section="xijinping",
                type_value=1,
                number=1,
                chapter="导论",
                stem="习思导论题",
                options=["甲", "乙", "丙", "丁"],
            ),
        ]
        analysis_map = {
            ("maozhongte", 1, 1): converter.ParsedAnalysis(
                section="maozhongte",
                type_value=1,
                number=1,
                chapter="导论",
                answer="A",
                analysis="解析",
            ),
            ("xijinping", 1, 1): converter.ParsedAnalysis(
                section="xijinping",
                type_value=1,
                number=1,
                chapter="导论",
                answer="B",
                analysis="解析",
            ),
        }

        records = converter.build_records(
            questions=questions,
            analysis_map=analysis_map,
            source="26版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("毛概：导论", records[0]["p_kaodian_text"])
        self.assertEqual(2001, records[0]["categoryId"])
        self.assertEqual("习思：导论", records[1]["p_kaodian_text"])
        self.assertEqual(2101, records[1]["categoryId"])

    def test_build_records_merges_history_legacy_chapters(self) -> None:
        questions = [
            converter.ParsedQuestion(
                section="history",
                type_value=5,
                number=9,
                chapter="第九章 社会主义建设在探索中曲折发展",
                stem="史纲题目",
                options=["甲", "乙", "丙", "丁"],
            )
        ]
        analysis_map = {
            ("history", 5, 9): converter.ParsedAnalysis(
                section="history",
                type_value=5,
                number=9,
                chapter="第九章 社会主义建设在探索中曲折发展",
                answer="AC",
                analysis="解析",
            )
        }

        records = converter.build_records(
            questions=questions,
            analysis_map=analysis_map,
            source="26版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("第八章 中华人民共和国的成立与中国社会主义建设道路的探索", records[0]["p_kaodian_text"])
        self.assertEqual(3009, records[0]["categoryId"])

    def test_build_records_repairs_number_only_maozhongte_chapter(self) -> None:
        questions = [
            converter.ParsedQuestion(
                section="maozhongte",
                type_value=1,
                number=7,
                chapter="第七章",
                stem="科学发展观的基本要求是",
                options=["甲", "乙", "丙", "丁"],
            )
        ]
        analysis_map = {
            ("maozhongte", 1, 7): converter.ParsedAnalysis(
                section="maozhongte",
                type_value=1,
                number=7,
                chapter="第七章",
                answer="A",
                analysis="科学发展观的基本要求是全面协调可持续。",
            )
        }

        records = converter.build_records(
            questions=questions,
            analysis_map=analysis_map,
            source="26版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("第八章 科学发展观", records[0]["p_kaodian_text"])
        self.assertEqual(2009, records[0]["categoryId"])

    def test_build_records_repairs_ethics_ocr_garble(self) -> None:
        questions = [
            converter.ParsedQuestion(
                section="ethics",
                type_value=1,
                number=1,
                chapter="i",
                stem="社会主义核心价值观的根本特性是",
                options=["甲", "乙", "丙", "丁"],
            )
        ]
        analysis_map = {
            ("ethics", 1, 1): converter.ParsedAnalysis(
                section="ethics",
                type_value=1,
                number=1,
                chapter="i",
                answer="A",
                analysis="人民性是社会主义核心价值观的根本特性。",
            )
        }

        records = converter.build_records(
            questions=questions,
            analysis_map=analysis_map,
            source="26版《肖秀荣1000题》",
            source_type="模拟题",
            fallback_category_id=99,
        )

        self.assertEqual("第四章 明确价值要求 践行价值准则", records[0]["p_kaodian_text"])
        self.assertEqual(4004, records[0]["categoryId"])


if __name__ == "__main__":
    unittest.main()
