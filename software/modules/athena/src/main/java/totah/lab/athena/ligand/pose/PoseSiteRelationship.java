package totah.lab.athena.ligand.pose;

/**
 * Structural relationship between the sites two Vina poses occupy when
 * docked against two different proteins, established by structural
 * pocket alignment (never by pocket numbering).
 *
 * <p>This is computational evidence about predicted poses: a
 * {@link #DIFFERENT_SITE} result says the two predicted poses occupy
 * structurally non-homologous pockets; it is not proof about the
 * biological binding site of either ligand.
 */
public enum PoseSiteRelationship {

    /** The assigned pockets are structurally homologous and the aligned
     *  pose centroids coincide: both predicted poses occupy the same
     *  homologous site. */
    SAME_HOMOLOGOUS_SITE,

    /** The assigned pockets are structurally homologous but the aligned
     *  pose centroids sit apart: same site family, different predicted
     *  pose. */
    HOMOLOGOUS_SITE_DIFFERENT_POSE,

    /** The assigned pockets are not structurally homologous: the
     *  predicted poses occupy different sites. */
    DIFFERENT_SITE,

    /** The evidence does not support a conclusion (for example a pose
     *  could not be assigned to a pocket unambiguously). */
    AMBIGUOUS
}
