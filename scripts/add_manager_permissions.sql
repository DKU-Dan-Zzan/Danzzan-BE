-- MySQL 8.4. Back up the explicitly selected database first.
-- Prerequisite: scripts/add_manager_role.sql. Run this BEFORE the new backend.
-- Stop permission-changing writers during rollout. Do not use mysql --force.
-- Existing active managers retain both scopes and must log in again.
-- NULL is the resumable backfill marker; reruns never restore revoked permissions.
DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_danzzan_manager_permissions$$
CREATE PROCEDURE migrate_danzzan_manager_permissions()
BEGIN
    DECLARE before_users BIGINT;
    DECLARE phase VARCHAR(40) DEFAULT 'preflight';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SELECT CONCAT('FAILED during ', phase, '; completed DDL remains. Repair and rerun.') AS migration_status;
        RESIGNAL;
    END;
    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target database explicitly';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('users','user_role_change_history') AND ENGINE='InnoDB' AND TABLE_TYPE='BASE TABLE') <> 2
        OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND IS_NULLABLE='NO' AND (
            (COLUMN_NAME='id' AND COLUMN_TYPE='bigint') OR
            (COLUMN_NAME='token_version' AND COLUMN_TYPE='int' AND EXTRA='') OR
            (COLUMN_NAME='is_deleted' AND COLUMN_TYPE='bit(1)' AND EXTRA='')
        )) <> 3
        OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND INDEX_NAME='PRIMARY' GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)='id')
        OR NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='role' AND EXTRA='' AND COLUMN_TYPE IN (
            'enum(''ROLE_USER'',''ROLE_ADMIN'',''ROLE_MANAGER'')', 'enum(''ROLE_ADMIN'',''ROLE_USER'',''ROLE_MANAGER'')',
            'enum(''ROLE_USER'',''ROLE_MANAGER'',''ROLE_ADMIN'')', 'enum(''ROLE_ADMIN'',''ROLE_MANAGER'',''ROLE_USER'')',
            'enum(''ROLE_MANAGER'',''ROLE_USER'',''ROLE_ADMIN'')', 'enum(''ROLE_MANAGER'',''ROLE_ADMIN'',''ROLE_USER'')'
        ))
        OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND COLUMN_NAME NOT IN ('previous_permissions','new_permissions')) <> 6
        OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND IS_NULLABLE='NO' AND (
            (COLUMN_NAME='id' AND COLUMN_TYPE='bigint' AND EXTRA='auto_increment') OR
            (COLUMN_NAME IN ('actor_user_id','target_user_id') AND COLUMN_TYPE='bigint' AND EXTRA='') OR
            (COLUMN_NAME IN ('previous_role','new_role') AND COLUMN_TYPE='varchar(32)' AND EXTRA='') OR
            (COLUMN_NAME='changed_at' AND COLUMN_TYPE='datetime(6)' AND EXTRA='')
        )) <> 6
        OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND INDEX_NAME='PRIMARY' GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)='id')
        OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND NON_UNIQUE=1 GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)='target_user_id,changed_at' AND SUM(SUB_PART IS NOT NULL)=0)
        OR EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND CONSTRAINT_TYPE='FOREIGN KEY') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid base schema: verify users role/version/deleted/id and role history before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME IN ('manager_operations','manager_ticketing') AND (COLUMN_TYPE <> 'bit(1)' OR EXTRA <> ''))
        OR EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND COLUMN_NAME IN ('previous_permissions','new_permissions') AND (COLUMN_TYPE <> 'varchar(64)' OR IS_NULLABLE <> 'YES' OR EXTRA <> '')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected existing permission columns; manual schema review required';
    END IF;
    SELECT COUNT(*) INTO before_users FROM users;
    SET phase='nullable permission columns';
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='manager_operations') THEN
        ALTER TABLE users ADD COLUMN manager_operations BIT(1) NULL DEFAULT NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='manager_ticketing') THEN
        ALTER TABLE users ADD COLUMN manager_ticketing BIT(1) NULL DEFAULT NULL;
    END IF;
    SET phase='backfill';
    START TRANSACTION;
    UPDATE users
       SET token_version = token_version + IF(role='ROLE_MANAGER' AND is_deleted=b'0',1,0),
           manager_operations = COALESCE(manager_operations, IF(role='ROLE_MANAGER' AND is_deleted=b'0',b'1',b'0')),
           manager_ticketing = COALESCE(manager_ticketing, IF(role='ROLE_MANAGER' AND is_deleted=b'0',b'1',b'0'))
     WHERE manager_operations IS NULL OR manager_ticketing IS NULL;
    COMMIT;
    SET phase='permission defaults';
    ALTER TABLE users
        MODIFY COLUMN manager_operations BIT(1) NOT NULL DEFAULT b'0',
        MODIFY COLUMN manager_ticketing BIT(1) NOT NULL DEFAULT b'0';
    SET phase='permission history';
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND COLUMN_NAME='previous_permissions') THEN
        ALTER TABLE user_role_change_history ADD COLUMN previous_permissions VARCHAR(64) NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND COLUMN_NAME='new_permissions') THEN
        ALTER TABLE user_role_change_history ADD COLUMN new_permissions VARCHAR(64) NULL;
    END IF;
    IF (SELECT COUNT(*) FROM users) <> before_users THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'User count changed; investigate concurrent writers';
    END IF;
    SELECT role, manager_operations+0 AS operations, manager_ticketing+0 AS ticketing, COUNT(*) AS user_count
      FROM users GROUP BY role,manager_operations,manager_ticketing;
    SELECT 'COMPLETE: existing active managers retain both permissions; roles unchanged' AS migration_status;
END$$
CALL migrate_danzzan_manager_permissions()$$
DROP PROCEDURE migrate_danzzan_manager_permissions$$
DELIMITER ;
