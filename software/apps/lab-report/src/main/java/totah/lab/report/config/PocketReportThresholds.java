package totah.lab.report.config;

public record PocketReportThresholds(
        double coreContactFraction,
        double frequentContactFraction,
        double variableContactFraction,
        double enrichedRatio,
        double stronglyEnrichedRatio,
        double meaningfulFilteredChange,
        long minimumFilteredLigands
) {
    public PocketReportThresholds {
        if (coreContactFraction < frequentContactFraction
                || frequentContactFraction < variableContactFraction
                || variableContactFraction < 0.0
                || coreContactFraction > 1.0
                || enrichedRatio <= 1.0
                || stronglyEnrichedRatio < enrichedRatio
                || meaningfulFilteredChange < 0.0
                || minimumFilteredLigands < 1) {
            throw new IllegalArgumentException(
                    "Pocket report thresholds are inconsistent"
            );
        }
    }

    public static PocketReportThresholds defaults() {
        return new PocketReportThresholds(
                0.90,
                0.60,
                0.20,
                1.50,
                3.00,
                0.01,
                30
        );
    }
}
