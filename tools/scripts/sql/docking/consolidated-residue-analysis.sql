BEGIN;

DROP MATERIALIZED VIEW IF EXISTS docking.residue_enrichment_summary;
DROP MATERIALIZED VIEW IF EXISTS docking.residue_enrichment_by_receptor;
DROP MATERIALIZED VIEW IF EXISTS docking.residue_contact_by_score_band;
DROP MATERIALIZED VIEW IF EXISTS docking.residue_contact_score_band_summary;
DROP MATERIALIZED VIEW IF EXISTS docking.residue_contact_score_summary;
DROP MATERIALIZED VIEW IF EXISTS docking.docking_run_residue_summary;
DROP MATERIALIZED VIEW IF EXISTS
    docking.docking_run_residue_score_band_summary;

CREATE MATERIALIZED VIEW docking.docking_run_residue_summary AS
WITH analysis_parameter AS (
    SELECT -5.0::double precision AS contact_score_threshold
),
ligand_score AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        min(pose.vina_score) AS vina_score
    FROM docking.docking_pose pose
    GROUP BY pose.run_id, pose.ligand_id
),
pose_total AS (
    SELECT
        pose.run_id,
        count(*) AS total_pose_count,
        count(*) FILTER (
            WHERE pose.vina_score
                < parameter.contact_score_threshold
        ) AS score_filtered_pose_count
    FROM docking.docking_pose pose
    CROSS JOIN analysis_parameter parameter
    GROUP BY pose.run_id
),
run_totals AS (
    SELECT
        score.run_id,
        pose.total_pose_count,
        pose.score_filtered_pose_count,
        count(*) AS total_ligand_count,
        count(*) FILTER (
            WHERE score.vina_score
                < parameter.contact_score_threshold
        ) AS score_filtered_ligand_count,
        count(*) FILTER (WHERE score.vina_score <= -9.0)
            AS total_good_ligand_count,
        count(*) FILTER (WHERE score.vina_score >= -6.0)
            AS total_bad_ligand_count
    FROM ligand_score score
    JOIN pose_total pose ON pose.run_id = score.run_id
    CROSS JOIN analysis_parameter parameter
    GROUP BY
        score.run_id,
        pose.total_pose_count,
        pose.score_filtered_pose_count
),
contacted_pose AS (
    SELECT
        pose.run_id,
        contact.residue_id,
        count(*) AS contacting_pose_count,
        count(*) FILTER (
            WHERE pose.vina_score
                < parameter.contact_score_threshold
        ) AS score_filtered_contacting_pose_count,
        min(contact.min_distance) AS closest_distance,
        avg(contact.min_distance) AS avg_pose_min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    CROSS JOIN analysis_parameter parameter
    GROUP BY pose.run_id, contact.residue_id
),
contacted_ligand AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        contact.residue_id,
        min(pose.vina_score) AS vina_score,
        min(contact.min_distance) AS min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    GROUP BY pose.run_id, pose.ligand_id, contact.residue_id
),
contacted_ligand_summary AS (
    SELECT
        contact.run_id,
        contact.residue_id,
        count(*) AS contacting_ligand_count,
        count(*) FILTER (
            WHERE contact.vina_score
                < parameter.contact_score_threshold
        ) AS score_filtered_contacting_ligand_count,
        count(*) FILTER (WHERE contact.vina_score <= -9.0)
            AS good_contacting_ligand_count,
        count(*) FILTER (WHERE contact.vina_score >= -6.0)
            AS bad_contacting_ligand_count,
        avg(contact.vina_score) AS avg_contacting_score,
        percentile_cont(0.5) WITHIN GROUP (ORDER BY contact.vina_score)
            AS median_contacting_score,
        min(contact.vina_score) AS best_contacting_score,
        max(contact.vina_score) AS worst_contacting_score,
        min(contact.min_distance) AS closest_distance,
        avg(contact.min_distance) AS avg_ligand_min_distance
    FROM contacted_ligand contact
    CROSS JOIN analysis_parameter parameter
    GROUP BY contact.run_id, contact.residue_id
)
SELECT
    run.id AS run_id,
    run.structure_id,
    run.receptor_id,
    residue.id AS residue_id,
    residue.chain,
    residue.residue_number,
    residue.residue_name,
    parameter.contact_score_threshold,
    totals.score_filtered_ligand_count,
    COALESCE(ligand.score_filtered_contacting_ligand_count, 0)
        AS score_filtered_contacting_ligand_count,
    COALESCE(
        ligand.score_filtered_contacting_ligand_count::double precision
            / NULLIF(totals.score_filtered_ligand_count, 0),
        0.0
    ) AS score_filtered_contacting_ligand_fraction,
    totals.score_filtered_pose_count,
    COALESCE(pose.score_filtered_contacting_pose_count, 0)
        AS score_filtered_contacting_pose_count,
    COALESCE(
        pose.score_filtered_contacting_pose_count::double precision
            / NULLIF(totals.score_filtered_pose_count, 0),
        0.0
    ) AS score_filtered_contacting_pose_fraction,
    totals.total_ligand_count,
    COALESCE(ligand.contacting_ligand_count, 0) AS contacting_ligand_count,
    COALESCE(
        ligand.contacting_ligand_count::double precision
            / NULLIF(totals.total_ligand_count, 0),
        0.0
    ) AS contacting_ligand_fraction,
    totals.total_pose_count,
    COALESCE(pose.contacting_pose_count, 0) AS contacting_pose_count,
    COALESCE(
        pose.contacting_pose_count::double precision
            / NULLIF(totals.total_pose_count, 0),
        0.0
    ) AS contacting_pose_fraction,
    totals.total_good_ligand_count,
    COALESCE(ligand.good_contacting_ligand_count, 0)
        AS good_contacting_ligand_count,
    COALESCE(
        ligand.good_contacting_ligand_count::double precision
            / NULLIF(totals.total_good_ligand_count, 0),
        0.0
    ) AS good_contacting_ligand_fraction,
    totals.total_bad_ligand_count,
    COALESCE(ligand.bad_contacting_ligand_count, 0)
        AS bad_contacting_ligand_count,
    COALESCE(
        ligand.bad_contacting_ligand_count::double precision
            / NULLIF(totals.total_bad_ligand_count, 0),
        0.0
    ) AS bad_contacting_ligand_fraction,
    COALESCE(
        ligand.good_contacting_ligand_count::double precision
            / NULLIF(totals.total_good_ligand_count, 0),
        0.0
    ) - COALESCE(
        ligand.bad_contacting_ligand_count::double precision
            / NULLIF(totals.total_bad_ligand_count, 0),
        0.0
    ) AS contact_fraction_difference,
    CASE
        WHEN totals.total_good_ligand_count = 0
            OR totals.total_bad_ligand_count = 0
        THEN NULL
        ELSE
            ((COALESCE(ligand.good_contacting_ligand_count, 0) + 0.5)
                / (totals.total_good_ligand_count + 1.0))
            / ((COALESCE(ligand.bad_contacting_ligand_count, 0) + 0.5)
                / (totals.total_bad_ligand_count + 1.0))
    END AS enrichment_ratio,
    CASE
        WHEN totals.total_good_ligand_count = 0
            OR totals.total_bad_ligand_count = 0
        THEN NULL
        ELSE log(
            2,
            ((COALESCE(ligand.good_contacting_ligand_count, 0) + 0.5)
                / (totals.total_good_ligand_count + 1.0))
            / ((COALESCE(ligand.bad_contacting_ligand_count, 0) + 0.5)
                / (totals.total_bad_ligand_count + 1.0))
        )
    END AS log2_enrichment,
    ligand.avg_contacting_score,
    ligand.median_contacting_score,
    ligand.best_contacting_score,
    ligand.worst_contacting_score,
    COALESCE(ligand.closest_distance, pose.closest_distance)
        AS closest_distance,
    ligand.avg_ligand_min_distance,
    pose.avg_pose_min_distance
FROM docking.docking_run run
JOIN run_totals totals ON totals.run_id = run.id
JOIN docking.residue residue ON residue.structure_id = run.structure_id
CROSS JOIN analysis_parameter parameter
LEFT JOIN contacted_ligand_summary ligand
  ON ligand.run_id = run.id
 AND ligand.residue_id = residue.id
LEFT JOIN contacted_pose pose
  ON pose.run_id = run.id
 AND pose.residue_id = residue.id;

CREATE UNIQUE INDEX docking_run_residue_summary_uk
    ON docking.docking_run_residue_summary (run_id, residue_id);

CREATE INDEX docking_run_residue_summary_fraction_idx
    ON docking.docking_run_residue_summary (
        run_id,
        contacting_ligand_fraction DESC
    );

CREATE MATERIALIZED VIEW docking.docking_run_residue_score_band_summary AS
WITH ligand_score AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        min(pose.vina_score) AS vina_score
    FROM docking.docking_pose pose
    GROUP BY pose.run_id, pose.ligand_id
),
ligand_band_total AS (
    SELECT
        score.run_id,
        floor((score.vina_score + 6.0) / 2.0) * 2.0 - 6.0
            AS score_lower,
        count(*) AS ligand_count
    FROM ligand_score score
    GROUP BY score.run_id, score_lower
),
pose_band_total AS (
    SELECT
        pose.run_id,
        floor((pose.vina_score + 6.0) / 2.0) * 2.0 - 6.0
            AS score_lower,
        count(*) AS pose_count
    FROM docking.docking_pose pose
    GROUP BY pose.run_id, score_lower
),
contacted_ligand AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        contact.residue_id,
        min(pose.vina_score) AS vina_score,
        min(contact.min_distance) AS min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    GROUP BY pose.run_id, pose.ligand_id, contact.residue_id
),
contacted_ligand_band AS (
    SELECT
        contact.run_id,
        floor((contact.vina_score + 6.0) / 2.0) * 2.0 - 6.0
            AS score_lower,
        contact.residue_id,
        count(*) AS contacting_ligand_count,
        avg(contact.vina_score) AS avg_contacting_score,
        percentile_cont(0.5) WITHIN GROUP (ORDER BY contact.vina_score)
            AS median_contacting_score,
        min(contact.vina_score) AS best_contacting_score,
        max(contact.vina_score) AS worst_contacting_score,
        min(contact.min_distance) AS closest_distance,
        avg(contact.min_distance) AS avg_ligand_min_distance
    FROM contacted_ligand contact
    GROUP BY contact.run_id, score_lower, contact.residue_id
),
contacted_pose_band AS (
    SELECT
        pose.run_id,
        floor((pose.vina_score + 6.0) / 2.0) * 2.0 - 6.0
            AS score_lower,
        contact.residue_id,
        count(*) AS contacting_pose_count,
        avg(contact.min_distance) AS avg_pose_min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    GROUP BY pose.run_id, score_lower, contact.residue_id
)
SELECT
    run.id AS run_id,
    run.structure_id,
    run.receptor_id,
    ligand_total.score_lower,
    ligand_total.score_lower + 2.0 AS score_upper,
    residue.id AS residue_id,
    residue.chain,
    residue.residue_number,
    residue.residue_name,
    ligand_total.ligand_count,
    COALESCE(ligand.contacting_ligand_count, 0)
        AS contacting_ligand_count,
    COALESCE(
        ligand.contacting_ligand_count::double precision
            / NULLIF(ligand_total.ligand_count, 0),
        0.0
    ) AS contacting_ligand_fraction,
    pose_total.pose_count,
    COALESCE(pose.contacting_pose_count, 0) AS contacting_pose_count,
    COALESCE(
        pose.contacting_pose_count::double precision
            / NULLIF(pose_total.pose_count, 0),
        0.0
    ) AS contacting_pose_fraction,
    ligand.avg_contacting_score,
    ligand.median_contacting_score,
    ligand.best_contacting_score,
    ligand.worst_contacting_score,
    ligand.closest_distance,
    ligand.avg_ligand_min_distance,
    pose.avg_pose_min_distance
FROM ligand_band_total ligand_total
JOIN pose_band_total pose_total
  ON pose_total.run_id = ligand_total.run_id
 AND pose_total.score_lower = ligand_total.score_lower
JOIN docking.docking_run run ON run.id = ligand_total.run_id
JOIN docking.residue residue ON residue.structure_id = run.structure_id
LEFT JOIN contacted_ligand_band ligand
  ON ligand.run_id = run.id
 AND ligand.score_lower = ligand_total.score_lower
 AND ligand.residue_id = residue.id
LEFT JOIN contacted_pose_band pose
  ON pose.run_id = run.id
 AND pose.score_lower = ligand_total.score_lower
 AND pose.residue_id = residue.id;

CREATE UNIQUE INDEX docking_run_residue_score_band_summary_uk
    ON docking.docking_run_residue_score_band_summary (
        run_id,
        score_lower,
        residue_id
    );

CREATE INDEX docking_run_residue_score_band_fraction_idx
    ON docking.docking_run_residue_score_band_summary (
        run_id,
        residue_id,
        score_lower
    );

COMMIT;
