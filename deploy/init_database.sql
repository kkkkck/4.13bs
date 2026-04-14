CREATE DATABASE IF NOT EXISTS `question_bank`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `question_bank`;

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `email` VARCHAR(100) NOT NULL UNIQUE,
  `password` VARCHAR(255) NOT NULL,
  `nickname` VARCHAR(50) NOT NULL,
  `role` TINYINT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

UPDATE `user`
SET `nickname` = CONCAT('user_', `id`)
WHERE `nickname` IS NULL OR `nickname` = '';

UPDATE `user` u
JOIN (
  SELECT id, CONCAT(LEFT(nickname, 40), '_', id) AS new_nickname
  FROM (
    SELECT id, nickname, ROW_NUMBER() OVER (PARTITION BY nickname ORDER BY id) AS rn
    FROM `user`
    WHERE nickname IS NOT NULL AND nickname <> ''
  ) ranked
  WHERE ranked.rn > 1
) dup ON u.id = dup.id
SET u.nickname = dup.new_nickname;

SET @has_nickname_unique := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND INDEX_NAME = 'uk_user_nickname'
);
SET @user_nickname_index_sql := IF(
  @has_nickname_unique = 0,
  'ALTER TABLE `user` ADD UNIQUE KEY `uk_user_nickname` (`nickname`)',
  'SELECT 1'
);
PREPARE stmt_user_nickname_index FROM @user_nickname_index_sql;
EXECUTE stmt_user_nickname_index;
DEALLOCATE PREPARE stmt_user_nickname_index;

SET @nickname_nullable := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'user'
    AND COLUMN_NAME = 'nickname'
    AND IS_NULLABLE = 'YES'
);
SET @user_nickname_not_null_sql := IF(
  @nickname_nullable = 1,
  'ALTER TABLE `user` MODIFY COLUMN `nickname` VARCHAR(50) NOT NULL',
  'SELECT 1'
);
PREPARE stmt_user_nickname_not_null FROM @user_nickname_not_null_sql;
EXECUTE stmt_user_nickname_not_null;
DEALLOCATE PREPARE stmt_user_nickname_not_null;

CREATE TABLE IF NOT EXISTS `category` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `name` VARCHAR(50) NOT NULL,
  `description` VARCHAR(255),
  `sort` INT DEFAULT 1,
  `parent_id` BIGINT NOT NULL DEFAULT 0,
  `practice_mode` TINYINT NOT NULL DEFAULT 1,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @has_practice_mode := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'category'
    AND COLUMN_NAME = 'practice_mode'
);
SET @category_alter_sql := IF(
  @has_practice_mode = 0,
  'ALTER TABLE `category` ADD COLUMN `practice_mode` TINYINT NOT NULL DEFAULT 1 AFTER `sort`',
  'SELECT 1'
);
PREPARE stmt_category FROM @category_alter_sql;
EXECUTE stmt_category;
DEALLOCATE PREPARE stmt_category;

SET @has_parent_id := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'category'
    AND COLUMN_NAME = 'parent_id'
);
SET @category_parent_alter_sql := IF(
  @has_parent_id = 0,
  'ALTER TABLE `category` ADD COLUMN `parent_id` BIGINT NOT NULL DEFAULT 0 AFTER `sort`',
  'SELECT 1'
);
PREPARE stmt_category_parent FROM @category_parent_alter_sql;
EXECUTE stmt_category_parent;
DEALLOCATE PREPARE stmt_category_parent;

CREATE TABLE IF NOT EXISTS `question` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `content` TEXT NOT NULL,
  `type` TINYINT NOT NULL DEFAULT 1,
  `difficulty` TINYINT NOT NULL DEFAULT 1,
  `tags` VARCHAR(255),
  `source` VARCHAR(100),
  `source_type` TINYINT NOT NULL DEFAULT 1,
  `option_a` TEXT,
  `option_b` TEXT,
  `option_c` TEXT,
  `option_d` TEXT,
  `correct_answer` TEXT NOT NULL,
  `analysis` TEXT,
  `solution_strategy` TEXT,
  `category_id` BIGINT NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_question_category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @has_source_type := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'question'
    AND COLUMN_NAME = 'source_type'
);
SET @question_source_type_alter_sql := IF(
  @has_source_type = 0,
  'ALTER TABLE `question` ADD COLUMN `source_type` TINYINT NOT NULL DEFAULT 1 AFTER `source`',
  'SELECT 1'
);
PREPARE stmt_question_source_type FROM @question_source_type_alter_sql;
EXECUTE stmt_question_source_type;
DEALLOCATE PREPARE stmt_question_source_type;

UPDATE `question`
SET `source_type` = CASE
  WHEN LOWER(COALESCE(`source`, '')) LIKE '%mock%'
    OR COALESCE(`source`, '') LIKE '%模拟%'
    OR COALESCE(`source`, '') LIKE '%1000题%'
    OR COALESCE(`source`, '') LIKE '%肖秀荣%'
  THEN 2
  ELSE 1
END
WHERE `source_type` IS NULL
   OR `source_type` NOT IN (1, 2);

CREATE TABLE IF NOT EXISTS `wrong_question` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `user_answer` TEXT,
  `wrong_count` INT NOT NULL DEFAULT 1,
  `last_wrong_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_wrong_user_question` (`user_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `favorite` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `question_id` BIGINT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_favorite_user_question` (`user_id`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `practice_record` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `category_id` BIGINT NOT NULL,
  `total_questions` INT NOT NULL,
  `correct_count` INT NOT NULL,
  `duration` INT NOT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_activity_session` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `session_id` VARCHAR(64) NOT NULL,
  `last_path` VARCHAR(255) NOT NULL,
  `total_duration_seconds` INT NOT NULL DEFAULT 0,
  `last_seen_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX `idx_activity_user` (`user_id`),
  INDEX `idx_activity_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `category` (`id`, `name`, `description`, `sort`, `parent_id`, `practice_mode`, `status`) VALUES
  (1, '马克思主义基本原理', '约 24% · 理论基础模块，客观题高频主阵地', 1, 0, 1, 1),
  (2, '毛泽东思想和中国特色社会主义理论体系概论', '约 30% · 占比最高的核心专题', 2, 0, 1, 1),
  (3, '中国近现代史纲要', '约 14% · 以历史主线和关键节点为主', 3, 0, 2, 1),
  (4, '思想道德与法治', '约 16% · 价值观、法治与伦理判断模块', 4, 0, 1, 1),
  (5, '形势与政策以及当代世界经济与政治', '约 16% · 时政热点与国际视野模块', 5, 0, 2, 1),
  (101, '历史的选择与人民的抉择', '近现代史样例章节', 1, 3, 2, 1),
  (102, '时政热点与政策方法', '时政样例章节', 1, 5, 2, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `description` = VALUES(`description`),
  `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`),
  `practice_mode` = VALUES(`practice_mode`),
  `status` = VALUES(`status`);

INSERT INTO `question` (`id`, `content`, `type`, `difficulty`, `tags`, `source`, `source_type`, `option_a`, `option_b`, `option_c`, `option_d`, `correct_answer`, `analysis`, `solution_strategy`, `category_id`, `status`) VALUES
  (1, 'The direct motive force for the development of things is:', 1, 1, 'dialectics,contradiction', 'Political Theory Mock 2026', 2, 'The absolute identity of contradiction', 'The struggle and identity between contradictory aspects', 'External pressure from other things', 'Linear accumulation of quantity only', 'B', 'The development of things comes from the contradictory movement inside the thing itself. Identity and struggle jointly work, but external causes only work through internal causes.', 'Use the principle of internal cause versus external cause. Remove the options that only stress external pressure or one-sided quantity accumulation.', 1, 1),
  (6, 'Which options reflect the basic characteristics of materialist dialectics?', 5, 2, 'dialectics,multiple-choice', 'Political Theory Mock 2026', 2, 'Universal connection', 'Absolute stillness', 'Contradictory movement', 'Isolated one-sided thinking', 'A,C', 'Materialist dialectics emphasizes universal connection and development through contradiction, so A and C are correct.', 'Eliminate options that deny connection or movement, then combine the remaining correct options.', 1, 1),
  (2, 'Which statement best reflects the essence of socialism with Chinese characteristics entering a new era?', 1, 2, 'new era,socialism', 'Political Theory Sprint Set', 2, 'China has solved all development problems', 'The principal contradiction in society has changed and national rejuvenation shows a brighter prospect', 'Class struggle is the only central task', 'Market competition replaces political leadership', 'B', 'The new era is marked by the change in the principal contradiction and by the historical orientation of national rejuvenation.', 'Recall the official textbook formulation and eliminate absolute or one-dimensional distractors.', 2, 1),
  (7, 'Which statements are consistent with the strategic arrangement of Chinese modernization?', 5, 2, 'modernization,multiple-choice', 'Political Theory Sprint Set', 2, 'Pursue high-quality development', 'Completely copy another country''s model', 'Advance common prosperity in a solid way', 'Replace long-term planning with short-term improvisation', 'A,C', 'Chinese modernization stresses high-quality development and common prosperity, rather than copying foreign models or abandoning planning.', 'For multiple-choice questions, first match the options against the standard textbook wording.', 2, 1),
  (3, 'Why did history and the people choose the Communist Party of China?', 1, 2, 'history,CPC', 'History Review Volume', 2, 'Because of temporary military luck', 'Because it represented the direction of advanced productive forces and the fundamental interests of the overwhelming majority', 'Because old democratic forces had no shortcomings', 'Because colonial powers supported it', 'B', 'The historical choice was rooted in political direction, mass foundation, and practical achievements, not accidental or external endorsement.', 'First exclude options that contradict basic historical facts, then match the remaining option with the standard textbook conclusion.', 101, 1),
  (8, 'Which factors explain why the old democratic road could not save China?', 5, 2, 'history,multiple-choice', 'History Review Volume', 2, 'It failed to solve the question of leadership and mass mobilization', 'It had already completed the task of national independence', 'It could not fundamentally change the semi-colonial and semi-feudal social structure', 'It fully represented the long-term interests of the overwhelming majority of the people', 'A,C', 'The old democratic road failed because it could not solve the leadership problem or transform the old social structure.', 'History multiple-choice questions usually hinge on the actual causes of success or failure.', 101, 1),
  (4, 'In ideological and moral cultivation, the core requirement for citizens in the new era is to:', 1, 1, 'values,citizenship', 'Ideology Practice Paper', 2, 'Pursue only personal success', 'Separate morality from law completely', 'Consciously practice the core socialist values', 'Reject all forms of social cooperation', 'C', 'The course emphasizes integrating personal development with social responsibility through practicing the core socialist values.', 'Look for the normative textbook expression rather than the distractors built from extreme personal or anti-social positions.', 4, 1),
  (5, 'When answering a current affairs question, the most reliable first step is to:', 1, 1, 'current affairs,method', 'Current Affairs Weekly', 2, 'Rely only on fragmented social media impressions', 'Ignore official policy documents', 'Anchor the issue to authoritative policy statements and the broader theoretical framework', 'Memorize isolated facts without context', 'C', 'Current affairs questions usually require both factual awareness and theoretical framing, so authoritative documents are the best anchor.', 'For current affairs, use a two-step method: verify the official statement first, then map the issue to the theory module behind it.', 102, 1)
ON DUPLICATE KEY UPDATE
  `content` = VALUES(`content`),
  `type` = VALUES(`type`),
  `difficulty` = VALUES(`difficulty`),
  `tags` = VALUES(`tags`),
  `source` = VALUES(`source`),
  `source_type` = VALUES(`source_type`),
  `option_a` = VALUES(`option_a`),
  `option_b` = VALUES(`option_b`),
  `option_c` = VALUES(`option_c`),
  `option_d` = VALUES(`option_d`),
  `correct_answer` = VALUES(`correct_answer`),
  `analysis` = VALUES(`analysis`),
  `solution_strategy` = VALUES(`solution_strategy`),
  `category_id` = VALUES(`category_id`),
  `status` = VALUES(`status`);

INSERT INTO `user` (`id`, `email`, `password`, `nickname`, `role`, `status`) VALUES
  (1, 'admin@example.com', '$2a$10$/IHWwdQ3awJDZEYTA56qAetfb1/TtDHsM5tGfR5OU4unp9NxpCepy', 'Admin', 1, 1)
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `nickname` = VALUES(`nickname`),
  `role` = VALUES(`role`),
  `status` = VALUES(`status`);
