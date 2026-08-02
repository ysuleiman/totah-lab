BEGIN;

ALTER TABLE docking.receptor
    ADD COLUMN IF NOT EXISTS uniprot_id VARCHAR(20),
    ADD COLUMN IF NOT EXISTS protein_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS gene_name VARCHAR(50),
    ADD COLUMN IF NOT EXISTS organism VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'docking.receptor'::regclass
          AND conname = 'receptor_uniprot_id_unique'
    ) THEN
        ALTER TABLE docking.receptor
            ADD CONSTRAINT receptor_uniprot_id_unique
            UNIQUE (uniprot_id);
    END IF;
END;
$$;

UPDATE docking.receptor
SET
    uniprot_id = 'Q6UX53',
    protein_name = 'Thiol S-methyltransferase TMT1B',
    gene_name = 'METTL7B',
    organism = 'Homo sapiens'
WHERE target_name = 'METTL7B';

UPDATE docking.receptor
SET
    uniprot_id = 'Q9H8H3',
    protein_name = 'Thiol S-methyltransferase TMT1A',
    gene_name = 'METTL7A',
    organism = 'Homo sapiens'
WHERE target_name = 'METTL7A';

COMMENT ON COLUMN docking.receptor.uniprot_id IS
    'Canonical UniProt accession for this receptor.';
COMMENT ON COLUMN docking.receptor.protein_name IS
    'Human-readable protein name from the structure source metadata.';
COMMENT ON COLUMN docking.receptor.gene_name IS
    'Canonical gene symbol used by Totah Lab.';
COMMENT ON COLUMN docking.receptor.organism IS
    'Scientific name of the source organism.';

COMMIT;
