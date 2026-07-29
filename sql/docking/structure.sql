BEGIN;

CREATE TABLE IF NOT EXISTS docking.structure (
    id BIGSERIAL PRIMARY KEY,
    receptor_id BIGINT NOT NULL,

    source VARCHAR(20) NOT NULL,
    source_accession VARCHAR(100),
    chain VARCHAR(10),
    model_number INTEGER,

    artifact_id BIGINT NOT NULL,

    preparation_state VARCHAR(20) NOT NULL DEFAULT 'RAW',
    parent_structure_id BIGINT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT structure_receptor_fk
        FOREIGN KEY (receptor_id)
        REFERENCES docking.receptor(id)
        ON DELETE CASCADE,

    CONSTRAINT structure_parent_fk
        FOREIGN KEY (parent_structure_id)
        REFERENCES docking.structure(id)
        ON DELETE RESTRICT,

    CONSTRAINT structure_artifact_fk
        FOREIGN KEY (artifact_id)
        REFERENCES docking.artifacts(id)
        ON DELETE RESTRICT,

    CONSTRAINT structure_source_check
        CHECK (
            source IN (
                'PDB',
                'ALPHAFOLD',
                'BIOHUB',
                'UPLOADED',
                'GENERATED'
            )
        ),

    CONSTRAINT structure_preparation_state_check
        CHECK (
            preparation_state IN (
                'RAW',
                'CLEANED',
                'PROTONATED',
                'PDBQT'
            )
        ),

    CONSTRAINT structure_model_number_check
        CHECK (
            model_number IS NULL
            OR model_number > 0
        ),

    CONSTRAINT structure_not_own_parent_check
        CHECK (
            parent_structure_id IS NULL
            OR parent_structure_id <> id
        ),

    CONSTRAINT structure_file_version_unique
        UNIQUE (
            receptor_id,
            artifact_id,
            preparation_state
        )
);

CREATE INDEX IF NOT EXISTS structure_receptor_idx
    ON docking.structure (
        receptor_id,
        source,
        source_accession
    );

ALTER TABLE docking.pocket
    ADD COLUMN IF NOT EXISTS structure_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.pocket'::regclass
          AND conname = 'pocket_structure_fk'
    ) THEN
        ALTER TABLE docking.pocket
            ADD CONSTRAINT pocket_structure_fk
            FOREIGN KEY (structure_id)
            REFERENCES docking.structure(id)
            ON DELETE RESTRICT;
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS pocket_structure_idx
    ON docking.pocket (structure_id);

COMMENT ON TABLE docking.structure IS
    'Immutable receptor structure files and their preparation lineage.';

COMMENT ON COLUMN docking.structure.source IS
    'Origin of the structure: PDB, ALPHAFOLD, BIOHUB, UPLOADED, or GENERATED.';

COMMENT ON COLUMN docking.structure.artifact_id IS
    'Artifact containing the immutable structure file.';

COMMENT ON COLUMN docking.pocket.structure_id IS
    'Structure on which this pocket was detected.';

COMMIT;
