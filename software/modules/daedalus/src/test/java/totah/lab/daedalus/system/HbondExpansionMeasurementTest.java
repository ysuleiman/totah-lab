package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.SystemStateView;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.ref;
import static totah.lab.daedalus.system.ContinuousGeometryAcceptanceTest.*;

/** Raw, chemically anonymous geometry only: no proposed H-bond criterion is executed here. */
class HbondExpansionMeasurementTest {
    static JsonNode measure(double degrees, boolean transform) throws Exception {
        double radians = Math.toRadians(degrees);
        double[][] xyz = {{1, 0, 0}, {0, 0, 0},
                {2 * Math.cos(radians), 2 * Math.sin(radians), 0}, {3, 2, 1}};
        if (transform) {
            for (double[] p : xyz) {
                double x = p[0];
                p[0] = -p[1] + 7;
                p[1] = x - 3;
                p[2] += 11;
            }
        }
        var source = state(xyz);
        var plan = plan(source);
        // X0=D, X1=H, X2=A, X3=acceptor antecedent for measurement purposes only.
        op(plan, "DISTANCE", ref(1, "X0"), ref(1, "X2"));
        op(plan, "DISTANCE", ref(1, "X1"), ref(1, "X2"));
        op(plan, "ANGLE", ref(1, "X0"), ref(1, "X1"), ref(1, "X2"));
        op(plan, "ANGLE", ref(1, "X1"), ref(1, "X2"), ref(1, "X3"));
        op(plan, "ANGLE", ref(1, "X0"), ref(1, "X2"), ref(1, "X3"));
        return run(source, plan); // Existing helper checks source immutability and evidence exchange.
    }

    @ParameterizedTest
    @ValueSource(doubles = {90, 100, 120, 129.999, 130, 130.001, 180})
    void preservesMeasuredAnglesWithoutClassifyingThem(double degrees) throws Exception {
        var report = measure(degrees, false);
        near(degrees, value(report, 2, "angleDegrees"));
        near(2, value(report, 1, "distanceAngstrom"));
        near(Math.sqrt(5 - 4 * Math.cos(Math.toRadians(degrees))),
                value(report, 0, "distanceAngstrom"));
    }

    @Test
    void donorAngleAndAcceptorAnglesRemainIndependentUnderRigidMotion() throws Exception {
        var original = measure(130, false);
        var moved = measure(130, true);
        for (int i = 0; i < 5; i++) {
            String quantity = i < 2 ? "distanceAngstrom" : "angleDegrees";
            near(Double.parseDouble(value(original, i, quantity).asText()), value(moved, i, quantity));
        }
        assertNotEquals(value(original, 2, "angleDegrees"), value(original, 3, "angleDegrees"));
    }

    @Test
    void coincidentHydrogenAndAcceptorDoesNotCreateZeroAngleEvidence() throws Exception {
        var source = state(new double[][]{{1, 0, 0}, {0, 0, 0}, {0, 0, 0}});
        var plan = plan(source);
        op(plan, "ANGLE", ref(1, "X0"), ref(1, "X1"), ref(1, "X2"));
        var report = run(source, plan);
        var quantities = report.get("operations").get(0).get("quantities");
        assertFalse(quantities.has("angleDegrees"));
        var quantity = quantities.get("measurement");
        assertTrue(quantity.get("value").isNull());
        assertEquals("UNKNOWN_INCONCLUSIVE", quantity.get("status").asText());
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[0]), SystemStateView.bytes(Map.of(
                "original", measure(130, false), "rigidMotion", measure(130, true))));
    }
}
