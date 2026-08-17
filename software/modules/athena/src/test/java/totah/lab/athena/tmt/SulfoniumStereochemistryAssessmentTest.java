package totah.lab.athena.tmt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SulfoniumStereochemistryAssessmentTest {

    @Test
    void preservesResolvedSulfoniumHandednessAcrossRoundTrip() {
        assertTrue(SulfoniumStereochemistryAssessment.assess(1.25, 0.91, 0.05).preserved());
    }

    @Test
    void rejectsSulfoniumInversion() {
        var result = SulfoniumStereochemistryAssessment.assess(1.25, -0.91, 0.05);
        assertFalse(result.preserved());
        assertTrue(result.reason().contains("INVERTED"));
    }

    @Test
    void rejectsPlanarOrUnresolvedSulfoniumGeometry() {
        var result = SulfoniumStereochemistryAssessment.assess(1.25, 0.001, 0.05);
        assertFalse(result.preserved());
        assertTrue(result.reason().contains("UNRESOLVED"));
    }
}
