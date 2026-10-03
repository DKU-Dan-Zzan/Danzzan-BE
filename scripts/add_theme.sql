-- Apply once before deploying the Theme entities (production uses ddl-auto=validate).
-- Example: mysql -u <user> -p <database> < scripts/add_theme.sql
-- No seed row is required: active_theme_id stays NULL until an administrator activates one.

CREATE TABLE theme (
    id BIGINT NOT NULL AUTO_INCREMENT,
    festival_setting_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    theme_data JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_theme_festival_setting_id (festival_setting_id, id),
    CONSTRAINT fk_theme_festival_setting
        FOREIGN KEY (festival_setting_id) REFERENCES festival_setting (id)
        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE festival_setting
    ADD COLUMN active_theme_id BIGINT NULL;

-- Composite FK prevents a festival setting from pointing at another festival's Theme.
-- NULL active_theme_id is allowed and means the frontend uses its bundled defaults.
ALTER TABLE festival_setting
    ADD CONSTRAINT fk_festival_setting_active_theme
        FOREIGN KEY (id, active_theme_id)
        REFERENCES theme (festival_setting_id, id)
        ON DELETE RESTRICT;
