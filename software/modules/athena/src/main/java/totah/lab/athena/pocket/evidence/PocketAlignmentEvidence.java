package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.AlignmentInitialization;

import java.util.Objects;

/**
 * Alignment evidence preserving BOTH evaluated hypotheses — the
 * production PCA+ICP hypothesis and the sequence-seeded hypothesis —
 * together with which initialization was selected and why. The losing
 * hypothesis is retained with its real metrics so downstream code can
 * inspect the discarded frame (for example the PCA frame of a
 * sequence-consistent homologue pair).
 *
 * @param pcaIcp                the PCA+ICP hypothesis (always computed)
 * @param sequenceSeeded        the sequence-seeded hypothesis, or
 *                              {@link AlignmentHypothesisEvidence#unavailable()}
 *                              when no usable seed existed
 * @param selectedInitialization which initialization the aligner
 *                              selected
 * @param selectionReason       human-readable description of the
 *                              decisive selection criterion
 */
public record PocketAlignmentEvidence(
        AlignmentHypothesisEvidence pcaIcp,
        AlignmentHypothesisEvidence sequenceSeeded,
        AlignmentInitialization selectedInitialization,
        String selectionReason
) {

    public PocketAlignmentEvidence {
        Objects.requireNonNull(pcaIcp, "pcaIcp");
        Objects.requireNonNull(sequenceSeeded, "sequenceSeeded");
        Objects.requireNonNull(
                selectedInitialization,
                "selectedInitialization"
        );
        Objects.requireNonNull(selectionReason, "selectionReason");

        if (!selectedHypothesis(pcaIcp, sequenceSeeded,
                selectedInitialization).available()) {
            throw new IllegalArgumentException(
                    "The selected hypothesis must be available"
            );
        }
    }

    /**
     * The hypothesis the aligner selected: {@link #pcaIcp()} when the
     * selected initialization is
     * {@link AlignmentInitialization#PCA_ICP}, otherwise
     * {@link #sequenceSeeded()}.
     */
    public AlignmentHypothesisEvidence selectedHypothesis() {
        return selectedHypothesis(
                pcaIcp,
                sequenceSeeded,
                selectedInitialization
        );
    }

    private static AlignmentHypothesisEvidence selectedHypothesis(
            AlignmentHypothesisEvidence pcaIcp,
            AlignmentHypothesisEvidence sequenceSeeded,
            AlignmentInitialization selectedInitialization
    ) {
        return selectedInitialization == AlignmentInitialization.PCA_ICP
                ? pcaIcp
                : sequenceSeeded;
    }
}
