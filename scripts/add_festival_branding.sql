-- Apply before deploying code that maps these columns. MySQL 8.x.
-- Back up the selected database first. Existing home_image rows belong to festival_setting.id = 1.
ALTER TABLE festival_setting
    ADD COLUMN poster_image_url VARCHAR(2048) NULL,
    ADD COLUMN logo_image_url VARCHAR(2048) NULL;

ALTER TABLE home_image ADD COLUMN festival_setting_id BIGINT NULL;

-- Abort the migration if festival_setting.id = 1 is absent, or if any home_image row
-- belongs to a different festival. Check both assumptions before running this update.
UPDATE home_image SET festival_setting_id = 1 WHERE festival_setting_id IS NULL;

ALTER TABLE home_image MODIFY COLUMN festival_setting_id BIGINT NOT NULL;
CREATE INDEX idx_home_image_festival_display ON home_image (festival_setting_id, display_order);
ALTER TABLE home_image ADD CONSTRAINT fk_home_image_festival_setting
    FOREIGN KEY (festival_setting_id) REFERENCES festival_setting(id);
