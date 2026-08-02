package totah.lab.report.config;

import java.util.List;

public record PocketReportConfiguration(
        double strongContactCutoff,
        double directContactCutoff,
        double contextCutoff,
        List<Double> distanceCutoffs
) {
    public PocketReportConfiguration {
        if (!Double.isFinite(strongContactCutoff)
                || !Double.isFinite(directContactCutoff)
                || !Double.isFinite(contextCutoff)
                || strongContactCutoff <= 0
                || strongContactCutoff > directContactCutoff
                || directContactCutoff > contextCutoff) {
            throw new IllegalArgumentException(
                    "Contact cutoffs must be finite, positive, and ordered");
        }
        distanceCutoffs = List.copyOf(distanceCutoffs);
    }

    public static PocketReportConfiguration defaults() {
        return new PocketReportConfiguration(
                4.0,
                4.5,
                6.0,
                List.of(3.0, 3.5, 4.0, 5.0)
        );
    }
}
