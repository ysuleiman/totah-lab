package totah.lab.athena.tmt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TorsionProfileAssessmentTest {

    @Test
    void acceptsOnlyAProfileWithinBothErrorLimits() {
        var result = TorsionProfileAssessment.compare(
                List.of(-180.0, -60.0, 60.0, 180.0),
                List.of(0.0, 2.0, 1.0, 0.0),
                List.of(0.0, 2.1, 0.9, 0.0), 0.1, 0.11);
        assertTrue(result.withinTolerance());
    }

    @Test
    void rejectsAConvergedButEnergeticallyWrongProfile() {
        var result = TorsionProfileAssessment.compare(
                List.of(-180.0, -60.0, 60.0, 180.0),
                List.of(0.0, 2.0, 1.0, 0.0),
                List.of(0.0, 0.1, 3.5, 0.0), 0.5, 1.0);
        assertFalse(result.withinTolerance());
    }

    @Test
    void missingReferenceDataIsUnevaluatedAndCannotPass() {
        var result = TorsionProfileAssessment.unevaluated("QM_REFERENCE_NOT_AVAILABLE");
        assertFalse(result.evaluated());
        assertFalse(result.withinTolerance());
    }

    @Test
    void rejectsMismatchedAngleAndEnergyGrids() {
        assertThrows(IllegalArgumentException.class, () -> TorsionProfileAssessment.compare(
                List.of(0.0, 60.0), List.of(0.0), List.of(0.0, 1.0), 1.0, 2.0));
    }
}
