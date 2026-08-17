package totah.lab.athena.tmt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class SamStereochemistryRegressionTest {

    @Test
    void requiresConfiguredSulfurToSurviveEveryPipelineStage() {
        var volumes = completeVolumes(1.0);
        assertTrue(SamStereochemistryRegression.assess(volumes, 0.05).preserved());
    }

    @Test
    void failsWhenRoundTripSerializationInvertsSulfur() {
        var volumes = completeVolumes(1.0);
        volumes.put(SamStereochemistryStage.ROUND_TRIP_SERIALIZED, -0.8);
        var result = SamStereochemistryRegression.assess(volumes, 0.05);
        assertFalse(result.preserved());
        assertTrue(result.reason().contains("ROUND_TRIP_SERIALIZED"));
    }

    @Test
    void failsClosedWhenAnyStageIsMissing() {
        var volumes = completeVolumes(1.0);
        volumes.remove(SamStereochemistryStage.MINIMIZED);
        assertFalse(SamStereochemistryRegression.assess(volumes, 0.05).preserved());
    }

    private static EnumMap<SamStereochemistryStage, Double> completeVolumes(double value) {
        var volumes = new EnumMap<SamStereochemistryStage, Double>(SamStereochemistryStage.class);
        for (SamStereochemistryStage stage : SamStereochemistryStage.values()) {
            volumes.put(stage, value);
        }
        return volumes;
    }
}
