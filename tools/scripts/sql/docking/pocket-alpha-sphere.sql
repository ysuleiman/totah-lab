BEGIN;

-- Alpha spheres of fpocket pockets, parsed from the pocketN_vert.pqr
-- vertex files. sphere_index preserves the parser order within a pocket.
CREATE TABLE IF NOT EXISTS docking.pocket_alpha_sphere (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pocket_id BIGINT NOT NULL,
    sphere_index INTEGER NOT NULL,
    center_x DOUBLE PRECISION NOT NULL,
    center_y DOUBLE PRECISION NOT NULL,
    center_z DOUBLE PRECISION NOT NULL,
    radius DOUBLE PRECISION NOT NULL,

    CONSTRAINT pocket_alpha_sphere_pocket_fk
        FOREIGN KEY (pocket_id)
        REFERENCES docking.pocket (id)
        ON DELETE CASCADE,

    CONSTRAINT pocket_alpha_sphere_unique
        UNIQUE (pocket_id, sphere_index),

    CONSTRAINT pocket_alpha_sphere_coords_finite
        CHECK (
            center_x <> 'Infinity'::float8
            AND center_x <> '-Infinity'::float8
            AND center_y <> 'Infinity'::float8
            AND center_y <> '-Infinity'::float8
            AND center_z <> 'Infinity'::float8
            AND center_z <> '-Infinity'::float8
        ),

    CONSTRAINT pocket_alpha_sphere_radius_positive
        CHECK (radius > 0 AND radius <> 'Infinity'::float8)
);

CREATE INDEX IF NOT EXISTS pocket_alpha_sphere_pocket_idx
    ON docking.pocket_alpha_sphere (pocket_id);

COMMIT;
