package totah.lab.prometheus.reporting;

import java.util.List;
import java.util.Objects;

/** One supplied strategy-comparison row; all scientific judgments come from the caller. */
public record StrategyComparisonRow(
        String strategyId,
        String methodFamily,
        String readiness,
        List<String> reusableEvidenceHashes,
        List<String> missingEvidencePurposes,
        List<String> reasons) {

    public StrategyComparisonRow {
        strategyId = requireNonBlank(strategyId, "strategyId");
        methodFamily = requireNonBlank(methodFamily, "methodFamily");
        readiness = requireNonBlank(readiness, "readiness");
        reusableEvidenceHashes = List.copyOf(
                Objects.requireNonNull(reusableEvidenceHashes, "reusableEvidenceHashes"));
        missingEvidencePurposes = List.copyOf(
                Objects.requireNonNull(missingEvidencePurposes, "missingEvidencePurposes"));
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
