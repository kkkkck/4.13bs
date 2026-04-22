SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO `category` (`id`, `name`, `description`, `sort`, `parent_id`, `practice_mode`, `status`) VALUES
  (1, '马克思主义基本原理', '约 22% · 理论基础模块，客观题高频主阵地', 1, 0, 1, 1),
  (2, '毛泽东思想和中国特色社会主义理论体系概论', '约 13% · 毛中特章节', 2, 0, 1, 1),
  (6, '习近平新时代中国特色社会主义思想概论', '约 22% · 新思想章节', 3, 0, 1, 1),
  (3, '中国近现代史纲要', '约 15% · 以历史主线和关键节点为主', 4, 0, 2, 1),
  (4, '思想道德与法治', '约 15% · 价值观、法治与伦理判断模块', 5, 0, 1, 1),
  (5, '形势与政策以及当代世界经济与政治', '约 13% · 时政热点与国际视野模块', 6, 0, 2, 1),
  (2001, '导论', '毛中特章节', 1, 2, 2, 1),
  (2002, '第一章 毛泽东思想及其历史地位', '毛中特章节', 2, 2, 2, 1),
  (2003, '第二章 新民主主义革命理论', '毛中特章节', 3, 2, 2, 1),
  (2004, '第三章 社会主义改造理论', '毛中特章节', 4, 2, 2, 1),
  (2005, '第四章 社会主义建设道路初步探索的理论成果', '毛中特章节', 5, 2, 2, 1),
  (2006, '第五章 中国特色社会主义理论体系的形成发展', '毛中特章节', 6, 2, 2, 1),
  (2007, '第六章 邓小平理论', '毛中特章节', 7, 2, 2, 1),
  (2008, '第七章 “三个代表”重要思想', '毛中特章节', 8, 2, 2, 1),
  (2009, '第八章 科学发展观', '毛中特章节', 9, 2, 2, 1),
  (2101, '导论', '新思想导论，不作为章节入口', 0, 6, 2, 0),
  (2102, '第一章 新时代坚持和发展中国特色社会主义', '新思想章节', 1, 6, 2, 1),
  (2103, '第二章 以中国式现代化全面推进中华民族伟大复兴', '新思想章节', 2, 6, 2, 1),
  (2104, '第三章 坚持党的全面领导', '新思想章节', 3, 6, 2, 1),
  (2105, '第四章 坚持以人民为中心', '新思想章节', 4, 6, 2, 1),
  (2106, '第五章 全面深化改革开放', '新思想章节', 5, 6, 2, 1),
  (2107, '第六章 推动高质量发展', '新思想章节', 6, 6, 2, 1),
  (2108, '第七章 社会主义现代化建设的教育、科技、人才战略', '新思想章节', 7, 6, 2, 1),
  (2109, '第八章 发展全过程人民民主', '新思想章节', 8, 6, 2, 1),
  (2110, '第九章 全面依法治国', '新思想章节', 9, 6, 2, 1),
  (2111, '第十章 建设社会主义文化强国', '新思想章节', 10, 6, 2, 1),
  (2112, '第十一章 以保障和改善民生为重点加强社会建设', '新思想章节', 11, 6, 2, 1),
  (2113, '第十二章 建设社会主义生态文明', '新思想章节', 12, 6, 2, 1),
  (2114, '第十三章 维护和塑造国家安全', '新思想章节', 13, 6, 2, 1),
  (2115, '第十四章 建设巩固国防和强大人民军队', '新思想章节', 14, 6, 2, 1),
  (2116, '第十五章 坚持“一国两制”和推进祖国完全统一', '新思想章节', 15, 6, 2, 1),
  (2117, '第十六章 中国特色大国外交和推动构建人类命运共同体', '新思想章节', 16, 6, 2, 1),
  (2118, '第十七章 全面从严治党', '新思想章节', 17, 6, 2, 1),
  (3001, '导言', '史纲导言，不作为章节入口', 0, 3, 2, 0),
  (3002, '第一章 进入近代后中华民族的磨难与抗争', '史纲章节', 1, 3, 2, 1),
  (3003, '第二章 不同社会力量对国家出路的早期探索', '史纲章节', 2, 3, 2, 1),
  (3004, '第三章 辛亥革命与君主专制制度的终结', '史纲章节', 3, 3, 2, 1),
  (3005, '第四章 中国共产党成立和中国革命新局面', '史纲章节', 4, 3, 2, 1),
  (3006, '第五章 中国革命的新道路', '史纲章节', 5, 3, 2, 1),
  (3007, '第六章 中华民族的抗日战争', '史纲章节', 6, 3, 2, 1),
  (3008, '第七章 为建立新中国而奋斗', '史纲章节', 7, 3, 2, 1),
  (3009, '第八章 中华人民共和国的成立与中国社会主义建设道路的探索', '史纲章节', 8, 3, 2, 1),
  (3010, '第九章 改革开放与中国特色社会主义的开创和发展', '史纲章节', 9, 3, 2, 1),
  (3011, '第十章 中国特色社会主义进入新时代', '史纲章节', 10, 3, 2, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`),
  `practice_mode` = VALUES(`practice_mode`),
  `status` = VALUES(`status`);

UPDATE `question` SET `category_id` = 2102 WHERE `category_id` = 2002;
UPDATE `question` SET `category_id` = 2103 WHERE `category_id` = 2004;
UPDATE `question` SET `category_id` = 2106 WHERE `category_id` = 2008;
UPDATE `question` SET `category_id` = 2107 WHERE `category_id` = 2011;
UPDATE `question` SET `category_id` = 2108 WHERE `category_id` = 2012;
UPDATE `question` SET `category_id` = 2109 WHERE `category_id` = 2014;
UPDATE `question` SET `category_id` = 2111 WHERE `category_id` = 2015;
UPDATE `question` SET `category_id` = 2112 WHERE `category_id` = 2016;
UPDATE `question` SET `category_id` = 2113 WHERE `category_id` = 2017;
UPDATE `question` SET `category_id` = 2114 WHERE `category_id` = 2018;
UPDATE `question` SET `category_id` = 2116 WHERE `category_id` = 2019;
UPDATE `question` SET `category_id` = 2117 WHERE `category_id` = 2020;
UPDATE `question` SET `category_id` = 2118 WHERE `category_id` = 2021;

UPDATE `question` SET `category_id` = 2002 WHERE `category_id` = 2003;
UPDATE `question` SET `category_id` = 2003 WHERE `category_id` = 2005;
UPDATE `question` SET `category_id` = 2004 WHERE `category_id` = 2006;
UPDATE `question` SET `category_id` = 2005 WHERE `category_id` = 2007;
UPDATE `question` SET `category_id` = 2007 WHERE `category_id` = 2009;
UPDATE `question` SET `category_id` = 2008 WHERE `category_id` = 2010;
UPDATE `question` SET `category_id` = 2009 WHERE `category_id` = 2013;

UPDATE `question` SET `category_id` = 2102 WHERE `category_id` IN (2101, 2133001);
UPDATE `question` SET `category_id` = 2103 WHERE `category_id` = 2133002;
UPDATE `question` SET `category_id` = 6 WHERE `category_id` IN (2133003, 2133004);
UPDATE `question` SET `category_id` = 2115 WHERE `category_id` = 2133005;
UPDATE `question` SET `category_id` = 2104 WHERE `category_id` = 2133006;

UPDATE `question` SET `category_id` = 3 WHERE `category_id` = 101;
UPDATE `question` SET `category_id` = 3002 WHERE `category_id` = 3001;
UPDATE `question` SET `category_id` = 3009 WHERE `category_id` IN (3133001, 3133002);
UPDATE `question` SET `category_id` = 3011 WHERE `category_id` = 3133003;

UPDATE `category`
SET `parent_id` = 6, `practice_mode` = 2, `status` = 0, `sort` = 100 + (`id` - 2133000)
WHERE `id` BETWEEN 2133001 AND 2133006;

UPDATE `category`
SET `parent_id` = 6, `practice_mode` = 2, `status` = 0
WHERE `id` IN (2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021);

UPDATE `category`
SET `parent_id` = 3, `practice_mode` = 2, `status` = 0
WHERE `id` IN (101, 3001, 3133001, 3133002, 3133003);

COMMIT;
