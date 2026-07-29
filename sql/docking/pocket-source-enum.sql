BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n
            ON n.oid = t.typnamespace
        WHERE n.nspname = 'docking'
          AND t.typname = 'pocket_source'
    ) THEN
        CREATE TYPE docking.pocket_source AS ENUM (
            'FPOCKET',
            'P2RANK',
            'MANUAL',
            'IMPORTED'
        );
    END IF;
END;
$$;

ALTER TABLE docking.pocket
    ALTER COLUMN source TYPE docking.pocket_source
    USING source::docking.pocket_source;

COMMENT ON COLUMN docking.pocket.source IS
    'Method used to detect or define the pocket.';

COMMIT;
