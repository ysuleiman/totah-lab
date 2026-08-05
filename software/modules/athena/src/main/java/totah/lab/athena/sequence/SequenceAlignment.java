package totah.lab.athena.sequence;

import java.util.List;
import java.util.Objects;

/**
 * Result of a global protein sequence alignment.
 *
 * @param identity fraction of aligned pairs whose residue names are
 *                 identical; {@code 0.0} when there are no pairs
 * @param pairs    aligned (non-gapped) residue pairs in alignment
 *                 order
 */
public record SequenceAlignment(
        double identity,
        List<AlignedResiduePair> pairs
) {

    public SequenceAlignment {
        if (!Double.isFinite(identity)
                || identity < 0.0
                || identity > 1.0) {
            throw new IllegalArgumentException(
                    "identity must be within [0, 1]: " + identity
            );
        }

        pairs = List.copyOf(
                Objects.requireNonNull(pairs, "pairs")
        );

        if (pairs.isEmpty() && identity != 0.0) {
            throw new IllegalArgumentException(
                    "An alignment without pairs must have identity 0.0"
            );
        }
    }

    /**
     * The absent alignment: no pairs, identity {@code 0.0}. Used when
     * no sequence evidence exists for a receptor pair.
     */
    public static SequenceAlignment empty() {
        return new SequenceAlignment(0.0, List.of());
    }
}
