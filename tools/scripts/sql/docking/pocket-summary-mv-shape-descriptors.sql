BEGIN;

-- Adds the precomputed Stage 1 shape-descriptor columns to
-- docking.pocket_summary_mv via a LEFT JOIN on pocket_shape_descriptor
-- (rows without a descriptor keep NULLs and sort last in the Stage 1
-- retrieval ORDER BY). The view is dropped and recreated because
-- materialized views cannot gain columns in place; the definition is the
-- pre-existing one (see pg_get_viewdef('docking.pocket_summary_mv') and
-- pocket-summary-mv-alpha-spheres.sql) plus the descriptor join.
-- The eligibility WHERE and the four pocket_summary_mv_* indexes are
-- unchanged.
DROP MATERIALIZED VIEW IF EXISTS docking.pocket_summary_mv;

CREATE MATERIALIZED VIEW docking.pocket_summary_mv AS
WITH residue_stats AS (
    SELECT
        pr.pocket_id,
        count(DISTINCT pr.residue_id) AS residue_count,
        count(DISTINCT pr.residue_id)
            FILTER (WHERE r.residue_name = 'CYS') AS cysteine_count,
        count(DISTINCT pr.residue_id)
            FILTER (WHERE r.residue_name IN ('PHE', 'TYR', 'TRP'))
            AS aromatic_count,
        count(DISTINCT pr.residue_id)
            FILTER (WHERE r.residue_name IN
                ('ALA', 'VAL', 'LEU', 'ILE', 'MET', 'PRO'))
            AS hydrophobic_count,
        count(DISTINCT pr.residue_id)
            FILTER (WHERE r.residue_name IN
                ('SER', 'THR', 'ASN', 'GLN'))
            AS polar_count,
        count(DISTINCT pr.residue_id)
            FILTER (WHERE r.residue_name IN ('ASP', 'GLU'))
            AS negative_count,
        count(DISTINCT pr.residue_id)
            FILTER (WHERE r.residue_name IN ('LYS', 'ARG', 'HIS'))
            AS positive_count
    FROM docking.pocket_residue pr
    JOIN docking.residue r
        ON r.id = pr.residue_id
    GROUP BY pr.pocket_id
),
atom_stats AS (
    SELECT
        pr.pocket_id,
        count(pa.id) AS atom_count
    FROM docking.pocket_residue pr
    JOIN docking.pocket_atom pa
        ON pa.pocket_residue_id = pr.id
    GROUP BY pr.pocket_id
),
sphere_stats AS (
    SELECT
        pas.pocket_id,
        count(pas.id) AS alpha_sphere_count
    FROM docking.pocket_alpha_sphere pas
    GROUP BY pas.pocket_id
),
summary AS (
    SELECT
        p.id AS pocket_id,
        p.structure_id,
        p.pocket_number,
        p.source,
        p.volume,
        p.score,
        p.druggability_score,
        s.receptor_id,
        s.source AS structure_source,
        s.source_accession,
        r.uniprot_id,
        r.target_name,
        r.protein_name,
        r.gene_name,
        r.organism,
        COALESCE(rs.residue_count, 0::bigint) AS residue_count,
        COALESCE(a.atom_count, 0::bigint) AS atom_count,
        COALESCE(sph.alpha_sphere_count, 0::bigint) AS alpha_sphere_count,
        COALESCE(rs.cysteine_count, 0::bigint) AS cysteine_count,
        COALESCE(rs.aromatic_count, 0::bigint) AS aromatic_count,
        COALESCE(rs.hydrophobic_count, 0::bigint) AS hydrophobic_count,
        COALESCE(rs.polar_count, 0::bigint) AS polar_count,
        COALESCE(rs.negative_count, 0::bigint) AS negative_count,
        COALESCE(rs.positive_count, 0::bigint) AS positive_count,
        psd.point_count AS shape_point_count,
        psd.radius_of_gyration,
        psd.extent_major,
        psd.extent_middle,
        psd.extent_minor,
        psd.elongation,
        psd.flatness,
        psd.h0,
        psd.h1,
        psd.h2,
        psd.h3,
        psd.h4,
        psd.h5,
        psd.h6,
        psd.h7,
        psd.h8,
        psd.h9,
        psd.h10,
        psd.h11,
        psd.descriptor_version
    FROM docking.pocket p
    JOIN docking.structure s
        ON s.id = p.structure_id
    JOIN docking.receptor r
        ON r.id = s.receptor_id
    LEFT JOIN residue_stats rs
        ON rs.pocket_id = p.id
    LEFT JOIN atom_stats a
        ON a.pocket_id = p.id
    LEFT JOIN sphere_stats sph
        ON sph.pocket_id = p.id
    LEFT JOIN docking.pocket_shape_descriptor psd
        ON psd.pocket_id = p.id
)
SELECT
    summary.pocket_id,
    summary.structure_id,
    summary.pocket_number,
    summary.source,
    summary.volume,
    summary.score,
    summary.druggability_score,
    summary.receptor_id,
    summary.structure_source,
    summary.source_accession,
    summary.uniprot_id,
    summary.target_name,
    summary.protein_name,
    summary.gene_name,
    summary.organism,
    summary.residue_count,
    summary.atom_count,
    summary.alpha_sphere_count,
    CASE
        WHEN summary.alpha_sphere_count > 0 THEN 'ALPHA_SPHERES'
        WHEN summary.atom_count >= 20 THEN 'RESIDUE_ATOMS'
        ELSE 'NONE'
    END AS geometry_basis,
    summary.cysteine_count,
    summary.aromatic_count,
    summary.hydrophobic_count,
    summary.polar_count,
    summary.negative_count,
    summary.positive_count,
    summary.cysteine_count::double precision
        / NULLIF(summary.residue_count, 0)::double precision
        AS cysteine_fraction,
    summary.aromatic_count::double precision
        / NULLIF(summary.residue_count, 0)::double precision
        AS aromatic_fraction,
    summary.hydrophobic_count::double precision
        / NULLIF(summary.residue_count, 0)::double precision
        AS hydrophobic_fraction,
    summary.polar_count::double precision
        / NULLIF(summary.residue_count, 0)::double precision
        AS polar_fraction,
    summary.negative_count::double precision
        / NULLIF(summary.residue_count, 0)::double precision
        AS negative_fraction,
    summary.positive_count::double precision
        / NULLIF(summary.residue_count, 0)::double precision
        AS positive_fraction,
    summary.shape_point_count,
    summary.radius_of_gyration,
    summary.extent_major,
    summary.extent_middle,
    summary.extent_minor,
    summary.elongation,
    summary.flatness,
    summary.h0,
    summary.h1,
    summary.h2,
    summary.h3,
    summary.h4,
    summary.h5,
    summary.h6,
    summary.h7,
    summary.h8,
    summary.h9,
    summary.h10,
    summary.h11,
    summary.descriptor_version
FROM summary
WHERE summary.volume >= 100::double precision
  AND summary.residue_count >= 8
  AND (summary.alpha_sphere_count > 0 OR summary.atom_count >= 20);

CREATE UNIQUE INDEX pocket_summary_mv_pk
    ON docking.pocket_summary_mv (pocket_id);

CREATE INDEX pocket_summary_mv_prefilter_idx
    ON docking.pocket_summary_mv (volume, residue_count, cysteine_count);

CREATE INDEX pocket_summary_mv_source_accession_idx
    ON docking.pocket_summary_mv (source_accession);

CREATE INDEX pocket_summary_mv_composition_idx
    ON docking.pocket_summary_mv (
        hydrophobic_fraction,
        aromatic_fraction,
        polar_fraction
    );

REFRESH MATERIALIZED VIEW docking.pocket_summary_mv;

COMMIT;
