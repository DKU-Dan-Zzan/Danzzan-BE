-- 가을 축제(2026 DANFESTA "LEGEND") 로컬 목업 데이터
--
-- 로컬 도커 MySQL을 채워 화면을 눈으로 확인하기 위한 것이다. 운영에서 실행하지 말 것.
--
-- 실행:
--   docker exec -i danzzan-mysql mysql --default-character-set=utf8mb4 \
--     -udanzzan -pdanzzan1234 danzzan < scripts/seed_local_fall_festival.sql
--
-- --default-character-set=utf8mb4 를 반드시 붙일 것. 없으면 한국어가 이중 인코딩되어
-- 저장되고, 앱은 깨진 글자를 읽어 DeepL에 보낸다. DeepL은 오류 대신 엉뚱한 영어를
-- 지어내므로 원인을 찾기 매우 어렵다.
--
-- 영문(_en) 컬럼은 일부러 비워둔다. 번역 보정 스케줄러가 5분 주기로 채우는 것을
-- 확인하는 것이 이 시드의 목적 중 하나다.

SET NAMES utf8mb4;

DELETE FROM pub_display_day;
DELETE FROM pub_operation;
DELETE FROM pub_image;
DELETE FROM pub;
DELETE FROM booth_operation;
DELETE FROM booth;
DELETE FROM college;
DELETE FROM performance;
DELETE FROM artist;
DELETE FROM home_image;
DELETE FROM lineup_image;
DELETE FROM notice;

-- 홈 메인 포스터 (nginx가 서빙하는 정적 파일을 가리킨다)
INSERT INTO home_image (created_at, display_order, image_url) VALUES
  (NOW(6), 0, '/legend-poster.jpg');

-- 단과대 (부스맵 마커 좌표는 죽전캠퍼스 근방 값)
INSERT INTO college (created_at, location_x, location_y, name, en_is_manual) VALUES
  (NOW(6), 127.1265, 37.3215, '공과대학',     b'0'),
  (NOW(6), 127.1272, 37.3221, '사회과학대학', b'0'),
  (NOW(6), 127.1258, 37.3209, '경영경제대학', b'0'),
  (NOW(6), 127.1280, 37.3218, '문과대학',     b'0');

-- 주점 (단과대별)
INSERT INTO pub (created_at, department, description, instagram, intro, name, college_id, en_is_manual)
SELECT NOW(6), '소프트웨어학과',
       '전공 살려 만든 야식 메뉴를 준비했습니다. 라면부터 골뱅이무침까지 있습니다.',
       'dku_sw', '밤새 불이 꺼지지 않는 곳', '코드가 술술', c.id, b'0'
FROM college c WHERE c.name = '공과대학';

INSERT INTO pub (created_at, department, description, instagram, intro, name, college_id, en_is_manual)
SELECT NOW(6), '기계공학과',
       '직접 만든 화로에 구워내는 꼬치가 대표 메뉴입니다.',
       'dku_me', '불맛 하나로 승부합니다', '기계실 화덕', c.id, b'0'
FROM college c WHERE c.name = '공과대학';

INSERT INTO pub (created_at, department, description, instagram, intro, name, college_id, en_is_manual)
SELECT NOW(6), '행정학과',
       '조용히 앉아 이야기 나누기 좋은 자리를 많이 두었습니다.',
       'dku_pa', '앉아서 쉬어가세요', '행정타운', c.id, b'0'
FROM college c WHERE c.name = '사회과학대학';

INSERT INTO pub (created_at, department, description, instagram, intro, name, college_id, en_is_manual)
SELECT NOW(6), '경영학과',
       '학과 선배들이 후원한 안주를 원가로 제공합니다.',
       'dku_biz', '가격은 정직하게', '경영포차', c.id, b'0'
FROM college c WHERE c.name = '경영경제대학';

-- 주점 운영 시간 (축제 이틀)
INSERT INTO pub_operation (end_time, operation_date, start_time) VALUES
  ('23:59:59', '2026-09-09', '18:00:00'),
  ('23:59:59', '2026-09-10', '18:00:00');

-- 모든 주점을 이틀 다 노출
INSERT INTO pub_display_day (pub_id, pub_operation_id)
SELECT p.id, o.id FROM pub p CROSS JOIN pub_operation o;

-- 부스
INSERT INTO booth (created_at, description, image_url, location_x, location_y, name, type, en_is_manual) VALUES
  (NOW(6), '학과 점퍼와 굿즈를 판매합니다. 현장 결제만 가능합니다.', NULL, 127.1268, 37.3212, '총학생회 굿즈샵', 'EVENT',      b'0'),
  (NOW(6), '즉석에서 사진을 뽑아 드립니다. 대기 줄이 길 수 있습니다.', NULL, 127.1274, 37.3216, '인생네컷 포토부스', 'EXPERIENCE', b'0'),
  (NOW(6), '직접 만든 팔찌를 가져가실 수 있습니다.',              NULL, 127.1262, 37.3219, '비즈 공방',        'EXPERIENCE', b'0'),
  (NOW(6), '수제버거와 감자튀김을 판매합니다.',                    NULL, 127.1277, 37.3211, '더블패티 푸드트럭', 'FOOD_TRUCK', b'0'),
  (NOW(6), '분실물을 맡아드립니다. 축제 종료 후에는 학생회관으로 옮겨집니다.', NULL, 127.1266, 37.3223, '분실물 센터', 'FACILITY', b'0'),
  (NOW(6), '응급처치와 휴식 공간을 제공합니다.',                    NULL, 127.1259, 37.3214, '의무실',          'FACILITY',   b'0');

-- 부스 운영 시간
INSERT INTO booth_operation (end_time, operation_date, operation_status, start_time, booth_id)
SELECT '21:00:00', d.day, 'OPEN', '11:00:00', b.id
FROM booth b
CROSS JOIN (SELECT '2026-09-09' AS day UNION ALL SELECT '2026-09-10') d;

-- 아티스트
INSERT INTO artist (created_at, description, image_url, name, en_is_manual) VALUES
  (NOW(6), '잔나비는 서정적인 멜로디로 사랑받는 밴드입니다.',     NULL, '잔나비',     b'0'),
  (NOW(6), '청하는 파워풀한 퍼포먼스로 무대를 채웁니다.',         NULL, '청하',       b'0'),
  (NOW(6), '다이나믹 듀오는 국내 힙합의 대표 듀오입니다.',        NULL, '다이나믹 듀오', b'0'),
  (NOW(6), '교내 밴드 동아리 연합 무대입니다.',                  NULL, '단국 밴드연합', b'0'),
  (NOW(6), '교내 댄스 동아리 연합 무대입니다.',                  NULL, '단국 댄스연합', b'0');

-- 타임테이블 (09.09 ~ 09.10, 메인 스테이지)
INSERT INTO performance (created_at, end_time, performance_date, stage, start_time, artist_id, en_is_manual)
SELECT NOW(6), '18:40:00', '2026-09-09', '메인 스테이지', '18:00:00', id, b'0' FROM artist WHERE name = '단국 댄스연합';
INSERT INTO performance (created_at, end_time, performance_date, stage, start_time, artist_id, en_is_manual)
SELECT NOW(6), '19:30:00', '2026-09-09', '메인 스테이지', '18:50:00', id, b'0' FROM artist WHERE name = '단국 밴드연합';
INSERT INTO performance (created_at, end_time, performance_date, stage, start_time, artist_id, en_is_manual)
SELECT NOW(6), '20:40:00', '2026-09-09', '메인 스테이지', '20:00:00', id, b'0' FROM artist WHERE name = '잔나비';
INSERT INTO performance (created_at, end_time, performance_date, stage, start_time, artist_id, en_is_manual)
SELECT NOW(6), '19:40:00', '2026-09-10', '메인 스테이지', '19:00:00', id, b'0' FROM artist WHERE name = '청하';
INSERT INTO performance (created_at, end_time, performance_date, stage, start_time, artist_id, en_is_manual)
SELECT NOW(6), '21:00:00', '2026-09-10', '메인 스테이지', '20:10:00', id, b'0' FROM artist WHERE name = '다이나믹 듀오';

-- 공지
INSERT INTO notice (title, content, author, category, is_pinned, display_order, is_emergency, is_active, en_is_manual, created_at, updated_at) VALUES
  ('우천 시 부스 운영 안내',
   '공과대학 주점은 오후 6시부터 자정까지 운영합니다. 우천 시 야외 부스 운영이 중단될 수 있으니 타임테이블을 확인해 주세요.',
   '총학생회', 'GENERAL', 0, 0, 0, 1, b'0', NOW(), NOW()),
  ('분실물 안내',
   '분실물은 학생회관 1층 총학생회 부스에서 수령 가능합니다. 축제 종료 후에는 학생회관 사무실로 옮겨집니다.',
   '총학생회', 'GENERAL', 1, 1, 0, 1, b'0', NOW(), NOW()),
  ('주점 운영 시간 변경',
   '경영포차의 운영 시작 시간이 오후 6시에서 오후 7시로 변경되었습니다. 이용에 참고해 주세요.',
   '총학생회', 'GENERAL', 0, 2, 0, 1, b'0', NOW(), NOW()),
  ('안전 수칙 안내',
   '메인 스테이지 앞은 인원이 몰릴 수 있습니다. 안전요원의 안내에 따라 이동해 주시고, 응급 상황 시 의무실을 이용해 주세요.',
   '총학생회', 'GENERAL', 1, 3, 0, 1, b'0', NOW(), NOW());

SELECT '단과대' AS 항목, COUNT(*) AS 건수 FROM college
UNION ALL SELECT '주점', COUNT(*) FROM pub
UNION ALL SELECT '부스', COUNT(*) FROM booth
UNION ALL SELECT '아티스트', COUNT(*) FROM artist
UNION ALL SELECT '공연', COUNT(*) FROM performance
UNION ALL SELECT '공지', COUNT(*) FROM notice
UNION ALL SELECT '홈이미지', COUNT(*) FROM home_image;
