package totah.lab.prometheus.variational.force;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterGradient;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumAmplitude;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.StateGradient;
import totah.lab.prometheus.variational.StateLaplacian;

class AssarafCaffarelForceEstimatorTest {
    @Test
    void eq6StreamingSufficientStatisticsEqualExactSecondPassContributions() {
        var batches = new HydrogenMoleculeImportanceBatches(113, 1.4, 1.0, 43, 19);
        var hamiltonian = new HydrogenMoleculeHamiltonian(1.4);
        var estimator = new AssarafCaffarelZvzbForceEstimator();
        var state = new CountingConstantState();
        var result = estimator.evaluate(state, hamiltonian, batches, 0);
        List<AssarafCaffarelZvzbForceEstimator.Contribution> contributions = new ArrayList<>();

        estimator.forEachContribution(new CountingConstantState(), hamiltonian, batches, 0,
                result.sampledMeanEnergyHartree(), contributions::add);

        double weight = contributions.stream().mapToDouble(c -> c.importanceWeight()).sum();
        double weightedZ = contributions.stream().mapToDouble(c -> c.importanceWeight()
                * c.forceHartreePerBohr().z()).sum();
        double weightedZ2 = contributions.stream().mapToDouble(c -> c.importanceWeight()
                * c.forceHartreePerBohr().z() * c.forceHartreePerBohr().z()).sum();
        double mean = weightedZ / weight;
        assertThat(result.rawStatistics().meanHartreePerBohr().z()).isCloseTo(mean,
                org.assertj.core.data.Offset.offset(2e-12));
        assertThat(result.rawStatistics().varianceHartree2PerBohr2().z()).isCloseTo(
                Math.max(0, weightedZ2 / weight - mean * mean),
                org.assertj.core.data.Offset.offset(2e-10));
        assertThat(result.rawStatistics().stateEvaluations()).isEqualTo(113);
        assertThat(state.evaluations()).isEqualTo(113);
        assertThat(result.estimatorEquation()).isEqualTo("Qian-2022-Eq-6");
    }

    private static final class CountingConstantState implements DifferentiableQuantumState {
        private final AtomicLong evaluations = new AtomicLong();

        long evaluations() { return evaluations.get(); }
        @Override public String representationId() { return "constant-formula-fixture"; }
        @Override public ParameterVector parameters() { return new ParameterVector(List.of()); }
        @Override public DifferentiableQuantumState withParameters(ParameterVector parameters) {
            if (!parameters.values().isEmpty()) throw new IllegalArgumentException("no fixture parameters");
            return this;
        }
        @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
            evaluations.incrementAndGet();
            var zero = QuantumAmplitude.real(0);
            var gradients = coordinates.particles().stream()
                    .map(ignored -> new StateGradient.Vector3(zero, zero, zero)).toList();
            return new DifferentiableStateEvaluation(QuantumAmplitude.real(1), new StateGradient(gradients),
                    new StateLaplacian(zero), new ParameterGradient(List.of()),
                    CanonicalHashing.sha256Hex(representationId() + "|" + coordinates));
        }
    }
}
