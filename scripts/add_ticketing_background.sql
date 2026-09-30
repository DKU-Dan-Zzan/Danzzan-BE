-- Select the intended database and take a backup before running. MySQL 8.x.
SET @ddl = IF(
    EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='festival_setting' AND column_name='ticketing_background_image_url'),
    'SELECT 1',
    'ALTER TABLE festival_setting ADD COLUMN ticketing_background_image_url VARCHAR(2048) NULL'
);
PREPARE migration_stmt FROM @ddl;
EXECUTE migration_stmt;
DEALLOCATE PREPARE migration_stmt;
