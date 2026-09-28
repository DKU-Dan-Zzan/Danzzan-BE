-- MySQL 8.4. Run against an explicitly selected, backed-up database:
-- mysql -u <user> -p <database> < scripts/add_manager_role.sql
-- Do not use --force: a failed preflight must stop execution.
-- This is not a database bootstrap. No account is promoted by this migration.
-- DDL commits independently. A successful enum alteration survives later failure;
-- rerunning checks both steps and resumes the missing step. No data is deleted.

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_danzzan_manager_role$$
CREATE PROCEDURE migrate_danzzan_manager_role()
BEGIN
    DECLARE role_type TEXT;
    DECLARE role_nullable VARCHAR(3);
    DECLARE role_default TEXT;
    DECLARE role_charset VARCHAR(64);
    DECLARE role_collation VARCHAR(64);
    DECLARE role_comment TEXT;
    DECLARE role_extra VARCHAR(255);
    DECLARE history_exists INT DEFAULT 0;
    DECLARE before_users BIGINT;
    DECLARE before_admins BIGINT;
    DECLARE before_members BIGINT;
    DECLARE before_managers BIGINT;
    DECLARE phase VARCHAR(32) DEFAULT 'preflight';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        SELECT CONCAT('FAILED during ', phase, '; completed DDL is retained. Repair the reported issue and rerun.') AS migration_status;
        RESIGNAL;
    END;

    IF DATABASE() IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select the target database explicitly before running this migration';
    END IF;
    IF FIND_IN_SET('NO_BACKSLASH_ESCAPES', @@SESSION.sql_mode) > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'NO_BACKSLASH_ESCAPES needs manual DDL review; no application schema changed';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND TABLE_TYPE='BASE TABLE' AND ENGINE='InnoDB') <> 1
        OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME IN ('id','student_id','role','token_version','is_deleted')) <> 5 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Prerequisite missing: InnoDB users with id/student_id/role/token_version/is_deleted';
    END IF;

    SELECT COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,CHARACTER_SET_NAME,COLLATION_NAME,COLUMN_COMMENT,EXTRA
      INTO role_type,role_nullable,role_default,role_charset,role_collation,role_comment,role_extra
      FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='role';
    IF role_type NOT IN (
        'enum(''ROLE_USER'',''ROLE_ADMIN'')', 'enum(''ROLE_ADMIN'',''ROLE_USER'')',
        'enum(''ROLE_USER'',''ROLE_ADMIN'',''ROLE_MANAGER'')', 'enum(''ROLE_ADMIN'',''ROLE_USER'',''ROLE_MANAGER'')',
        'enum(''ROLE_USER'',''ROLE_MANAGER'',''ROLE_ADMIN'')', 'enum(''ROLE_ADMIN'',''ROLE_MANAGER'',''ROLE_USER'')',
        'enum(''ROLE_MANAGER'',''ROLE_USER'',''ROLE_ADMIN'')', 'enum(''ROLE_MANAGER'',''ROLE_ADMIN'',''ROLE_USER'')'
    ) OR role_extra <> '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected users.role definition; VARCHAR/check/generated schemas require manual validation';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS tc
        JOIN information_schema.CHECK_CONSTRAINTS cc ON cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME
        WHERE tc.TABLE_SCHEMA=DATABASE() AND tc.TABLE_NAME='users' AND LOWER(cc.CHECK_CLAUSE) REGEXP '(^|[^a-z_])role([^a-z_]|$)') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'users.role has a CHECK constraint; review ROLE_MANAGER acceptance manually';
    END IF;

    SELECT COUNT(*) INTO history_exists FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history';
    IF history_exists > 0 THEN
        IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND TABLE_TYPE='BASE TABLE' AND ENGINE='InnoDB') <> 1
          OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND COLUMN_NAME NOT IN ('previous_permissions','new_permissions')) <> 6
          OR (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND IS_NULLABLE='NO' AND (
                (COLUMN_NAME='id' AND COLUMN_TYPE='bigint' AND EXTRA='auto_increment') OR
                (COLUMN_NAME IN ('actor_user_id','target_user_id') AND COLUMN_TYPE='bigint' AND EXTRA='') OR
                (COLUMN_NAME IN ('previous_role','new_role') AND COLUMN_TYPE='varchar(32)' AND EXTRA='') OR
                (COLUMN_NAME='changed_at' AND COLUMN_TYPE='datetime(6)' AND EXTRA='')
          )) <> 6
          OR (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND INDEX_NAME='PRIMARY') <> 1
          OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND INDEX_NAME='PRIMARY' AND COLUMN_NAME='id' AND SEQ_IN_INDEX=1)
          OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND NON_UNIQUE=1 GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)='target_user_id,changed_at' AND SUM(SUB_PART IS NOT NULL)=0)
          OR EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND COLUMN_NAME IN ('previous_permissions','new_permissions') AND (COLUMN_TYPE <> 'varchar(64)' OR IS_NULLABLE <> 'YES' OR EXTRA <> ''))
          OR EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='user_role_change_history' AND CONSTRAINT_TYPE='FOREIGN KEY') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing user_role_change_history differs: verify engine, base columns/types/nullability, PK and target/time index';
        END IF;
    END IF;

    SELECT COUNT(*),COALESCE(SUM(role='ROLE_ADMIN'),0),COALESCE(SUM(role='ROLE_USER'),0),COALESCE(SUM(role='ROLE_MANAGER'),0)
      INTO before_users,before_admins,before_members,before_managers FROM users;
    SELECT DATABASE() AS target_database, VERSION() AS mysql_version, role_type AS original_role_type;
    SET phase='A: role enum';
    IF LOCATE('''ROLE_MANAGER''',role_type)=0 THEN
        SET @manager_role_ddl=CONCAT('ALTER TABLE `users` MODIFY COLUMN `role` ', LEFT(role_type,CHAR_LENGTH(role_type)-1),
            ',''ROLE_MANAGER'') CHARACTER SET ',role_charset,' COLLATE ',role_collation,
            IF(role_nullable='YES',' NULL',' NOT NULL'),
            IF(role_default IS NULL,IF(role_nullable='YES',' DEFAULT NULL',''),CONCAT(' DEFAULT ',QUOTE(role_default))),
            ' COMMENT ',QUOTE(role_comment));
        PREPARE manager_stmt FROM @manager_role_ddl;
        EXECUTE manager_stmt;
        DEALLOCATE PREPARE manager_stmt;
        SELECT 'A: ROLE_MANAGER appended, existing order and attributes preserved' AS migration_status;
    ELSE
        SELECT 'A: skipped; ROLE_MANAGER already present' AS migration_status;
    END IF;

    SET phase='B: role history';
    IF history_exists=0 THEN
        CREATE TABLE user_role_change_history (
            id BIGINT NOT NULL AUTO_INCREMENT,
            actor_user_id BIGINT NOT NULL,
            target_user_id BIGINT NOT NULL,
            previous_role VARCHAR(32) NOT NULL,
            new_role VARCHAR(32) NOT NULL,
            changed_at DATETIME(6) NOT NULL,
            PRIMARY KEY (id),
            KEY idx_role_history_target_time (target_user_id,changed_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        SELECT 'B: history table created' AS migration_status;
    ELSE
        SELECT 'B: skipped; existing history schema verified' AS migration_status;
    END IF;
    SET phase='postflight';
    IF (SELECT COUNT(*) FROM users) <> before_users
       OR (SELECT COUNT(*) FROM users WHERE role='ROLE_ADMIN') <> before_admins
       OR (SELECT COUNT(*) FROM users WHERE role='ROLE_USER') <> before_members
       OR (SELECT COUNT(*) FROM users WHERE role='ROLE_MANAGER') <> before_managers THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Role counts changed during migration; investigate concurrent writers before declaring success';
    END IF;
    SHOW CREATE TABLE users;
    SHOW CREATE TABLE user_role_change_history;
    SELECT role,COUNT(*) AS user_count FROM users GROUP BY role;
    SELECT 'COMPLETE: no account roles changed' AS migration_status;
END$$
CALL migrate_danzzan_manager_role()$$
DROP PROCEDURE migrate_danzzan_manager_role$$
DELIMITER ;
