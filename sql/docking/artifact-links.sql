BEGIN;

ALTER TABLE public.artifacts
    SET SCHEMA docking;

ALTER TABLE docking.structure
    ADD COLUMN IF NOT EXISTS artifact_id BIGINT;

ALTER TABLE docking.structure
    DROP CONSTRAINT IF EXISTS structure_file_version_unique;

ALTER TABLE docking.structure
    DROP CONSTRAINT IF EXISTS structure_sha256_check;

ALTER TABLE docking.structure
    DROP COLUMN IF EXISTS storage_uri;

ALTER TABLE docking.structure
    DROP COLUMN IF EXISTS sha256;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.structure'::regclass
          AND conname = 'structure_artifact_fk'
    ) THEN
        ALTER TABLE docking.structure
            ADD CONSTRAINT structure_artifact_fk
            FOREIGN KEY (artifact_id)
            REFERENCES docking.artifacts(id)
            ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.structure'::regclass
          AND conname = 'structure_file_version_unique'
    ) THEN
        ALTER TABLE docking.structure
            ADD CONSTRAINT structure_file_version_unique
            UNIQUE (receptor_id, artifact_id, preparation_state);
    END IF;
END;
$$;

ALTER TABLE docking.pocket
    ADD COLUMN IF NOT EXISTS artifact_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.pocket'::regclass
          AND conname = 'pocket_artifact_fk'
    ) THEN
        ALTER TABLE docking.pocket
            ADD CONSTRAINT pocket_artifact_fk
            FOREIGN KEY (artifact_id)
            REFERENCES docking.artifacts(id)
            ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.pocket'::regclass
          AND conname = 'pocket_structure_source_number_unique'
    ) THEN
        ALTER TABLE docking.pocket
            ADD CONSTRAINT pocket_structure_source_number_unique
            UNIQUE (structure_id, source, pocket_number);
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS pocket_artifact_idx
    ON docking.pocket (artifact_id);

COMMIT;
