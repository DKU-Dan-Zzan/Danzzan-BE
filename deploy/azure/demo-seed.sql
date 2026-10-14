-- Synthetic Azure demo data for the isolated danzzan_demo database only.
-- No production IDs, posts, image references, or plaintext credentials are included.
-- The deployment renderer replaces the two BCrypt placeholders from its ignored
-- credential file before this is mounted into MySQL's initialization directory.

SET NAMES utf8mb4;

DELIMITER //
DROP PROCEDURE IF EXISTS assert_danzzan_demo_seed_target//
CREATE PROCEDURE assert_danzzan_demo_seed_target()
BEGIN
    DECLARE existing_rows BIGINT DEFAULT 0;

    IF DATABASE() <> 'danzzan_demo' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing demo seed: selected database must be danzzan_demo';
    END IF;

    SELECT COUNT(*) INTO existing_rows FROM festival_setting;
    IF existing_rows <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Refusing demo seed: festival_setting is not empty';
    END IF;
END//
CALL assert_danzzan_demo_seed_target()//
DROP PROCEDURE assert_danzzan_demo_seed_target//
DELIMITER ;

START TRANSACTION;

INSERT INTO festival_setting (
    id, school_name, festival_name, start_date, end_date, ticketing_enabled,
    active_theme_id, ticketing_background_image_url, ticket_card_background_image_url,
    poster_image_url, logo_image_url, updated_at
) VALUES (
    1, '단국대학교', 'DANZZAN Azure 시연', '2026-10-14', '2026-10-20', b'0',
    NULL, NULL, NULL, NULL, NULL, NOW(6)
);

INSERT INTO timetable_display_setting (id, coming_soon_overlay_enabled, updated_at)
VALUES (1, b'0', NOW(6));

INSERT INTO users (
    student_id, password, name, college, major, academic_status, role,
    manager_operations, manager_ticketing, phone_number, is_phone_verified,
    phone_verified_at, token_version, is_deleted, deleted_at, created_at
) VALUES
    (
        '90000001', '__ADMIN_PASSWORD_HASH__', 'Azure Demo Admin',
        'DANZZAN_DEMO', 'Demonstration', 'ENROLLED', 'ROLE_ADMIN',
        b'1', b'1', NULL, b'1', NOW(6), 0, b'0', NULL, NOW(6)
    ),
    (
        '90000002', '__USER_PASSWORD_HASH__', 'Azure Demo User',
        'DANZZAN_DEMO', 'Demonstration', 'ENROLLED', 'ROLE_USER',
        b'0', b'0', NULL, b'1', NOW(6), 0, b'0', NULL, NOW(6)
    );

INSERT INTO emergency_notice (
    message, is_active, message_en, en_is_manual, created_at, updated_at
) VALUES (
    'Azure 시연용 데이터입니다. 실제 공지가 아닙니다.', b'1',
    'This is synthetic Azure demo data, not a real notice.', b'1', NOW(6), NOW(6)
);

INSERT INTO notice (
    title, content, author, category, is_pinned, thumbnail_image_url,
    display_order, is_emergency, is_active, created_at, updated_at,
    title_en, content_en, en_is_manual
) VALUES (
    'Azure 배포 시연 공지',
    '이 공지는 새 danzzan_demo 데이터베이스에서만 생성한 합성 시연 데이터입니다. 기존 게시물과 이미지는 변경하지 않았습니다.',
    'DANZZAN Demo', 'GENERAL', b'1', NULL,
    0, b'0', b'1', NOW(6), NOW(6),
    'Azure deployment demo notice',
    'This notice is synthetic demo data created only in the new danzzan_demo database. Existing posts and images were not changed.',
    b'1'
);

COMMIT;
