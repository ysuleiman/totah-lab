BEGIN;

ALTER TABLE docking.pocket
    ADD COLUMN IF NOT EXISTS score DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS probability DOUBLE PRECISION;

COMMENT ON COLUMN docking.pocket.score IS
    'Source-native pocket score; values are not comparable across detection sources.';
COMMENT ON COLUMN docking.pocket.probability IS
    'Source-native pocket probability when provided, currently populated by P2Rank.';

COMMIT;
