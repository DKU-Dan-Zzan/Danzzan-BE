-- MySQL 5.7+/8.x 호환: advertisement 레거시 컬럼 정리(2차) 스크립트
SET @schema_name := DATABASE();

-- 존재하는 레거시 컬럼만 골라 DROP
SET @drop_clauses := (
    SELECT GROUP_CONCAT(
               CONCAT('DROP COLUMN `', COLUMN_NAME, '`')
               ORDER BY FIELD(COLUMN_NAME, 'start_date', 'end_date', 'priority', 'object_position', 'redirection_url')
               SEPARATOR ', '
           )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @schema_name
      AND TABLE_NAME = 'advertisement'
      AND COLUMN_NAME IN ('start_date', 'end_date', 'priority', 'object_position', 'redirection_url')
);

SET @drop_sql := IF(
    @drop_clauses IS NULL,
    'SELECT ''no legacy columns to drop''',
    CONCAT('ALTER TABLE `advertisement` ', @drop_clauses)
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
