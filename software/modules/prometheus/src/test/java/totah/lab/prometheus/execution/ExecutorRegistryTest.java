package totah.lab.prometheus.execution;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.planning.CalculationSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Routing by protocol software name. */
class ExecutorRegistryTest {

    @Test
    void routesToTheExecutorSupportingTheSoftware() throws EvidenceExecutionException {
        ExecutorRegistry registry = new ExecutorRegistry();
        OrcaExecutor orca = new OrcaExecutor(null);
        registry.register(orca);
        registry.register(new GaussianExecutor(null));

        EvidenceExecutor routed = registry.route(ExecutionTestSpecs.withSoftware("ORCA"));

        assertThat(routed).isSameAs(orca);
    }

    @Test
    void routingIsCaseInsensitive() throws EvidenceExecutionException {
        ExecutorRegistry registry = new ExecutorRegistry();
        registry.register(new Psi4Executor(null));

        EvidenceExecutor routed = registry.route(ExecutionTestSpecs.withSoftware("psi4"));

        assertThat(routed.executorId()).isEqualTo("psi4");
    }

    @Test
    void unknownSoftwareThrowsAndNamesTheSoftware() {
        ExecutorRegistry registry = new ExecutorRegistry();
        registry.register(new OrcaExecutor(null));
        CalculationSpecification spec = ExecutionTestSpecs.withSoftware("NWChem");

        assertThatThrownBy(() -> registry.route(spec))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("NWChem");
    }

    @Test
    void emptyRegistryNeverRoutes() {
        ExecutorRegistry registry = new ExecutorRegistry();

        assertThatThrownBy(() -> registry.route(ExecutionTestSpecs.withSoftware("ORCA")))
                .isInstanceOf(EvidenceExecutionException.class);
    }
}
