"""Permission migration tests on a disposable local MySQL database only."""
from pathlib import Path
import unittest
import test_manager_migration as legacy

SCRIPT = Path(__file__).resolve().parents[1] / 'add_manager_permissions.sql'


class ManagerPermissionsMigrationTest(unittest.TestCase):
    sql = legacy.ManagerMigrationTest.sql
    setUp = legacy.ManagerMigrationTest.setUp
    tearDown = legacy.ManagerMigrationTest.tearDown
    users = legacy.ManagerMigrationTest.users

    def prepare(self):
        self.users("'ROLE_ADMIN','ROLE_USER','ROLE_MANAGER'")
        self.sql("INSERT INTO users VALUES (3,'003','ROLE_MANAGER',7,0),(4,'004','ROLE_MANAGER',8,1)")
        self.sql(legacy.SCRIPT.read_text())
        self.sql("INSERT INTO user_role_change_history (actor_user_id,target_user_id,previous_role,new_role,changed_at) VALUES (2,3,'USER','MANAGER',NOW(6))")

    def migrate(self, check=True):
        return self.sql(SCRIPT.read_text(), check=check)

    def snapshot(self):
        return self.sql('SELECT id,role,token_version,manager_operations+0,manager_ticketing+0 FROM users ORDER BY id').stdout.strip()

    def test_backfills_active_managers_and_invalidates_only_their_legacy_sessions(self):
        self.prepare()
        self.migrate()
        self.assertEqual(self.snapshot().splitlines(), [
            '1\tROLE_USER\t3\t0\t0', '2\tROLE_ADMIN\t4\t0\t0',
            '3\tROLE_MANAGER\t8\t1\t1', '4\tROLE_MANAGER\t8\t0\t0'])
        self.assertEqual(self.sql('SELECT previous_permissions,new_permissions FROM user_role_change_history').stdout.strip(), 'NULL\tNULL')

    def test_rerun_preserves_restricted_permissions_versions_and_audit(self):
        self.prepare()
        self.migrate()
        self.sql("UPDATE users SET manager_operations=0,token_version=9 WHERE id=3; UPDATE user_role_change_history SET previous_permissions='',new_permissions='TICKETING'")
        before = self.snapshot()
        self.migrate()
        self.sql(legacy.SCRIPT.read_text())
        self.assertEqual(self.snapshot(), before)
        self.assertEqual(self.sql('SELECT new_permissions FROM user_role_change_history').stdout.strip(), 'TICKETING')

    def test_partial_nullable_columns_resume_without_restoring_existing_false(self):
        self.prepare()
        self.sql('ALTER TABLE users ADD manager_operations BIT(1) NULL; UPDATE users SET manager_operations=0 WHERE id=3')
        self.migrate()
        self.assertIn('3\tROLE_MANAGER\t8\t0\t1', self.snapshot())
        before = self.snapshot()
        self.migrate()
        self.assertEqual(self.snapshot(), before)

    def test_partial_history_resumes_without_overwriting_existing_values(self):
        self.prepare()
        self.sql("ALTER TABLE user_role_change_history ADD previous_permissions VARCHAR(64) NULL; UPDATE user_role_change_history SET previous_permissions='OPERATIONS'")
        self.migrate()
        self.assertEqual(self.sql('SELECT previous_permissions,new_permissions FROM user_role_change_history').stdout.strip(), 'OPERATIONS\tNULL')

    def test_new_users_default_to_no_permissions(self):
        self.prepare()
        self.migrate()
        self.sql("INSERT INTO users (id,student_id,role) VALUES (5,'005','ROLE_USER')")
        self.assertIn('5\tROLE_USER\t0\t0\t0', self.snapshot())

    def test_unexpected_permission_column_stops_before_other_ddl(self):
        self.prepare()
        self.sql('ALTER TABLE users ADD manager_operations VARCHAR(20)')
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='manager_ticketing'").stdout.strip(), '0')

    def test_unexpected_history_stops_before_user_ddl(self):
        self.prepare()
        self.sql('ALTER TABLE user_role_change_history ADD new_permissions INT')
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME LIKE 'manager_%'").stdout.strip(), '0')

    def test_invalid_base_columns_stop_before_ddl_or_backfill(self):
        for alteration in (
            'ALTER TABLE users MODIFY token_version VARCHAR(20) NOT NULL',
            'ALTER TABLE users MODIFY is_deleted INT NOT NULL',
            'ALTER TABLE user_role_change_history MODIFY previous_role VARCHAR(255) NOT NULL',
        ):
            with self.subTest(alteration=alteration):
                self.sql('DROP TABLE IF EXISTS user_role_change_history,users')
                self.prepare()
                self.sql(alteration)
                before = self.sql('SELECT * FROM users ORDER BY id').stdout
                self.assertNotEqual(self.migrate(check=False).returncode, 0)
                self.assertEqual(self.sql('SELECT * FROM users ORDER BY id').stdout, before)
                self.assertEqual(self.sql("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME LIKE 'manager_%'").stdout.strip(), '0')

    def test_base_migration_rejects_malformed_optional_history_columns(self):
        self.prepare()
        self.sql('ALTER TABLE user_role_change_history ADD previous_permissions INT')
        self.assertNotEqual(self.sql(legacy.SCRIPT.read_text(), check=False).returncode, 0)

    def test_missing_prerequisites_do_not_bootstrap_schema(self):
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertEqual(self.sql('SHOW TABLES').stdout.strip(), '')


if __name__ == '__main__':
    unittest.main(verbosity=2)
