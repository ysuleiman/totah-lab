\set ON_ERROR_STOP on

BEGIN;

ALTER TABLE docking.docking_run
    ADD COLUMN IF NOT EXISTS structure_id bigint;

UPDATE docking.docking_run run
SET structure_id = structure.id
FROM docking.structure structure
WHERE structure.receptor_id = run.receptor_id
  AND run.structure_id IS NULL;

ALTER TABLE docking.docking_run
    ALTER COLUMN structure_id SET NOT NULL;

ALTER TABLE docking.docking_run
    DROP CONSTRAINT IF EXISTS docking_run_structure_id_fkey;

ALTER TABLE docking.docking_run
    ADD CONSTRAINT docking_run_structure_id_fkey
    FOREIGN KEY (structure_id)
    REFERENCES docking.structure(id)
    ON DELETE RESTRICT;

DROP MATERIALIZED VIEW IF EXISTS
    docking.residue_contact_by_score_band;

DROP MATERIALIZED VIEW docking.pose_residue_contact CASCADE;

CREATE TABLE docking.pose_residue_contact (
    pose_id bigint NOT NULL,
    residue_id bigint NOT NULL,
    atom_contact_count integer NOT NULL,
    min_distance double precision NOT NULL,
    CONSTRAINT pose_residue_contact_pkey
        PRIMARY KEY (pose_id, residue_id),
    CONSTRAINT pose_residue_contact_pose_id_fkey
        FOREIGN KEY (pose_id)
        REFERENCES docking.docking_pose(id)
        ON DELETE CASCADE,
    CONSTRAINT pose_residue_contact_residue_id_fkey
        FOREIGN KEY (residue_id)
        REFERENCES docking.residue(id)
        ON DELETE RESTRICT,
    CONSTRAINT pose_residue_contact_atom_count_check
        CHECK (atom_contact_count > 0),
    CONSTRAINT pose_residue_contact_distance_check
        CHECK (min_distance >= 0.0 AND min_distance <= 4.0)
);

CREATE INDEX pose_residue_contact_residue_idx
    ON docking.pose_residue_contact (residue_id, pose_id);

CREATE TEMP TABLE extracted_residue_contact (
    run_id bigint NOT NULL,
    pose_file text NOT NULL,
    chain char(1) NOT NULL,
    residue_number integer NOT NULL,
    residue_name varchar NOT NULL,
    atom_contact_count integer NOT NULL,
    min_distance double precision NOT NULL
);

\copy extracted_residue_contact FROM '__METTL7B_CONTACTS__'
\copy extracted_residue_contact FROM '__METTL7A_CONTACTS__'

DO $$
DECLARE
    missing_pose_files bigint;
    missing_residues bigint;
BEGIN
    SELECT count(*) INTO missing_pose_files
    FROM (
        SELECT DISTINCT source.run_id, source.pose_file
        FROM extracted_residue_contact source
        LEFT JOIN docking.docking_pose pose
          ON pose.run_id = source.run_id
         AND pose.pose_file = source.pose_file
        WHERE pose.id IS NULL
    ) missing;
    IF missing_pose_files <> 0 THEN
        RAISE EXCEPTION '% extracted pose files do not map to docking_pose',
            missing_pose_files;
    END IF;

    SELECT count(*) INTO missing_residues
    FROM (
        SELECT DISTINCT
            source.run_id,
            source.chain,
            source.residue_number
        FROM extracted_residue_contact source
        JOIN docking.docking_run run ON run.id = source.run_id
        LEFT JOIN docking.residue residue
          ON residue.structure_id = run.structure_id
         AND residue.chain = source.chain
         AND residue.residue_number = source.residue_number
        WHERE residue.id IS NULL
    ) missing;
    IF missing_residues <> 0 THEN
        RAISE EXCEPTION '% extracted residues do not map to canonical residue',
            missing_residues;
    END IF;
END $$;

INSERT INTO docking.pose_residue_contact (
    pose_id,
    residue_id,
    atom_contact_count,
    min_distance
)
SELECT
    pose.id,
    residue.id,
    source.atom_contact_count,
    source.min_distance
FROM extracted_residue_contact source
JOIN docking.docking_run run ON run.id = source.run_id
JOIN docking.docking_pose pose
  ON pose.run_id = source.run_id
 AND pose.pose_file = source.pose_file
JOIN docking.residue residue
  ON residue.structure_id = run.structure_id
 AND residue.chain = source.chain
 AND residue.residue_number = source.residue_number;

CREATE MATERIALIZED VIEW docking.residue_contact_score_summary AS
WITH contacted_ligand AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        contact.residue_id,
        min(pose.vina_score) AS vina_score,
        min(contact.min_distance) AS min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    GROUP BY pose.run_id, pose.ligand_id, contact.residue_id
)
SELECT
    run_id,
    residue_id,
    count(*) AS contacting_ligand_count,
    avg(vina_score) AS avg_score,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY vina_score)
        AS median_score,
    min(vina_score) AS best_score,
    max(vina_score) AS worst_score,
    min(min_distance) AS closest_distance,
    avg(min_distance) AS avg_ligand_min_distance
FROM contacted_ligand
GROUP BY run_id, residue_id;

CREATE UNIQUE INDEX residue_contact_score_summary_uk
    ON docking.residue_contact_score_summary (run_id, residue_id);

CREATE MATERIALIZED VIEW docking.residue_contact_score_band_summary AS
WITH contacted_ligand AS (
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
banded AS (
    SELECT
        *,
        floor((vina_score + 6.0) / 2.0) * 2.0 - 6.0 AS score_lower
    FROM contacted_ligand
)
SELECT
    run_id,
    score_lower,
    score_lower + 2.0 AS score_upper,
    residue_id,
    count(*) AS contacting_ligand_count,
    avg(vina_score) AS avg_score,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY vina_score)
        AS median_score,
    min(vina_score) AS best_score,
    max(vina_score) AS worst_score,
    min(min_distance) AS closest_distance,
    avg(min_distance) AS avg_ligand_min_distance
FROM banded
GROUP BY run_id, score_lower, residue_id;

CREATE UNIQUE INDEX residue_contact_score_band_summary_uk
    ON docking.residue_contact_score_band_summary (
        run_id,
        score_lower,
        residue_id
    );

CREATE MATERIALIZED VIEW docking.residue_contact_by_score_band AS
WITH ligand_score AS (
    SELECT
        run_id,
        ligand_id,
        min(vina_score) AS vina_score
    FROM docking.docking_pose
    GROUP BY run_id, ligand_id
),
band_total AS (
    SELECT
        run_id,
        floor((vina_score + 6.0) / 2.0) * 2.0 - 6.0 AS score_lower,
        count(*) AS ligand_count
    FROM ligand_score
    GROUP BY run_id, score_lower
),
contacted_ligand AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        contact.residue_id,
        min(contact.min_distance) AS min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    GROUP BY pose.run_id, pose.ligand_id, contact.residue_id
),
contact_total AS (
    SELECT
        contact.run_id,
        floor((score.vina_score + 6.0) / 2.0) * 2.0 - 6.0
            AS score_lower,
        contact.residue_id,
        count(*) AS contacting_ligand_count,
        min(contact.min_distance) AS closest_distance,
        avg(contact.min_distance) AS avg_ligand_min_distance
    FROM contacted_ligand contact
    JOIN ligand_score score
      ON score.run_id = contact.run_id
     AND score.ligand_id = contact.ligand_id
    GROUP BY contact.run_id, score_lower, contact.residue_id
)
SELECT
    total.run_id,
    total.score_lower,
    total.score_lower + 2.0 AS score_upper,
    residue.id AS residue_id,
    residue.chain,
    residue.residue_number,
    residue.residue_name,
    total.ligand_count,
    COALESCE(contact.contacting_ligand_count, 0) AS contacting_ligand_count,
    COALESCE(
        contact.contacting_ligand_count::double precision
            / NULLIF(total.ligand_count, 0),
        0.0
    ) AS contacting_ligand_fraction,
    contact.closest_distance,
    contact.avg_ligand_min_distance
FROM band_total total
JOIN docking.docking_run run ON run.id = total.run_id
JOIN docking.residue residue ON residue.structure_id = run.structure_id
LEFT JOIN contact_total contact
  ON contact.run_id = total.run_id
 AND contact.score_lower = total.score_lower
 AND contact.residue_id = residue.id;

CREATE UNIQUE INDEX residue_contact_by_score_band_uk
    ON docking.residue_contact_by_score_band (
        run_id,
        score_lower,
        residue_id
    );

CREATE MATERIALIZED VIEW docking.residue_enrichment_by_receptor AS
WITH ligand_score AS (
    SELECT
        run_id,
        ligand_id,
        min(vina_score) AS vina_score
    FROM docking.docking_pose
    GROUP BY run_id, ligand_id
),
totals AS (
    SELECT
        run_id,
        count(*) AS total_ligands,
        count(*) FILTER (WHERE vina_score <= -9.0) AS total_good,
        count(*) FILTER (WHERE vina_score >= -6.0) AS total_bad
    FROM ligand_score
    GROUP BY run_id
),
contacted_ligand AS (
    SELECT
        pose.run_id,
        pose.ligand_id,
        contact.residue_id,
        min(contact.min_distance) AS min_distance
    FROM docking.pose_residue_contact contact
    JOIN docking.docking_pose pose ON pose.id = contact.pose_id
    GROUP BY pose.run_id, pose.ligand_id, contact.residue_id
),
counts AS (
    SELECT
        contact.run_id,
        contact.residue_id,
        count(*) AS total_contacts,
        count(*) FILTER (WHERE score.vina_score <= -9.0) AS good_contacts,
        count(*) FILTER (WHERE score.vina_score >= -6.0) AS bad_contacts,
        avg(score.vina_score) AS avg_score,
        percentile_cont(0.5) WITHIN GROUP (ORDER BY score.vina_score)
            AS median_score
    FROM contacted_ligand contact
    JOIN ligand_score score
      ON score.run_id = contact.run_id
     AND score.ligand_id = contact.ligand_id
    GROUP BY contact.run_id, contact.residue_id
)
SELECT
    counts.run_id,
    run.receptor_id,
    receptor.target_name,
    counts.residue_id,
    residue.chain,
    residue.residue_number,
    residue.residue_name,
    counts.total_contacts,
    counts.good_contacts,
    counts.bad_contacts,
    round(counts.avg_score::numeric, 3) AS avg_score,
    round(counts.median_score::numeric, 3) AS median_score,
    round(
        counts.total_contacts::numeric / NULLIF(totals.total_ligands, 0),
        5
    ) AS overall_contact_fraction,
    round(
        counts.good_contacts::numeric / NULLIF(totals.total_good, 0),
        5
    ) AS good_contact_fraction,
    round(
        counts.bad_contacts::numeric / NULLIF(totals.total_bad, 0),
        5
    ) AS bad_contact_fraction,
    round(
        counts.good_contacts::numeric / NULLIF(totals.total_good, 0)
            - counts.bad_contacts::numeric / NULLIF(totals.total_bad, 0),
        5
    ) AS contact_fraction_difference,
    round(
        ((counts.good_contacts + 0.5) / (totals.total_good + 1.0))
            / ((counts.bad_contacts + 0.5) / (totals.total_bad + 1.0)),
        4
    ) AS enrichment_ratio,
    round(
        log(
            2,
            ((counts.good_contacts + 0.5) / (totals.total_good + 1.0))
                / ((counts.bad_contacts + 0.5)
                    / (totals.total_bad + 1.0))
        ),
        4
    ) AS log2_enrichment
FROM counts
JOIN totals ON totals.run_id = counts.run_id
JOIN docking.docking_run run ON run.id = counts.run_id
JOIN docking.receptor receptor ON receptor.id = run.receptor_id
JOIN docking.residue residue ON residue.id = counts.residue_id;

CREATE UNIQUE INDEX residue_enrichment_by_receptor_uk
    ON docking.residue_enrichment_by_receptor (run_id, residue_id);

CREATE MATERIALIZED VIEW docking.residue_enrichment_summary AS
SELECT *
FROM docking.residue_enrichment_by_receptor;

CREATE UNIQUE INDEX residue_enrichment_summary_uk
    ON docking.residue_enrichment_summary (run_id, residue_id);

COMMIT;
