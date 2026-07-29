BEGIN;

ALTER TABLE docking.structure
    ADD COLUMN IF NOT EXISTS chosen_pocket_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.pocket'::regclass
          AND conname = 'pocket_id_structure_unique'
    ) THEN
        ALTER TABLE docking.pocket
            ADD CONSTRAINT pocket_id_structure_unique
            UNIQUE (id, structure_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.structure'::regclass
          AND conname = 'structure_chosen_pocket_unique'
    ) THEN
        ALTER TABLE docking.structure
            ADD CONSTRAINT structure_chosen_pocket_unique
            UNIQUE (chosen_pocket_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.structure'::regclass
          AND conname = 'structure_chosen_pocket_fk'
    ) THEN
        ALTER TABLE docking.structure
            ADD CONSTRAINT structure_chosen_pocket_fk
            FOREIGN KEY (chosen_pocket_id, id)
            REFERENCES docking.pocket (id, structure_id)
            ON DELETE RESTRICT;
    END IF;
END;
$$;

UPDATE docking.structure structure
SET chosen_pocket_id = pocket.id
FROM docking.pocket pocket
JOIN docking.receptor receptor
    ON receptor.id = pocket.receptor_id
WHERE pocket.structure_id = structure.id
  AND pocket.source = 'FPOCKET'::docking.pocket_source
  AND (
      (receptor.target_name = 'METTL7B' AND pocket.pocket_number = 1)
      OR
      (receptor.target_name = 'METTL7A' AND pocket.pocket_number = 12)
  );

COMMENT ON COLUMN docking.structure.chosen_pocket_id IS
    'Selected pocket for this structure; the composite foreign key ensures it belongs to the same structure.';

COMMIT;
