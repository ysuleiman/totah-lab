package totah.lab.athena.energy.openmm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.athena.energy.EnergyEvaluationException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMmResponseValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsWrongPlatformNonfiniteEnergyAndMissingGroup() throws Exception {
        String base = """
                {"openMmVersion":"8.5.2","platform":"%s",
                "platformProperties":{"Threads":"8","DeterministicForces":"true"},
                "totalKilojoulesPerMole":%s,"forceGroupKilojoulesPerMole":%s,
                "minimized":false,"converged":true}
                """;
        assertThatThrownBy(() -> OpenMmResponseValidator.validateEnergy(
                mapper.readTree(base.formatted("CUDA", "1", "{\"1\":1}")), Set.of(1),
                "CPU", properties())).isInstanceOf(EnergyEvaluationException.class)
                .hasMessageContaining("platform mismatch");
        assertThatThrownBy(() -> OpenMmResponseValidator.validateEnergy(
                mapper.readTree(base.formatted("CPU", "\"NaN\"", "{\"1\":1}")), Set.of(1),
                "CPU", properties())).hasMessageContaining("nonfinite");
        assertThatThrownBy(() -> OpenMmResponseValidator.validateEnergy(
                mapper.readTree(base.formatted("CPU", "1", "{}")), Set.of(1),
                "CPU", properties())).hasMessageContaining("keys differ");
    }

    @Test
    void rejectsMalformedMinimizedCoordinatesAndWrongOperationFlag() throws Exception {
        var response = mapper.readTree("""
                {"openMmVersion":"8.5.2","platform":"CPU",
                "platformProperties":{"Threads":"8","DeterministicForces":"true"},
                "totalKilojoulesPerMole":1,"forceGroupKilojoulesPerMole":{"1":1},
                "preMinimizationTotalKilojoulesPerMole":2,
                "preMinimizationForceGroupKilojoulesPerMole":{"1":2},
                "maximumForceKilojoulesPerMoleNanometre":1,
                "positionsNanometres":[[0,0]],"minimized":true,"converged":true}
                """);
        assertThatThrownBy(() -> OpenMmResponseValidator.validateMinimization(response,
                Set.of(1), "CPU", properties(), 1)).hasMessageContaining("xyz triplet");
        ((com.fasterxml.jackson.databind.node.ObjectNode) response).put("minimized", false);
        assertThatThrownBy(() -> OpenMmResponseValidator.validateMinimization(response,
                Set.of(1), "CPU", properties(), 1)).hasMessageContaining("minimized flag");
    }

    private static Map<String, String> properties() {
        return Map.of("Threads", "8", "DeterministicForces", "true");
    }
}
