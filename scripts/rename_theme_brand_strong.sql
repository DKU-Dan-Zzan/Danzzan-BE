-- Back up the theme table, then run once before deploying code that deserializes brand.strong.
-- Run this first SELECT separately to inspect rows; an empty result means no data migration is needed.
SELECT id, festival_setting_id
FROM theme
WHERE JSON_CONTAINS_PATH(theme_data, 'one', '$.brand.textEmphasis');

-- Preserves an already-present strong value and removes only the old key.
-- Safe to rerun: rows without the old key are untouched.
UPDATE theme
SET theme_data = JSON_REMOVE(
    JSON_SET(
        theme_data,
        '$.brand.strong',
        COALESCE(
            JSON_EXTRACT(theme_data, '$.brand.strong'),
            JSON_EXTRACT(theme_data, '$.brand.textEmphasis')
        )
    ),
    '$.brand.textEmphasis'
)
WHERE JSON_CONTAINS_PATH(theme_data, 'one', '$.brand.textEmphasis');

-- Should return zero rows after the update.
SELECT id, festival_setting_id
FROM theme
WHERE JSON_CONTAINS_PATH(theme_data, 'one', '$.brand.textEmphasis');
