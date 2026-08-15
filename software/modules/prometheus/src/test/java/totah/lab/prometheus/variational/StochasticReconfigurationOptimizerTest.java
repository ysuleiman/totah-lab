package totah.lab.prometheus.variational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;

class StochasticReconfigurationOptimizerTest {
    @Test
    void isDeterministicAndDoesNotMutateTheInitialState() {
        var parameters = new ParameterVector(List.of(0.08, -0.04, 0.03, -0.02, 0.01));
        var state = new HydrogenMoleculeCorrelatedState(1.4, parameters);
        var points = HydrogenMoleculeImportancePointSet.create(100, 1.4, 1.0, 17);
        var optimizer = new StochasticReconfigurationOptimizer(
                new StochasticReconfigurationOptimizer.Configuration(2, 0.01, 1e-3));

        var first = optimizer.optimize(state, new HydrogenMoleculeHamiltonian(1.4), points);
        var second = optimizer.optimize(state, new HydrogenMoleculeHamiltonian(1.4), points);

        assertThat(first.parameters()).isEqualTo(second.parameters());
        assertThat(first.energyHistory()).isEqualTo(second.energyHistory());
        assertThat(first.energy()).isFinite();
        assertThat(first.localEnergyVariance()).isFinite().isGreaterThanOrEqualTo(0.0);
        assertThat(first.stateEvaluations()).isEqualTo(300);
        assertThat(first.energyHistory()).hasSize(3);
        assertThat(state.parameters()).isEqualTo(parameters);
    }

    @Test
    void evaluatesTheSharedStateGraphExactlyOncePerConfiguration() {
        AtomicLong calls = new AtomicLong();
        DifferentiableQuantumState state = new CountingState(
                new HydrogenMoleculeCorrelatedState(1.4,
                        new ParameterVector(List.of(0.08, -0.04, 0.03, -0.02, 0.01))), calls);
        var points = HydrogenMoleculeImportancePointSet.create(100, 1.4, 1.0, 23);
        var optimizer = new StochasticReconfigurationOptimizer(
                new StochasticReconfigurationOptimizer.Configuration(1, 0.005, 1e-2));

        var result = optimizer.optimize(state, new HydrogenMoleculeHamiltonian(1.4), points);

        assertThat(calls).hasValue(200);
        assertThat(result.stateEvaluations()).isEqualTo(calls.get());
    }

    @Test
    void rejectsAnUnregularizedSolve() {
        assertThatThrownBy(() -> new StochasticReconfigurationOptimizer.Configuration(1, 0.01, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diagonalRegularization");
    }

    private record CountingState(DifferentiableQuantumState delegate, AtomicLong calls)
            implements DifferentiableQuantumState {
        @Override public String representationId() { return delegate.representationId(); }
        @Override public ParameterVector parameters() { return delegate.parameters(); }
        @Override public CountingState withParameters(ParameterVector parameters) {
            return new CountingState(delegate.withParameters(parameters), calls);
        }
        @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
            calls.incrementAndGet();
            return delegate.evaluateWithDerivatives(coordinates);
        }
    }
}
