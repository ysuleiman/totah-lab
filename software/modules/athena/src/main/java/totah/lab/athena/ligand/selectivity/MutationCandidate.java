package totah.lab.athena.ligand.selectivity;

import java.util.Objects;

/**
 * One proposed single-point mutation candidate that would make the
 * source receptor's residue at an aligned differential-contact
 * position match the other receptor's residue.
 *
 * <p>These are mutation candidates ranked by interpretable features —
 * nothing here asserts that a position determines selectivity.
 * {@code minDistance} is the source side's minimum ligand-contact
 * distance ({@code null} when the source side has no contact);
 * {@code pocketMember} is the source side's pocket membership
 * ({@code null} when no pocket was supplied). The chemistry delta
 * features of {@code chemistry} are exposed individually; the
 * {@code tier} is the ranker's interpretable grouping, not a score.
 */
public record MutationCandidate(
        String sourceReceptor,
        MutationDirection direction,
        int alignmentPosition,
        int residueNumber,
        String wildType,
        String mutant,
        boolean contactOnSource,
        boolean contactOnOther,
        Double minDistance,
        Boolean pocketMember,
        SubstitutionChemistry chemistry,
        int tier
) {

    /**
     * Which receptor is mutated toward which: A_TO_B mutates receptor
     * A's residue into receptor B's residue at the same aligned
     * position.
     */
    public enum MutationDirection {
        A_TO_B,
        B_TO_A
    }

    public MutationCandidate {
        Objects.requireNonNull(sourceReceptor, "sourceReceptor");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(wildType, "wildType");
        Objects.requireNonNull(mutant, "mutant");
        Objects.requireNonNull(chemistry, "chemistry");

        if (sourceReceptor.isBlank()) {
            throw new IllegalArgumentException(
                    "sourceReceptor must not be blank"
            );
        }

        if (alignmentPosition < 1) {
            throw new IllegalArgumentException(
                    "alignmentPosition must be positive"
            );
        }

        if (tier < 1 || tier > 3) {
            throw new IllegalArgumentException(
                    "tier must be 1, 2 or 3"
            );
        }
    }

    /**
     * Short label such as {@code F39L}: wild-type one-letter code,
     * source residue number, mutant one-letter code.
     */
    public String label() {
        return ContactStringRenderer.oneLetter(wildType)
                + residueNumber
                + ContactStringRenderer.oneLetter(mutant);
    }
}
