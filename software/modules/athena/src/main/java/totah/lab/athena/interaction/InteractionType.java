package totah.lab.athena.interaction;

/**
 * Unified vocabulary of detector-backed protein-ligand interactions
 * produced by the {@code totah.lab.athena.interaction} layer.
 *
 * <p>This enum is independent of
 * {@link totah.lab.athena.ligand.interaction.InteractionType} (the legacy
 * ligand analyzer vocabulary) and of the pocket-evidence vocabulary; it is
 * the record model of the new detector layer only.
 *
 * <p>Water bridges and metal coordination are deliberately absent: PLIP
 * derives water bridges from refined H-bonds plus crystallographic waters
 * and metal complexation from metal-site perception, neither of which the
 * current perception layer provides. They will be added here only when a
 * dedicated perception exists; nothing is guessed from the existing
 * perceived sets.
 */
public enum InteractionType {

    /** Hydrogen bond, D-H...A geometry over AD4-typed donors/acceptors. */
    HYDROGEN_BOND,

    /** Opposite-sign charged-group pair, center-of-charge distance. */
    SALT_BRIDGE,

    /** Closest-pair contact between perceived hydrophobic atoms. */
    HYDROPHOBIC_CONTACT,

    /** Aromatic ring pair with (near-)parallel ring planes. */
    PI_STACK_PARALLEL,

    /** Aromatic ring pair with (near-)perpendicular ring planes. */
    PI_STACK_T_SHAPED,

    /** Positive charged group facing an aromatic ring. */
    PI_CATION,

    /** Ligand halogen donor to a protein acceptor (halocarbon rule). */
    HALOGEN_BOND
}
