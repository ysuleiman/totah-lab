package totah.lab.prometheus.execution;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.planning.CalculationSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The executor boundary contract: skeleton executors explicitly report the
 * unsupported state (never fake a calculation), supports() matches only their
 * own software, and an executor returns the SAME frozen specification instance
 * it received.
 */
class ExecutorContractTest {

    static Stream<Arguments> skeletonExecutors() {
        return Stream.of(
                Arguments.of(new PyscfExecutor(null), "PySCF"),
                Arguments.of(new OrcaExecutor(null), "ORCA"),
                Arguments.of(new Psi4Executor(null), "Psi4"),
                Arguments.of(new GaussianExecutor(null), "Gaussian"),
                Arguments.of(new AmberToolsExecutor(null), "AmberTools"),
                Arguments.of(new AmberToolsExecutor(null), "resp"),
                Arguments.of(new OpenMmExecutor(null), "OpenMM"));
    }

    @ParameterizedTest
    @MethodSource("skeletonExecutors")
    void executeAlwaysReportsExplicitUnsupportedState(EvidenceExecutor executor, String software) {
        CalculationSpecification spec = ExecutionTestSpecs.withSoftware(software);

        assertThatThrownBy(() -> executor.execute(spec))
                .isInstanceOf(EvidenceExecutionException.class)
                .hasMessageContaining("explicit authorization");
    }

    @ParameterizedTest
    @MethodSource("skeletonExecutors")
    void supportsMatchesOwnSoftwareAndRejectsOthers(EvidenceExecutor executor, String software) {
        assertThat(executor.supports(ExecutionTestSpecs.withSoftware(software))).isTrue();
        assertThat(executor.supports(ExecutionTestSpecs.withSoftware("NWChem"))).isFalse();
    }

    @Test
    void pyscfRejectsOrcaAndViceVersa() {
        assertThat(new PyscfExecutor(null).supports(ExecutionTestSpecs.withSoftware("ORCA")))
                .isFalse();
        assertThat(new OrcaExecutor(null).supports(ExecutionTestSpecs.withSoftware("PySCF")))
                .isFalse();
    }

    @Test
    void configuredPathDoesNotEnableExecution() {
        // skeletons never probe installations; even a configured path stays unsupported
        assertThatThrownBy(() -> new OrcaExecutor("/opt/orca")
                .execute(ExecutionTestSpecs.withSoftware("ORCA")))
                .isInstanceOf(EvidenceExecutionException.class);
    }

    @Test
    void executorReturnsTheSameFrozenSpecificationInstance() throws EvidenceExecutionException {
        // a tiny fake executor inside the test: honors the contract by returning
        // the identical specification instance it received
        EvidenceExecutor fake = new EvidenceExecutor() {
            @Override
            public String executorId() {
                return "fake";
            }

            @Override
            public boolean supports(CalculationSpecification spec) {
                return true;
            }

            @Override
            public RawCalculationResult execute(CalculationSpecification spec) {
                return new RawCalculationResult(
                        spec, List.of(), ConvergenceStatus.CONVERGED, "fake run");
            }
        };

        CalculationSpecification spec = ExecutionTestSpecs.withSoftware("FakeEngine");
        RawCalculationResult result = fake.execute(spec);

        assertThat(result.specification()).isSameAs(spec);
        assertThat(result.specification().checksum()).isEqualTo(spec.checksum());
    }
}
