package totah.lab.athena.pocket.evidence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PocketAssessmentTest {

    @Test
    void carriesVerdictReasonsAndRulesetWithoutAScore() {
        AssessmentReason reason = new AssessmentReason(
                "GEOMETRY_ACCEPTABLE", "GEOMETRY",
                "Geometry passed its independent threshold",
                Map.of("similarity", "0.72", "threshold", "0.60"));

        PocketAssessment<PocketComparisonAssessment> assessment =
                new PocketAssessment<>(
                        PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                        List.of(reason), "comparison-rules/1");

        assertEquals(PocketComparisonAssessment.PROBABLE_FUNCTIONAL_MATCH,
                assessment.verdict());
        assertEquals(List.of(reason), assessment.reasons());
        assertEquals("comparison-rules/1", assessment.rulesetVersion());
    }

    @Test
    void rejectsReasonlessOrUnversionedJudgments() {
        assertThrows(IllegalArgumentException.class,
                () -> new PocketAssessment<>(
                        PocketComparisonAssessment.REJECTED,
                        List.of(), "rules/1"));
        assertThrows(IllegalArgumentException.class,
                () -> new PocketAssessment<>(
                        PocketComparisonAssessment.REJECTED,
                        List.of(new AssessmentReason(
                                "REJECTED", "GEOMETRY", "failed")), " "));
    }
}
