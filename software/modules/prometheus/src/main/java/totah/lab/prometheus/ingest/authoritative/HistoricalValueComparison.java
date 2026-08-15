package totah.lab.prometheus.ingest.authoritative;

import java.util.Objects;

/** Comparison of an independently recovered raw value with a historical table value. */
public record HistoricalValueComparison(
        String recordId,
        String field,
        double recoveredValue,
        double historicalValue,
        double absoluteDifference,
        boolean matchesTolerance,
        String historicalSourcePath,
        String historicalSourceSha256,
        String historicalLocator) {

    public HistoricalValueComparison {
        recordId = requireNonBlank(recordId, "recordId");
        field = requireNonBlank(field, "field");
        historicalSourcePath = requireNonBlank(historicalSourcePath, "historicalSourcePath");
        historicalSourceSha256 = requireNonBlank(historicalSourceSha256, "historicalSourceSha256");
        historicalLocator = requireNonBlank(historicalLocator, "historicalLocator");
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
