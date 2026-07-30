BEGIN;

ALTER TABLE docking.docking_run
    ADD COLUMN IF NOT EXISTS source_system varchar(64),
    ADD COLUMN IF NOT EXISTS source_id uuid,
    ADD COLUMN IF NOT EXISTS source_metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE docking.docking_pose
    ADD COLUMN IF NOT EXISTS source_system varchar(64),
    ADD COLUMN IF NOT EXISTS source_id uuid,
    ADD COLUMN IF NOT EXISTS source_artifact_id uuid,
    ADD COLUMN IF NOT EXISTS source_compound_id uuid;

CREATE UNIQUE INDEX IF NOT EXISTS docking_run_source_identity_unique
    ON docking.docking_run (source_system, source_id)
    WHERE source_system IS NOT NULL AND source_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS docking_pose_source_identity_unique
    ON docking.docking_pose (source_system, source_id)
    WHERE source_system IS NOT NULL AND source_id IS NOT NULL;

COMMENT ON COLUMN docking.docking_run.source_system IS
    'External system that supplied this run; paired with source_id for idempotent imports.';
COMMENT ON COLUMN docking.docking_run.source_id IS
    'Immutable run identifier in source_system.';
COMMENT ON COLUMN docking.docking_run.source_metadata IS
    'Source run metadata retained without forcing source-specific columns into the canonical model.';
COMMENT ON COLUMN docking.docking_pose.source_system IS
    'External system that supplied this pose; paired with source_id for idempotent imports.';
COMMENT ON COLUMN docking.docking_pose.source_id IS
    'Immutable pose identifier in source_system.';
COMMENT ON COLUMN docking.docking_pose.source_artifact_id IS
    'Immutable source artifact identifier for provenance and later file verification.';
COMMENT ON COLUMN docking.docking_pose.source_compound_id IS
    'Immutable source compound identifier; ligand_id stores its compact 32-character UUID representation.';

COMMIT;
