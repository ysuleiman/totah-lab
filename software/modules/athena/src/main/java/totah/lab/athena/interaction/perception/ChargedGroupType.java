package totah.lab.athena.interaction.perception;

/** Chemical type label of a perceived charged group. */
public enum ChargedGroupType {

    // Protein residue-name templates
    RESIDUE_ARG,
    RESIDUE_LYS,
    RESIDUE_HIS,
    RESIDUE_ASP,
    RESIDUE_GLU,

    // Bond-graph functional groups
    CARBOXYLATE,
    GUANIDINIUM,
    AMINE,
    SULFONIUM,

    /** Degraded per-residue partial-charge-sum pseudo-group. */
    CHARGE_SUM
}
