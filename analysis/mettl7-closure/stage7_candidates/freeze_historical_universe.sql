\set ON_ERROR_STOP on

COPY (
WITH analysis AS (
    SELECT c.id, c.name, c.created_at
    FROM campaigns c
    WHERE c.name = 'Analysis - residue fingerprints for all completed selectivity docking rank1'
    ORDER BY c.created_at DESC
    LIMIT 1
),
sources AS (
    SELECT
        a.id AS analysis_campaign_id,
        a.name AS analysis_campaign_name,
        jsonb_array_elements_text(p.config::jsonb->'source_campaign_ids')::uuid AS campaign_id
    FROM analysis a
    JOIN pipeline_plans p ON p.campaign_id = a.id
),
rank1 AS (
    SELECT
        s.analysis_campaign_id,
        s.analysis_campaign_name,
        mv.campaign_id AS source_campaign_id,
        mv.docking_run_id,
        mv.docking_pose_id,
        mv.score::double precision AS score,
        nullif(mv.compound_id, '')::uuid AS compound_id,
        lower(coalesce(dr.run_metadata::jsonb->>'selectivity_role', '')) AS target_role,
        coalesce(dr.run_metadata::jsonb->>'target_label', '') AS target_label,
        row_number() OVER (
            PARTITION BY nullif(mv.compound_id, '')::uuid,
                         lower(coalesce(dr.run_metadata::jsonb->>'selectivity_role', ''))
            ORDER BY mv.score::double precision, mv.docking_pose_id
        ) AS role_rank
    FROM derived.mv_pose_residue_fingerprints mv
    JOIN sources s ON s.campaign_id = mv.campaign_id
    JOIN docking_runs dr ON dr.id = mv.docking_run_id
    WHERE mv.pose_rank = 1
      AND nullif(mv.compound_id, '') IS NOT NULL
      AND lower(coalesce(dr.run_metadata::jsonb->>'selectivity_role', '')) IN ('primary', 'counter')
),
paired AS (
    SELECT
        compound_id,
        min(analysis_campaign_id::text) AS analysis_campaign_id,
        min(analysis_campaign_name) AS analysis_campaign_name,
        min(source_campaign_id::text) FILTER (WHERE target_role = 'primary' AND role_rank = 1) AS source_campaign_7b,
        min(source_campaign_id::text) FILTER (WHERE target_role = 'counter' AND role_rank = 1) AS source_campaign_7a,
        min(docking_run_id::text) FILTER (WHERE target_role = 'primary' AND role_rank = 1) AS historical_run_7b,
        min(docking_run_id::text) FILTER (WHERE target_role = 'counter' AND role_rank = 1) AS historical_run_7a,
        min(docking_pose_id::text) FILTER (WHERE target_role = 'primary' AND role_rank = 1) AS historical_pose_7b,
        min(docking_pose_id::text) FILTER (WHERE target_role = 'counter' AND role_rank = 1) AS historical_pose_7a,
        min(score) FILTER (WHERE target_role = 'primary' AND role_rank = 1) AS score_7b,
        min(score) FILTER (WHERE target_role = 'counter' AND role_rank = 1) AS score_7a
    FROM rank1
    GROUP BY compound_id
    HAVING min(score) FILTER (WHERE target_role = 'primary' AND role_rank = 1) IS NOT NULL
       AND min(score) FILTER (WHERE target_role = 'counter' AND role_rank = 1) IS NOT NULL
),
selected AS (
    SELECT p.*, p.score_7a - p.score_7b AS delta_7a_minus_7b
    FROM paired p
    WHERE p.score_7b <= -7.5
      AND p.score_7a - p.score_7b >= 1.0
)
SELECT
    row_number() OVER (ORDER BY s.delta_7a_minus_7b DESC, s.score_7b, s.compound_id) AS historical_rank,
    encode(digest(coalesce(c.inchi_key, '') || E'\n' || coalesce(c.smiles, '') || E'\n' || coalesce(c.mol_structure, ''), 'sha256'), 'hex') AS immutable_ligand_identity_sha256,
    s.compound_id AS historical_database_compound_id_provenance_only,
    c.external_id,
    c.name,
    c.smiles,
    c.inchi_key,
    s.score_7b AS historical_engine_output_7b,
    s.score_7a AS historical_engine_output_7a,
    s.delta_7a_minus_7b AS historical_delta_7a_minus_7b,
    true AS historical_216_member,
    (s.score_7b <= -7.0 AND s.delta_7a_minus_7b >= 1.5) AS alternative_116_member,
    s.analysis_campaign_id,
    s.analysis_campaign_name,
    s.source_campaign_7b,
    s.source_campaign_7a,
    s.historical_run_7b,
    s.historical_run_7a,
    s.historical_pose_7b,
    s.historical_pose_7a,
    p7b.pose_artifact_id AS historical_pose_artifact_7b,
    p7a.pose_artifact_id AS historical_pose_artifact_7a,
    a7b.storage_uri AS historical_pose_uri_7b,
    a7a.storage_uri AS historical_pose_uri_7a,
    r7b.engine AS historical_engine_7b,
    r7a.engine AS historical_engine_7a,
    r7b.run_metadata::text AS historical_run_metadata_7b,
    r7a.run_metadata::text AS historical_run_metadata_7a,
    c.compound_metadata::text AS compound_metadata,
    c.source_campaign_id AS compound_source_campaign_id
FROM selected s
JOIN compounds c ON c.id = s.compound_id
JOIN docking_poses p7b ON p7b.id::text = s.historical_pose_7b
JOIN docking_poses p7a ON p7a.id::text = s.historical_pose_7a
JOIN docking_runs r7b ON r7b.id::text = s.historical_run_7b
JOIN docking_runs r7a ON r7a.id::text = s.historical_run_7a
LEFT JOIN artifacts a7b ON a7b.id = p7b.pose_artifact_id
LEFT JOIN artifacts a7a ON a7a.id = p7a.pose_artifact_id
ORDER BY historical_rank
) TO STDOUT WITH (FORMAT csv, HEADER true);
