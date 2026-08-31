-- 가을 축제 영어 지원: 영문 컬럼 추가
-- 적용: mysql -u <user> -p <database> < scripts/add_translation_columns.sql
--
-- 자유 서술 필드의 영문 컬럼은 TEXT 로 둔다. 한국어를 영어로 옮기면 글자 수가
-- 대체로 1.5~2배 늘어나므로, 원본과 같은 VARCHAR(255) 로 잡으면 넘친다.
-- 실제로 booth.description_en 이 255 로 되어 있어 저장이 반복 실패했고,
-- 그때마다 회차 전체가 롤백되며 DeepL 무료 쿼터 100만 자가 소진됐다.
-- 이름·소속처럼 짧은 값은 VARCHAR 를 유지하되 여유를 둔다.

ALTER TABLE notice
    ADD COLUMN title_en VARCHAR(512) NULL,
    ADD COLUMN content_en TEXT NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE booth
    ADD COLUMN name_en VARCHAR(512) NULL,
    ADD COLUMN description_en TEXT NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE pub
    ADD COLUMN name_en VARCHAR(512) NULL,
    ADD COLUMN intro_en VARCHAR(1000) NULL,
    ADD COLUMN description_en TEXT NULL,
    ADD COLUMN department_en VARCHAR(512) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE college
    ADD COLUMN name_en VARCHAR(512) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE artist
    ADD COLUMN name_en VARCHAR(512) NULL,
    ADD COLUMN description_en TEXT NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE performance
    ADD COLUMN stage_en VARCHAR(512) NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE emergency_notice
    ADD COLUMN message_en TEXT NULL,
    ADD COLUMN en_is_manual BOOLEAN NOT NULL DEFAULT FALSE;
