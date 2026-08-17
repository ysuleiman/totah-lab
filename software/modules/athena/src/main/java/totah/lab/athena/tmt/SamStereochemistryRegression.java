package totah.lab.athena.tmt;

import java.util.EnumMap;
import java.util.Map;

/** Fail-closed configured-SAM chirality evidence across every serialization stage. */
public record SamStereochemistryRegression(
        Map<SamStereochemistryStage, Double> signedVolumes,
        double minimumAbsoluteVolume,
        boolean preserved,
        String reason) {

    public SamStereochemistryRegression {
        signedVolumes = Map.copyOf(signedVolumes);
        if (!Double.isFinite(minimumAbsoluteVolume) || minimumAbsoluteVolume <= 0.0) {
            throw new IllegalArgumentException("minimumAbsoluteVolume must be positive and finite");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static SamStereochemistryRegression assess(
            Map<SamStereochemistryStage, Double> measurements, double minimumAbsoluteVolume) {
        var values = new EnumMap<SamStereochemistryStage, Double>(SamStereochemistryStage.class);
        values.putAll(measurements);
        if (values.size() != SamStereochemistryStage.values().length) {
            return new SamStereochemistryRegression(values, minimumAbsoluteVolume, false,
                    "MISSING_REQUIRED_STAGE");
        }
        double source = values.get(SamStereochemistryStage.SOURCE_FILE);
        for (SamStereochemistryStage stage : SamStereochemistryStage.values()) {
            Double value = values.get(stage);
            if (value == null || !Double.isFinite(value) || Math.abs(value) < minimumAbsoluteVolume) {
                return new SamStereochemistryRegression(values, minimumAbsoluteVolume, false,
                        "UNRESOLVED_GEOMETRY_AT_" + stage);
            }
            if (Math.signum(value) != Math.signum(source)) {
                return new SamStereochemistryRegression(values, minimumAbsoluteVolume, false,
                        "CONFIGURATION_INVERTED_AT_" + stage);
            }
        }
        return new SamStereochemistryRegression(values, minimumAbsoluteVolume, true,
                "CONFIGURATION_PRESERVED_ALL_STAGES");
    }
}
