-- Apply once before deploying Theme provenance fields (production uses ddl-auto=validate).
-- Existing Theme rows retain NULL provenance; no backfill can reconstruct their source poster.
ALTER TABLE theme
    ADD COLUMN source_poster_url VARCHAR(2048) NULL,
    ADD COLUMN selected_base_color VARCHAR(7) NULL,
    ADD COLUMN source_colors JSON NULL;
