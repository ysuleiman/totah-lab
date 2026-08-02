package totah.lab.http.biohub.model;

import java.util.Objects;

public record ResidueConstraintEvidence(
        int position,
        char wildType,
        double wildTypeLogProbability,
        double meanAlternativeLogProbability,
        char bestAlternative,
        double bestAlternativeLogProbability,
        double wildTypeMinusMeanAlternative,
        double wildTypeMinusBestAlternative,
        int wildTypeRank,
        double aminoAcidEntropy
) {

    public ResidueConstraintEvidence {
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
        if (wildTypeRank < 1 || wildTypeRank > 20) {
            throw new IllegalArgumentException(
                    "wildTypeRank must be between 1 and 20"
            );
        }
        if (wildType == bestAlternative) {
            throw new IllegalArgumentException(
                    "bestAlternative must differ from wildType"
            );
        }
        requireFinite(wildTypeLogProbability, "wildTypeLogProbability");
        requireFinite(
                meanAlternativeLogProbability,
                "meanAlternativeLogProbability"
        );
        requireFinite(
                bestAlternativeLogProbability,
                "bestAlternativeLogProbability"
        );
        requireFinite(
                wildTypeMinusMeanAlternative,
                "wildTypeMinusMeanAlternative"
        );
        requireFinite(
                wildTypeMinusBestAlternative,
                "wildTypeMinusBestAlternative"
        );
        requireFinite(aminoAcidEntropy, "aminoAcidEntropy");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(
                    Objects.requireNonNull(name) + " must be finite"
            );
        }
    }
}
