package totah.lab.prometheus.variational;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;

class HydrogenMoleculeStreamingEvaluationTest {
    private static final ParameterVector PARAMETERS =
            new ParameterVector(List.of(0.08, -0.04, 0.03, -0.02, 0.01));

    @Test
    void batchesPreserveTheMaterializedPointsWeightsAndProvenance() {
        var batches = new HydrogenMoleculeImportanceBatches(1100, 1.4, 1.0, 17, 127);
        var expected = HydrogenMoleculeImportancePointSet.create(1100, 1.4, 1.0, 17);
        List<CollocationPointSet.WeightedPoint> actual = new ArrayList<>();
        AtomicInteger largest = new AtomicInteger();
        batches.forEachBatch(batch -> {
            largest.set(Math.max(largest.get(), batch.size()));
            actual.addAll(batch);
        });

        assertThat(actual).isEqualTo(expected.points());
        assertThat(batches.provenanceHash()).isEqualTo(expected.provenanceHash());
        assertThat(largest).hasValue(127);
        assertThat(largest.get()).isLessThanOrEqualTo(HydrogenMoleculeImportanceBatches.MAXIMUM_BATCH_SIZE);
    }

    @Test
    void streamingRayleighMatchesMaterializedAndEvaluatesOncePerPoint() {
        AtomicLong calls = new AtomicLong();
        DifferentiableQuantumState state = new CountingState(
                new HydrogenMoleculeCorrelatedState(1.4, PARAMETERS), calls);
        var batches = new HydrogenMoleculeImportanceBatches(257, 1.4, 1.0, 29, 64);
        var hamiltonian = new HydrogenMoleculeHamiltonian(1.4);

        FunctionalEvaluation streaming = new HydrogenMoleculeStreamingRayleighEvaluator()
                .evaluate(state, hamiltonian, batches);
        FunctionalEvaluation materialized = new HydrogenMoleculeRayleighFunctional().evaluate(
                new HydrogenMoleculeCorrelatedState(1.4, PARAMETERS), hamiltonian,
                HydrogenMoleculeImportancePointSet.create(257, 1.4, 1.0, 29));

        assertThat(streaming.objective()).isEqualTo(materialized.objective());
        assertThat(streaming.terms().get("local_energy_variance"))
                .isCloseTo(materialized.terms().get("local_energy_variance"),
                        org.assertj.core.data.Offset.offset(1e-12));
        assertThat(calls).hasValue(257);
        assertThat(streaming.terms().get("redundant_state_evaluations")).isZero();
    }

    @Test
    void batchedSrExactlyMatchesMaterializedSr() {
        var state = new HydrogenMoleculeCorrelatedState(1.4, PARAMETERS);
        var hamiltonian = new HydrogenMoleculeHamiltonian(1.4);
        var batches = new HydrogenMoleculeImportanceBatches(257, 1.4, 1.0, 31, 63);
        var materialized = HydrogenMoleculeImportancePointSet.create(257, 1.4, 1.0, 31);
        var optimizer = new StochasticReconfigurationOptimizer(
                new StochasticReconfigurationOptimizer.Configuration(2, 0.01, 1e-3));

        var streamed = optimizer.optimize(state, hamiltonian, batches);
        var resident = optimizer.optimize(state, hamiltonian, materialized);

        assertThat(streamed.parameters()).isEqualTo(resident.parameters());
        assertThat(streamed.energy()).isEqualTo(resident.energy());
        assertThat(streamed.localEnergyVariance()).isEqualTo(resident.localEnergyVariance());
        assertThat(streamed.energyHistory()).isEqualTo(resident.energyHistory());
        assertThat(streamed.stateEvaluations()).isEqualTo(resident.stateEvaluations());
    }

    private record CountingState(DifferentiableQuantumState delegate, AtomicLong calls)
            implements DifferentiableQuantumState {
        @Override public String representationId() { return delegate.representationId(); }
        @Override public ParameterVector parameters() { return delegate.parameters(); }
        @Override public CountingState withParameters(ParameterVector parameters) {
            return new CountingState(delegate.withParameters(parameters), calls);
        }
        @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
            calls.incrementAndGet(); return delegate.evaluateWithDerivatives(coordinates);
        }
    }
}
