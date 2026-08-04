package totah.lab.athena.pocket.geometry;


/**
 * Coordinate representation used for pocket geometry.
 */
public enum PocketGeometryBasis {

    /**
     * Centers of fpocket alpha spheres.
     */
    ALPHA_SPHERES,

    /**
     * Heavy atoms belonging to resolved pocket residues.
     */
    RESIDUE_ATOMS,

    /**
     * A reported center without enough information for shape comparison.
     */
    REPORTED_CENTER,
    RESOLVED_RESIDUE_HEAVY_ATOMS
}
