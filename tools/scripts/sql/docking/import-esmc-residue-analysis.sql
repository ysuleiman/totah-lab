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

\if :{?artifact_path}
\else
    \echo 'Pass -v artifact_path=<absolute-json-path>'
    \quit 3
\endif

BEGIN;

\lo_import :artifact_path

CREATE TEMPORARY TABLE esmc_import_context ON COMMIT DROP AS
SELECT
    :'structure_id'::BIGINT AS structure_id,
    :'target_id'::BIGINT AS target_id,
    :'pipeline_run_id'::BIGINT AS pipeline_run_id,
    :'artifact_path'::TEXT AS artifact_path,
    convert_from(lo_get(:LASTOID), 'UTF8')::JSONB AS document,
    :LASTOID::OID AS source_oid,
    NULL::BIGINT AS artifact_id;

SELECT lo_unlink(source_oid) FROM esmc_import_context;

DO $$
DECLARE
    context RECORD;
    actual_uniprot_id TEXT;
BEGIN
    SELECT * INTO STRICT context FROM esmc_import_context;

    IF context.document->>'schemaVersion' <> '1.0'
        OR context.document->>'analysisType'
            <> 'ESMC_RESIDUE_CONSTRAINT'
    THEN
        RAISE EXCEPTION
            'Unsupported residue constraint artifact schema or type';
    END IF;

    SELECT receptor.uniprot_id
    INTO actual_uniprot_id
    FROM docking.structure structure
    JOIN docking.receptor receptor
        ON receptor.id = structure.receptor_id
    WHERE structure.id = context.structure_id;

    IF actual_uniprot_id IS NULL THEN
        RAISE EXCEPTION
            'Structure % does not exist or has no UniProt ID',
            context.structure_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM targets target
        WHERE target.id = context.target_id
          AND target.uniprot_id = actual_uniprot_id
    ) THEN
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
    'BIOHUB_ESMC_ANALYSIS',
    context.artifact_path,
    context.pipeline_run_id,
    context.target_id
FROM esmc_import_context context
WHERE NOT EXISTS (
    SELECT 1
    FROM docking.artifacts artifact
    WHERE artifact.label = 'BIOHUB_ESMC_ANALYSIS'
      AND artifact.storage_location = context.artifact_path
      AND artifact.target_id = context.target_id
);

UPDATE esmc_import_context context
SET artifact_id = artifact.id
FROM docking.artifacts artifact
WHERE artifact.label = 'BIOHUB_ESMC_ANALYSIS'
  AND artifact.storage_location = context.artifact_path
  AND artifact.target_id = context.target_id;

CREATE TEMPORARY TABLE esmc_import_residue ON COMMIT DROP AS
SELECT
    (value->>'position')::INTEGER AS position,
    value->>'wildType' AS wild_type,
    (value->>'wildTypeMinusMeanAlternative')::DOUBLE PRECISION
        AS score,
    (value->>'wildTypeRank')::INTEGER AS rank,
    value AS metrics
FROM esmc_import_context context
CROSS JOIN LATERAL jsonb_array_elements(
    context.document->'analysis'->'residues'
) AS residue(value);

DO $$
DECLARE
    context RECORD;
    artifact_residue_count BIGINT;
    matched_residue_count BIGINT;
BEGIN
    SELECT * INTO STRICT context FROM esmc_import_context;
    SELECT count(*) INTO artifact_residue_count FROM esmc_import_residue;

    SELECT count(*)
    INTO matched_residue_count
    FROM esmc_import_residue imported
    JOIN docking.residue residue
      ON residue.structure_id = context.structure_id
     AND residue.chain = 'A'
     AND residue.residue_number = imported.position
     AND residue.insertion_code = '';

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

INSERT INTO docking.residue_analysis (
    structure_id,
    residue_id,
    artifact_id,
    analysis_type,
    score,
    rank,
    metrics
)
SELECT
    context.structure_id,
    residue.id,
    context.artifact_id,
    'ESMC_CONSTRAINT',
    imported.score,
    imported.rank,
    imported.metrics || jsonb_build_object(
        'provider', context.document->'analysis'->>'provider',
        'model', context.document->'analysis'->>'model'
    )
FROM esmc_import_context context
JOIN esmc_import_residue imported ON TRUE
JOIN docking.residue residue
  ON residue.structure_id = context.structure_id
 AND residue.chain = 'A'
 AND residue.residue_number = imported.position
 AND residue.insertion_code = ''
ON CONFLICT (
    structure_id,
    residue_id,
    artifact_id,
    analysis_type
)
DO UPDATE SET
    score = EXCLUDED.score,
    rank = EXCLUDED.rank,
    metrics = EXCLUDED.metrics;

COMMIT;
