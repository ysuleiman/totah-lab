package totah.lab.athena.tmt;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearAttackAssessorTest {
    private static final NearAttackCriteria CRITERIA = new NearAttackCriteria(
            2.8, 3.2, 150.0, 180.0, 2.0, 0, "doi:10.1080/10409238.2024.2318547");

    @Test
    void computesLinearBacksideAttackUsingGaiaCoordinates() {
        NearAttackGeometry geometry = NearAttackGeometry.from(
                new Point3D(-3.0, 0.0, 0.0),
                new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.8, 0.0, 0.0),
                0);

        assertEquals(3.0, geometry.substrateSulfurToMethylCarbonAngstrom(), 1.0e-9);
        assertEquals(180.0, geometry.substrateSulfurMethylCarbonSamSulfurAngleDegrees(), 1.0e-9);
    }

    @Test
    void geometryAloneCannotBecomeChemicallyCompatible() {
        NearAttackGeometry geometry = new NearAttackGeometry(3.0, 165.0, 1.82, 0);
        NearAttackAssessment assessment = new NearAttackAssessor().assess(
                geometry, CRITERIA, false, false);

        assertEquals(NearAttackClassification.GEOMETRICALLY_NEAR_PRODUCTIVE, assessment.classification());
        assertTrue(assessment.geometryWithinCandidateRange());
        assertFalse(assessment.sulfurStateEvaluated());
    }

    @Test
    void clashFailureIsClearlyNonproductive() {
        NearAttackAssessment assessment = new NearAttackAssessor().assess(
                new NearAttackGeometry(3.0, 165.0, 1.82, 1), CRITERIA, true, true);

        assertEquals(NearAttackClassification.CLEARLY_NONPRODUCTIVE, assessment.classification());
        assertFalse(assessment.clashCompatible());
    }

    @Test
    void criteriaRequireThresholdProvenance() {
        assertThrows(IllegalArgumentException.class, () -> new NearAttackCriteria(
                2.8, 3.2, 150.0, 180.0, 2.0, 0, " "));
    }
}
