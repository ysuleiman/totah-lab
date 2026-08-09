package totah.lab.athena.pocket.evidence.grammar;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.evidence.EvaluationStatus;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentalStructuralVariabilityCalculatorTest {
    @Test
    void removesRigidTranslationBeforeCalculatingDispersion() {
        var first = observation("a", 0, 0);
        var translated = observation("b", 10, 0);
        var result = new ExperimentalStructuralVariabilityCalculator()
                .calculate(List.of(first, translated));
        assertEquals(EvaluationStatus.PRESENT, result.get(1).status());
        assertEquals(0.0, result.get(1).caRmsfAngstroms().orElseThrow(), 1e-8);
        assertEquals(2, result.get(1).observationCount());
    }

    @Test
    void leavesSingleObservationExplicitlyUnavailable() {
        var result = new ExperimentalStructuralVariabilityCalculator()
                .calculate(List.of(observation("a", 0, 0)));
        assertEquals(EvaluationStatus.EMPTY, result.get(1).status());
        assertTrue(result.get(1).caRmsfAngstroms().isEmpty());
    }

    private static ExperimentalCoordinateObservation observation(String id,
            double translation, double displacement) {
        return new ExperimentalCoordinateObservation(id, Map.of(
                1, coordinate(0 + translation, 0, 0),
                2, coordinate(1 + translation, 0, 0),
                3, coordinate(0 + translation, 1, 0),
                4, coordinate(1 + translation + displacement, 1, 0)));
    }

    private static ExperimentalResidueCoordinate coordinate(double x,
            double y, double z) {
        return new ExperimentalResidueCoordinate(new Point3D(x, y, z),
                Optional.empty());
    }
}
