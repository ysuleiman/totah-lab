BEGIN;

CREATE TABLE IF NOT EXISTS docking.ligand (
    id character varying(32) PRIMARY KEY,
    smiles text NOT NULL,
    label character varying(255)
);

COMMENT ON TABLE docking.ligand IS
    'Canonical ligand identity per ligand_id used in docking.docking_pose; SMILES backfilled from the chemflow3 compounds table.';
COMMENT ON COLUMN docking.ligand.id IS
    'Compound UUID without dashes, matching docking.docking_pose.ligand_id.';
COMMENT ON COLUMN docking.ligand.smiles IS
    'Canonical SMILES from the source compound registry.';
COMMENT ON COLUMN docking.ligand.label IS
    'External compound identifier (e.g. MCULE id) when known.';

COMMIT;
