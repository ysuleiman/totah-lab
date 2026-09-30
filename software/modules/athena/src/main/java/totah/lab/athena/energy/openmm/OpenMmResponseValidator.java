package totah.lab.athena.energy.openmm;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.energy.EnergyEvaluationException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One canonical structural and numerical validator for OpenMM receipts. */
public final class OpenMmResponseValidator {
    private OpenMmResponseValidator() {}

    public static void validateEnergy(JsonNode response, Set<Integer> groups,
            String platform, Map<String, String> properties)
            throws EnergyEvaluationException {
        common(response, groups, platform, properties, false, -1);
    }

    public static void validateMinimization(JsonNode response, Set<Integer> groups,
            String platform, Map<String, String> properties, int particleCount)
            throws EnergyEvaluationException {
        common(response, groups, platform, properties, true, particleCount);
        finite(response, "preMinimizationTotalKilojoulesPerMole");
        groupEnergies(response.required("preMinimizationForceGroupKilojoulesPerMole"), groups);
        double maximumForce = finite(response, "maximumForceKilojoulesPerMoleNanometre");
        if (maximumForce < 0.0) fail("negative maximum force");
        JsonNode positions = response.required("positionsNanometres");
        if (!positions.isArray() || positions.size() != particleCount) {
            fail("minimized coordinate count differs from mapped particle count");
        }
        for (JsonNode xyz : positions) {
            if (!xyz.isArray() || xyz.size() != 3) fail("coordinate is not an xyz triplet");
            for (JsonNode coordinate : xyz) {
                if (!coordinate.isNumber() || !Double.isFinite(coordinate.asDouble())) {
                    fail("nonfinite/non-numeric minimized coordinate");
                }
            }
        }
    }

    private static void common(JsonNode response, Set<Integer> groups,
            String platform, Map<String, String> properties, boolean minimized,
            int particleCount) throws EnergyEvaluationException {
        if (response == null || !response.isObject()) fail("OpenMM receipt is not an object");
        text(response, "openMmVersion");
        if (!platform.equals(text(response, "platform"))) fail("OpenMM receipt platform mismatch");
        JsonNode actualProperties = response.required("platformProperties");
        for (Map.Entry<String, String> expected : properties.entrySet()) {
            if (!expected.getValue().equals(actualProperties.path(expected.getKey()).asText(null))) {
                fail("OpenMM platform property mismatch for " + expected.getKey());
            }
        }
        finite(response, "totalKilojoulesPerMole");
        groupEnergies(response.required("forceGroupKilojoulesPerMole"), groups);
        if (!response.has("minimized") || response.required("minimized").asBoolean() != minimized) {
            fail("OpenMM minimized flag mismatch");
        }
        if (!response.has("converged") || !response.required("converged").isBoolean()) {
            fail("OpenMM convergence flag missing or non-boolean");
        }
    }

    private static void groupEnergies(JsonNode node, Set<Integer> expected)
            throws EnergyEvaluationException {
        if (!node.isObject()) fail("force-group energies are not an object");
        Set<Integer> actual = new HashSet<>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            try {
                actual.add(Integer.parseInt(entry.getKey()));
            } catch (NumberFormatException exception) {
                fail("non-integer force-group key " + entry.getKey());
            }
            if (!entry.getValue().isNumber() || !Double.isFinite(entry.getValue().asDouble())) {
                fail("nonfinite force-group energy " + entry.getKey());
            }
        }
        if (!actual.equals(expected)) fail("force-group receipt keys differ from declaration");
    }

    private static double finite(JsonNode response, String field)
            throws EnergyEvaluationException {
        JsonNode value = response.required(field);
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) fail("nonfinite/missing " + field);
        return value.asDouble();
    }

    private static String text(JsonNode response, String field)
            throws EnergyEvaluationException {
        String value = response.path(field).asText("").trim();
        if (value.isEmpty()) fail("missing " + field);
        return value;
    }

    private static void fail(String message) throws EnergyEvaluationException {
        throw new EnergyEvaluationException(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                "invalid OpenMM receipt: " + message);
    }
}
