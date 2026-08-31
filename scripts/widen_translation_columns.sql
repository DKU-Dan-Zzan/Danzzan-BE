-- 이미 add_translation_columns.sql 을 적용한 DB 를 위한 후속 마이그레이션
-- 적용: mysql -u <user> -p <database> < scripts/widen_translation_columns.sql
--
-- 배경
--   영문 컬럼을 한국어 원본과 같은 VARCHAR(255) 로 잡았는데, 한국어를 영어로
--   옮기면 글자 수가 대체로 1.5~2배 늘어난다. booth.description_en 이 먼저
--   한계를 넘겨 "Data truncation: Data too long for column 'description_en'" 이
--   났고, 백필 전체가 하나의 트랜잭션이었던 탓에 그 회차에 번역한 115개 필드가
--   통째로 롤백됐다. 5분 뒤 같은 행을 다시 번역하는 일이 136회 반복되며
--   DeepL 무료 쿼터 100만 자가 소진됐다.
--
--   트랜잭션 경계는 TranslationBackfillWriter 로 분리해 행 단위로 끊었다.
--   이 스크립트는 나머지 절반, 즉 길이 자체를 바로잡는다.
--
-- 안전성
--   컬럼을 넓히는 방향이라 기존 값은 그대로 보존된다. 대상 테이블이 작아
--   (부스·주점·아티스트 각 수십 행) 테이블 복사도 순식간에 끝난다.
--   애플리케이션을 멈출 필요는 없다.

ALTER TABLE booth  MODIFY COLUMN description_en TEXT NULL;
ALTER TABLE artist MODIFY COLUMN description_en TEXT NULL;
ALTER TABLE pub    MODIFY COLUMN intro_en VARCHAR(1000) NULL;

ALTER TABLE emergency_notice MODIFY COLUMN message_en TEXT NULL;

-- 이름·소속처럼 짧은 값도 여유를 준다. 긴 부스명이나 학과명이 들어오면
-- 같은 방식으로 터질 수 있다.
ALTER TABLE notice      MODIFY COLUMN title_en VARCHAR(512) NULL;
ALTER TABLE booth       MODIFY COLUMN name_en VARCHAR(512) NULL;
ALTER TABLE pub         MODIFY COLUMN name_en VARCHAR(512) NULL;
ALTER TABLE pub         MODIFY COLUMN department_en VARCHAR(512) NULL;
ALTER TABLE college     MODIFY COLUMN name_en VARCHAR(512) NULL;
ALTER TABLE artist      MODIFY COLUMN name_en VARCHAR(512) NULL;
ALTER TABLE performance MODIFY COLUMN stage_en VARCHAR(512) NULL;

-- 확인
-- SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME LIKE '%\_en'
-- ORDER BY TABLE_NAME, COLUMN_NAME;
