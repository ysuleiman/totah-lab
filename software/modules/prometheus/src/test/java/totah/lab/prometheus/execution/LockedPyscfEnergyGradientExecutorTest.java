package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LockedPyscfEnergyGradientExecutorTest {

    @TempDir Path temporary;

    @Test
    void refusesSpecificationOutsideExactPilotAllowlist() {
        var spec = ExecutionTestSpecs.withSoftware("PySCF");
        var executor = new LockedPyscfEnergyGradientExecutor(
                Path.of("/usr/bin/python3"), Path.of("runner.py"), temporary,
                Map.of(spec.geometry().sha256(), Path.of("geometry.xyz")), Set.of("different"));

        assertThat(executor.supports(spec)).isFalse(); // fixture asks energy only, not gradient
        assertThatThrownBy(() -> executor.execute(spec))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("external Python execution is disabled");
    }

    @Test
    void hasNarrowExecutorIdentity() {
        var executor = new LockedPyscfEnergyGradientExecutor(
                Path.of("python"), Path.of("runner.py"), temporary, Map.of(), Set.of());
        assertThat(executor.executorId()).isEqualTo("pyscf-locked-energy-gradient-pilot");
    }

    @Test
    void pythonExecutionIsPermanentlyDisabled() {
        var executor = new LockedPyscfEnergyGradientExecutor(
                Path.of("python"), Path.of("runner.py"), temporary, Map.of(), Set.of());
        assertThatThrownBy(() -> executor.execute(ExecutionTestSpecs.withSoftware("PySCF")))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("external Python execution is disabled");
    }
}
