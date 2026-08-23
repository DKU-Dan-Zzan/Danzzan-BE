-- 가을 축제 영어 지원: 영문 컬럼 추가
-- 적용: mysql -u <user> -p <database> < scripts/add_translation_columns.sql

ALTER TABLE notice
    ADD COLUMN title_en VARCHAR(255) NULL,
    ADD COLUMN content_en TEXT NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE booth
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN description_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pub
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN intro_en VARCHAR(255) NULL,
    ADD COLUMN description_en TEXT NULL,
    ADD COLUMN department_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE college
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE artist
    ADD COLUMN name_en VARCHAR(255) NULL,
    ADD COLUMN description_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE performance
    ADD COLUMN stage_en VARCHAR(255) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;
