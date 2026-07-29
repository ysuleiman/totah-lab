\set ON_ERROR_STOP on

\if :{?structure_id}
\else
    \echo 'Pass -v structure_id=<id>'
    \quit 3
\endif

\if :{?target_id}
\else
    \echo 'Pass -v target_id=<id>'
    \quit 3
\endif

\if :{?pipeline_run_id}
\else
    \echo 'Pass -v pipeline_run_id=<id>'
    \quit 3
\endif

\if :{?pocket_number}
\else
    \echo 'Pass -v pocket_number=<number>'
    \quit 3
\endif

\if :{?artifact_path}
\else
    \echo 'Pass -v artifact_path=<absolute-pocket-json-path>'
    \quit 3
\endif

BEGIN;

\lo_import :artifact_path

CREATE TEMPORARY TABLE biohub_pocket_context ON COMMIT DROP AS
SELECT
    :'structure_id'::BIGINT AS structure_id,
    :'target_id'::BIGINT AS target_id,
    :'pipeline_run_id'::BIGINT AS pipeline_run_id,
    :'pocket_number'::INTEGER AS pocket_number,
    :'artifact_path'::TEXT AS artifact_path,
    convert_from(lo_get(:LASTOID), 'UTF8')::JSONB AS document,
    :LASTOID::OID AS source_oid,
    NULL::BIGINT AS artifact_id,
    NULL::BIGINT AS pocket_id;

SELECT lo_unlink(source_oid) FROM biohub_pocket_context;

DO $$
DECLARE
    context RECORD;
    actual_uniprot_id TEXT;
BEGIN
    SELECT * INTO STRICT context FROM biohub_pocket_context;

    IF context.document->>'proteinChain' IS NULL
        OR context.document->>'ligandChain' IS NULL
        OR context.document->>'ligandCcd' IS NULL
        OR context.document->>'cutoff' IS NULL
        OR jsonb_typeof(context.document->'residues') <> 'array'
    THEN
        RAISE EXCEPTION 'Unsupported BioHub ligand pocket artifact';
    END IF;

    SELECT receptor.uniprot_id
    INTO actual_uniprot_id
    FROM docking.structure structure
    JOIN docking.receptor receptor
      ON receptor.id = structure.receptor_id
    WHERE structure.id = context.structure_id;

    IF actual_uniprot_id IS NULL
        OR NOT EXISTS (
            SELECT 1
            FROM targets target
            WHERE target.id = context.target_id
              AND target.uniprot_id = actual_uniprot_id
        )
    THEN
        RAISE EXCEPTION
            'Target % does not match structure % (%)',
            context.target_id,
            context.structure_id,
            actual_uniprot_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pipeline_runs run
        WHERE run.id = context.pipeline_run_id
          AND run.status = 'FINISHED'
    ) THEN
        RAISE EXCEPTION
            'Pipeline run % does not exist or is not FINISHED',
            context.pipeline_run_id;
    END IF;
END;
$$;

INSERT INTO docking.artifacts (
    filename,
    label,
    storage_location,
    pipeline_run_id,
    target_id
)
SELECT
    regexp_replace(context.artifact_path, '^.*/', ''),
    'BIOHUB_LIGAND_POCKET',
    context.artifact_path,
    context.pipeline_run_id,
    context.target_id
FROM biohub_pocket_context context
WHERE NOT EXISTS (
    SELECT 1
    FROM docking.artifacts artifact
    WHERE artifact.label = 'BIOHUB_LIGAND_POCKET'
      AND artifact.storage_location = context.artifact_path
      AND artifact.target_id = context.target_id
);

UPDATE biohub_pocket_context context
SET artifact_id = artifact.id
FROM docking.artifacts artifact
WHERE artifact.label = 'BIOHUB_LIGAND_POCKET'
  AND artifact.storage_location = context.artifact_path
  AND artifact.target_id = context.target_id;

CREATE TEMPORARY TABLE biohub_pocket_residue ON COMMIT DROP AS
SELECT
    value->>'chain' AS chain,
    (value->>'residueNumber')::INTEGER AS residue_number,
    value->>'residueName' AS residue_name
FROM biohub_pocket_context context
CROSS JOIN LATERAL jsonb_array_elements(
    context.document->'residues'
) AS residue(value);

DO $$
DECLARE
    context RECORD;
    artifact_residue_count BIGINT;
    matched_residue_count BIGINT;
BEGIN
    SELECT * INTO STRICT context FROM biohub_pocket_context;
    SELECT count(*) INTO artifact_residue_count FROM biohub_pocket_residue;

    SELECT count(*)
    INTO matched_residue_count
    FROM biohub_pocket_residue imported
    JOIN docking.residue residue
      ON residue.structure_id = context.structure_id
     AND residue.chain = imported.chain
     AND residue.residue_number = imported.residue_number
     AND residue.insertion_code = ''
     AND residue.residue_name = imported.residue_name;

    IF artifact_residue_count = 0
        OR matched_residue_count <> artifact_residue_count
    THEN
        RAISE EXCEPTION
            'Matched % of % artifact residues to structure %',
            matched_residue_count,
            artifact_residue_count,
            context.structure_id;
    END IF;
END;
$$;

INSERT INTO docking.pocket (
    receptor_id,
    structure_id,
    pocket_number,
    source,
    artifact_id
)
SELECT
    structure.receptor_id,
    context.structure_id,
    context.pocket_number,
    'BIOHUB'::docking.pocket_source,
    context.artifact_id
FROM biohub_pocket_context context
JOIN docking.structure structure
  ON structure.id = context.structure_id
ON CONFLICT (structure_id, source, pocket_number)
DO UPDATE SET
    receptor_id = EXCLUDED.receptor_id,
    artifact_id = EXCLUDED.artifact_id;

UPDATE biohub_pocket_context context
SET pocket_id = pocket.id
FROM docking.pocket pocket
WHERE pocket.structure_id = context.structure_id
  AND pocket.source = 'BIOHUB'::docking.pocket_source
  AND pocket.pocket_number = context.pocket_number;

DELETE FROM docking.pocket_residue pocket_residue
USING biohub_pocket_context context
WHERE pocket_residue.pocket_id = context.pocket_id;

INSERT INTO docking.pocket_residue (
    pocket_id,
    chain,
    residue_number,
    residue_name,
    residue_id
)
SELECT
    context.pocket_id,
    imported.chain,
    imported.residue_number,
    imported.residue_name,
    residue.id
FROM biohub_pocket_context context
JOIN biohub_pocket_residue imported ON TRUE
JOIN docking.residue residue
  ON residue.structure_id = context.structure_id
 AND residue.chain = imported.chain
 AND residue.residue_number = imported.residue_number
 AND residue.insertion_code = ''
 AND residue.residue_name = imported.residue_name;

COMMIT;
