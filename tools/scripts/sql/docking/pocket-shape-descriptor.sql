BEGIN;

-- Precomputed Stage 1 retrieval shape descriptors, one row per pocket
-- that has persisted alpha spheres. Computed by Athena's
-- PocketShapeDescriptorFactory over the alpha-sphere centers
-- (PocketGeometryBasis.ALPHA_SPHERES, 12 radial bins) and persisted by
-- PocketShapeDescriptorService; pockets without spheres have no row.
--
-- elongation and flatness are stored in the NORMALIZED forms the Stage 1
-- retrieval distance uses (middle/major and minor/major, 0 when major is
-- 0 — both in [0, 1]), NOT the unbounded factory ratios (major/middle,
-- middle/minor). h0..h11 are the 12 radial-histogram bins (sum 1),
-- kept as separate columns so the Stage 1 ORDER BY can reference them
-- directly. descriptor_version stamps the row with
-- PocketRetrievalDistance.DESCRIPTOR_VERSION so stale rows can be
-- detected after a descriptor change.
CREATE TABLE IF NOT EXISTS docking.pocket_shape_descriptor (
    pocket_id BIGINT PRIMARY KEY,
    point_count INTEGER NOT NULL,
    radius_of_gyration DOUBLE PRECISION NOT NULL,
    extent_major DOUBLE PRECISION NOT NULL,
    extent_middle DOUBLE PRECISION NOT NULL,
    extent_minor DOUBLE PRECISION NOT NULL,
    elongation DOUBLE PRECISION NOT NULL,
    flatness DOUBLE PRECISION NOT NULL,
    h0 DOUBLE PRECISION NOT NULL,
    h1 DOUBLE PRECISION NOT NULL,
    h2 DOUBLE PRECISION NOT NULL,
    h3 DOUBLE PRECISION NOT NULL,
    h4 DOUBLE PRECISION NOT NULL,
    h5 DOUBLE PRECISION NOT NULL,
    h6 DOUBLE PRECISION NOT NULL,
    h7 DOUBLE PRECISION NOT NULL,
    h8 DOUBLE PRECISION NOT NULL,
    h9 DOUBLE PRECISION NOT NULL,
    h10 DOUBLE PRECISION NOT NULL,
    h11 DOUBLE PRECISION NOT NULL,
    descriptor_version INTEGER NOT NULL,

    CONSTRAINT pocket_shape_descriptor_pocket_fk
        FOREIGN KEY (pocket_id)
        REFERENCES docking.pocket (id)
        ON DELETE CASCADE
);

COMMIT;
