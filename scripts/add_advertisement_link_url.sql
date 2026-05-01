-- MySQL 5.7+/8.x 호환: advertisement.link_url 1차 반영 스크립트
SET @schema_name := DATABASE();

SET @add_link_url_sql := (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'advertisement'
              AND COLUMN_NAME = 'link_url'
        ),
        'SELECT ''link_url already exists''',
        'ALTER TABLE `advertisement` ADD COLUMN `link_url` VARCHAR(2048) NULL COMMENT ''광고 클릭 시 외부 이동 URL'' AFTER `image_url`'
    )
);

PREPARE stmt FROM @add_link_url_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
