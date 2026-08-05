package totah.lab.athena.pocket.compare;

/**
 * How a pocket alignment was initialized.
 *
 * <ul>
 *     <li>{@link #PCA_ICP}: principal-axis coarse alignment followed by
 *     ICP refinement (the production default, no sequence
 *     evidence).</li>
 *     <li>{@link #SEQUENCE_SEEDED_KABSCH}: rigid Kabsch fit over
 *     sequence-aligned pocket residue pairs.</li>
 *     <li>{@link #SEQUENCE_SEEDED_KABSCH_ICP}: sequence-seeded Kabsch
 *     followed by ICP refinement that strictly improved the mean
 *     bidirectional distance.</li>
 * </ul>
 */
public enum AlignmentInitialization {
    PCA_ICP,
    SEQUENCE_SEEDED_KABSCH,
    SEQUENCE_SEEDED_KABSCH_ICP
}
