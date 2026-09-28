"""MySQL 8.4 migration contract tests. Uses only a uniquely named disposable DB.
Run: python3 scripts/tests/test_manager_migration.py
Requires local Docker container danzzan-mysql (override MYSQL_TEST_CONTAINER).
The container's MYSQL_ROOT_PASSWORD is used internally, never printed.
"""
import os
from pathlib import Path
import subprocess
import unittest
import uuid

SCRIPT = Path(__file__).resolve().parents[1] / 'add_manager_role.sql'
CONTAINER = os.environ.get('MYSQL_TEST_CONTAINER', 'danzzan-mysql')


class ManagerMigrationTest(unittest.TestCase):
    def sql(self, text, *, database=True, check=True):
        command = ['docker', 'exec', '-i', CONTAINER, 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names "$@"',
                   'mysql-test']
        if database:
            command.append(self.database)
        result = subprocess.run(command, input=text, text=True, capture_output=True)
        if check and result.returncode:
            self.fail(result.stderr)
        return result

    def setUp(self):
        self.database = 'manager_migration_test_' + uuid.uuid4().hex
        self.sql(f'CREATE DATABASE `{self.database}`', database=False)

    def tearDown(self):
        assert self.database.startswith('manager_migration_test_')
        self.sql(f'DROP DATABASE `{self.database}`', database=False)

    def users(self, roles="'ROLE_ADMIN','ROLE_USER'", suffix='NOT NULL'):
        self.sql(f'''CREATE TABLE users (
            id BIGINT PRIMARY KEY, student_id VARCHAR(255),
            role ENUM({roles}) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin {suffix},
            token_version INT NOT NULL DEFAULT 0, is_deleted BIT NOT NULL DEFAULT 0
        ); INSERT INTO users VALUES (1,'001','ROLE_USER',3,0),(2,'002','ROLE_ADMIN',4,0);''')

    def migrate(self, check=True):
        return self.sql(SCRIPT.read_text(), check=check)

    def role_definition(self):
        return self.sql("SELECT COLUMN_TYPE,IS_NULLABLE,IFNULL(COLUMN_DEFAULT,'<NULL>'),COLLATION_NAME,COLUMN_COMMENT FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='users' AND COLUMN_NAME='role'").stdout.strip()

    def test_append_preserves_order_data_and_attributes_and_reruns(self):
        self.users(suffix="NULL DEFAULT 'ROLE_USER' COMMENT 'legacy role'")
        before = self.sql('SELECT * FROM users ORDER BY id').stdout
        self.migrate()
        expected = "enum('ROLE_ADMIN','ROLE_USER','ROLE_MANAGER')\tYES\tROLE_USER\tutf8mb4_bin\tlegacy role"
        self.assertEqual(self.role_definition(), expected)
        self.assertEqual(self.sql('SELECT * FROM users ORDER BY id').stdout, before)
        self.migrate()
        self.assertEqual(self.role_definition(), expected)
        self.assertEqual(self.sql('SELECT COUNT(*) FROM user_role_change_history').stdout.strip(), '0')

    def test_reverse_order(self):
        self.users("'ROLE_USER','ROLE_ADMIN'")
        self.migrate()
        self.assertTrue(self.role_definition().startswith("enum('ROLE_USER','ROLE_ADMIN','ROLE_MANAGER')"))

    def test_partial_role_already_applied_still_creates_history(self):
        self.users("'ROLE_USER','ROLE_ADMIN','ROLE_MANAGER'")
        self.migrate()
        self.assertEqual(self.sql('SELECT COUNT(*) FROM user_role_change_history').stdout.strip(), '0')

    def test_missing_prerequisites_stop_before_history(self):
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertEqual(self.sql('SHOW TABLES').stdout, '')

    def test_unknown_enum_stops_before_ddl(self):
        self.users("'ROLE_ADMIN','ROLE_USER','ROLE_OTHER'")
        before = self.role_definition()
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertEqual(self.role_definition(), before)
        self.assertNotIn('user_role_change_history', self.sql('SHOW TABLES').stdout)

    def test_incomplete_history_stops_before_role_alter(self):
        self.users()
        self.sql('CREATE TABLE user_role_change_history (id BIGINT PRIMARY KEY)')
        before = self.role_definition()
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertEqual(self.role_definition(), before)

    def test_varchar_needs_manual_validation(self):
        self.users()
        self.sql('ALTER TABLE users MODIFY role VARCHAR(32) NOT NULL')
        self.assertNotEqual(self.migrate(check=False).returncode, 0)
        self.assertTrue(self.role_definition().startswith('varchar(32)'))


if __name__ == '__main__':
    unittest.main(verbosity=2)
