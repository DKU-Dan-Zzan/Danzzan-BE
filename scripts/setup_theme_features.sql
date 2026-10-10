-- MySQL 8.x, ONE-TIME setup for a database with no AI Theme/branding changes applied.
-- This is the only setup SQL for teammates starting from the baseline schema.
-- Do not run on a partially migrated database or rerun after success.
-- Do not use mysql --force. MySQL DDL is not transaction-atomic.
-- Read scripts/setup_theme_features.md, verify the target database and take a backup.
-- Other baseline migrations (including ticketing background columns) are separate.

-- Fail before persistent DDL if the expected baseline is missing, any Theme change
-- is already present, or legacy home images cannot safely be assigned to id=1.
-- A populated home_image table is accepted only when festival_setting has exactly
-- one row and that row is id=1. Human verification of image ownership is still required.
CREATE TEMPORARY TABLE theme_setup_preflight_guard (ok TINYINT NOT NULL PRIMARY KEY);
INSERT INTO theme_setup_preflight_guard (ok)
SELECT CASE WHEN
    DATABASE() IS NOT NULL
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE() AND table_name IN ('festival_setting', 'home_image')
           AND table_type = 'BASE TABLE' AND engine = 'InnoDB') = 2
    AND (SELECT COUNT(*) FROM information_schema.tables
         WHERE table_schema = DATABASE() AND table_name = 'theme') = 0
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND (
             (table_name = 'festival_setting' AND column_name = 'id' AND data_type = 'bigint')
             OR (table_name = 'home_image' AND column_name = 'id' AND data_type = 'bigint')
             OR (table_name = 'home_image' AND column_name = 'display_order')
         )) = 3
    AND (SELECT COUNT(*) FROM information_schema.columns
         WHERE table_schema = DATABASE() AND (
             (table_name = 'festival_setting' AND column_name IN
                 ('active_theme_id', 'poster_image_url', 'logo_image_url'))
             OR (table_name = 'home_image' AND column_name = 'festival_setting_id')
         )) = 0
    AND (NOT EXISTS (SELECT 1 FROM home_image)
         OR ((SELECT COUNT(*) FROM festival_setting) = 1
             AND EXISTS (SELECT 1 FROM festival_setting WHERE id = 1)))
THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE theme_setup_preflight_guard;

-- Includes final provenance columns at creation.
-- No seed Theme is needed; active_theme_id remains NULL until activation.
-- The old brand.textEmphasis -> brand.strong JSON update is intentionally omitted:
-- a new theme table has no rows, and current code writes brand.strong. Existing
-- databases with legacy Theme rows need a separate data migration, not this setup.
CREATE TABLE theme (
    id BIGINT NOT NULL AUTO_INCREMENT,
    festival_setting_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    theme_data JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    source_poster_url VARCHAR(2048) NULL,
    selected_base_color VARCHAR(7) NULL,
    source_colors JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_theme_festival_setting_id (festival_setting_id, id),
    CONSTRAINT fk_theme_festival_setting
        FOREIGN KEY (festival_setting_id) REFERENCES festival_setting (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE festival_setting
    ADD COLUMN active_theme_id BIGINT NULL;

-- Enforce that an active Theme belongs to the same festival setting.
ALTER TABLE festival_setting
    ADD CONSTRAINT fk_festival_setting_active_theme
        FOREIGN KEY (id, active_theme_id)
        REFERENCES theme (festival_setting_id, id)
        ON DELETE RESTRICT;

ALTER TABLE festival_setting
    ADD COLUMN poster_image_url VARCHAR(2048) NULL,
    ADD COLUMN logo_image_url VARCHAR(2048) NULL;

ALTER TABLE home_image ADD COLUMN festival_setting_id BIGINT NULL;
UPDATE home_image SET festival_setting_id = 1 WHERE festival_setting_id IS NULL;
ALTER TABLE home_image MODIFY COLUMN festival_setting_id BIGINT NOT NULL;
CREATE INDEX idx_home_image_festival_display ON home_image (festival_setting_id, display_order);
ALTER TABLE home_image ADD CONSTRAINT fk_home_image_festival_setting
    FOREIGN KEY (festival_setting_id) REFERENCES festival_setting (id);
