\set ON_ERROR_STOP on

COPY (
WITH analysis AS (
    SELECT id FROM campaigns
    WHERE name = 'Analysis - residue fingerprints for all completed selectivity docking rank1'
    ORDER BY created_at DESC LIMIT 1
), sources AS (
    SELECT jsonb_array_elements_text(p.config::jsonb->'source_campaign_ids')::uuid AS campaign_id
    FROM pipeline_plans p JOIN analysis a ON a.id = p.campaign_id
), rank1 AS (
    SELECT
        nullif(mv.compound_id, '')::uuid AS compound_id,
        mv.score::double precision AS score,
        lower(coalesce(dr.run_metadata::jsonb->>'selectivity_role', '')) AS role,
        row_number() OVER (
            PARTITION BY nullif(mv.compound_id, '')::uuid,
                         lower(coalesce(dr.run_metadata::jsonb->>'selectivity_role', ''))
            ORDER BY mv.score::double precision, mv.docking_pose_id
        ) AS role_rank
    FROM derived.mv_pose_residue_fingerprints mv
    JOIN sources s ON s.campaign_id = mv.campaign_id
    JOIN docking_runs dr ON dr.id = mv.docking_run_id
    WHERE mv.pose_rank = 1 AND nullif(mv.compound_id, '') IS NOT NULL
      AND lower(coalesce(dr.run_metadata::jsonb->>'selectivity_role', '')) IN ('primary', 'counter')
), paired AS (
    SELECT
        compound_id,
        min(score) FILTER (WHERE role = 'primary' AND role_rank = 1) AS score_7b,
        min(score) FILTER (WHERE role = 'counter' AND role_rank = 1) AS score_7a
    FROM rank1 GROUP BY compound_id
    HAVING min(score) FILTER (WHERE role = 'primary' AND role_rank = 1) IS NOT NULL
       AND min(score) FILTER (WHERE role = 'counter' AND role_rank = 1) IS NOT NULL
)
SELECT
    row_number() OVER (ORDER BY p.score_7a - p.score_7b DESC, p.score_7b, p.compound_id) AS alternative_rank,
    encode(digest(coalesce(c.inchi_key, '') || E'\n' || coalesce(c.smiles, '') || E'\n' || coalesce(c.mol_structure, ''), 'sha256'), 'hex') AS immutable_ligand_identity_sha256,
    p.compound_id AS historical_database_compound_id_provenance_only,
    c.external_id,
    c.name,
    c.smiles,
    c.inchi_key,
    p.score_7b AS historical_engine_output_7b,
    p.score_7a AS historical_engine_output_7a,
    p.score_7a - p.score_7b AS historical_delta_7a_minus_7b,
    (p.score_7b <= -7.5 AND p.score_7a - p.score_7b >= 1.0) AS historical_216_member
FROM paired p JOIN compounds c ON c.id = p.compound_id
WHERE p.score_7b <= -7.0 AND p.score_7a - p.score_7b >= 1.5
ORDER BY alternative_rank
) TO STDOUT WITH (FORMAT csv, HEADER true);
