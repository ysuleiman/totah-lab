BEGIN;

CREATE TABLE IF NOT EXISTS docking.residue (
    id BIGSERIAL PRIMARY KEY,
    structure_id BIGINT NOT NULL,
    chain VARCHAR(10) NOT NULL,
    residue_number INTEGER NOT NULL,
    insertion_code VARCHAR(1) NOT NULL DEFAULT '',
    residue_name VARCHAR(3) NOT NULL,

    CONSTRAINT residue_structure_fk
        FOREIGN KEY (structure_id)
        REFERENCES docking.structure(id)
        ON DELETE CASCADE,

    CONSTRAINT residue_structure_position_unique
        UNIQUE (
            structure_id,
            chain,
            residue_number,
            insertion_code
        )
);

CREATE INDEX IF NOT EXISTS residue_structure_idx
    ON docking.residue (
        structure_id,
        chain,
        residue_number
    );

ALTER TABLE docking.pocket_residue
    ADD COLUMN IF NOT EXISTS residue_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.pocket_residue'::regclass
          AND conname = 'pocket_residue_residue_fk'
    ) THEN
        ALTER TABLE docking.pocket_residue
            ADD CONSTRAINT pocket_residue_residue_fk
            FOREIGN KEY (residue_id)
            REFERENCES docking.residue(id)
            ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.pocket_residue'::regclass
          AND conname = 'pocket_residue_membership_unique'
    ) THEN
        ALTER TABLE docking.pocket_residue
            ADD CONSTRAINT pocket_residue_membership_unique
            UNIQUE (pocket_id, residue_id);
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS pocket_residue_residue_idx
    ON docking.pocket_residue (residue_id, pocket_id);

COMMIT;
