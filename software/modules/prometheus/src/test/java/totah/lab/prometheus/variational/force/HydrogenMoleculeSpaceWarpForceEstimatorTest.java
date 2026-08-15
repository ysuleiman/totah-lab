package totah.lab.prometheus.variational.force;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.GeometryDifferentiableQuantumState;
import totah.lab.prometheus.variational.GeometryStateEvaluation;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterGradient;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumAmplitude;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.StateGradient;
import totah.lab.prometheus.variational.StateLaplacian;

class HydrogenMoleculeSpaceWarpForceEstimatorTest {
    @Test
    void estimatorIsDeterministicBoundedAndFullyInstrumented() {
        var batches = new HydrogenMoleculeImportanceBatches(257, 1.4, 1.0, 41, 53);
        var estimator = new HydrogenMoleculeSpaceWarpForceEstimator();
        var state = new GaussianState(1.4, 0.65, 0.12);
        var first = estimator.evaluate(state, new HydrogenMoleculeHamiltonian(1.4), batches);
        var second = estimator.evaluate(state, new HydrogenMoleculeHamiltonian(1.4), batches);

        assertThat(first).isEqualTo(second);
        assertThat(first.configurations()).isEqualTo(257);
        assertThat(first.stateEvaluations()).isEqualTo(5L * 257);
        assertThat(first.localEnergyEvaluations()).isEqualTo(5L * 257);
        assertThat(first.peakBatchSize()).isEqualTo(53);
        assertThat(first.numericalDerivativeStepBohr()).isEqualTo(1e-3);
        assertThat(first.derivativeImplementation()).isEqualTo("CENTRAL_NUMERICAL_EXACT_SWCT_EXPRESSION");
        assertThat(first.forceUnits()).isEqualTo("hartree/bohr");
        assertThat(first.forceHartreePerBohr()).isCloseTo(first.bareHfmForceHartreePerBohr()
                        + first.warpHfmForceHartreePerBohr() + first.pulayForceHartreePerBohr(),
                org.assertj.core.data.Offset.offset(2e-15));
        assertThat(first.forceEstimatorVarianceHartree2PerBohr2()).isFinite().isNotNegative();
    }

    @Test
    void forceMatchesNegativeDerivativeOfJacobianReweightedWarpedEnergy() {
        double radius = 1.4;
        var batches = new HydrogenMoleculeImportanceBatches(401, radius, 1.0, 37, 71);
        var state = new GaussianState(radius, 0.65, 0.12);
        var result = new HydrogenMoleculeSpaceWarpForceEstimator().evaluate(
                state, new HydrogenMoleculeHamiltonian(radius), batches);
        double plus = warpedEnergy(state, batches, radius,
                HydrogenMoleculeSpaceWarpForceEstimator.DIFFERENCE_STEP_BOHR);
        double minus = warpedEnergy(state, batches, radius,
                -HydrogenMoleculeSpaceWarpForceEstimator.DIFFERENCE_STEP_BOHR);
        double negativeDerivative = -(plus - minus)
                / (2 * HydrogenMoleculeSpaceWarpForceEstimator.DIFFERENCE_STEP_BOHR);

        assertThat(result.forceHartreePerBohr()).isCloseTo(negativeDerivative,
                org.assertj.core.data.Offset.offset(2e-5));
    }

    private static double warpedEnergy(GaussianState centerState,
            HydrogenMoleculeImportanceBatches batches, double radius, double displacement) {
        var displacedState = centerState.atGeometry(radius + displacement);
        var hamiltonian = new HydrogenMoleculeHamiltonian(radius + displacement);
        double[] sums = new double[2];
        batches.forEachBatch(batch -> batch.forEach(point -> {
            var warp = HydrogenMoleculeSpaceWarp.transform(point.coordinates(), radius, displacement);
            var evaluation = displacedState.evaluateWithDerivatives(warp.coordinates());
            double psi = evaluation.value().real();
            double weight = point.weight() * warp.jacobian() * psi * psi;
            double localEnergy = -0.5 * evaluation.coordinateLaplacian().value().real() / psi
                    + hamiltonian.potential(warp.coordinates());
            sums[0] += weight;
            sums[1] += weight * localEnergy;
        }));
        return sums[1] / sums[0];
    }

    private record GaussianState(double bondLengthBohr, double exponent, double response)
            implements GeometryDifferentiableQuantumState {
        @Override public String representationId() { return "swct-test-gaussian"; }
        @Override public ParameterVector parameters() { return new ParameterVector(List.of()); }
        @Override public GaussianState withParameters(ParameterVector parameters) {
            if (!parameters.values().isEmpty()) throw new IllegalArgumentException("no parameters");
            return this;
        }
        @Override public double geometryCoordinateBohr() { return bondLengthBohr; }
        @Override public GaussianState atGeometry(double replacement) {
            return new GaussianState(replacement, exponent, response);
        }
        @Override public GeometryStateEvaluation evaluateWithGeometryDerivatives(QuantumCoordinates coordinates) {
            double squared = squaredRadius(coordinates);
            double psi = Math.exp(-response * bondLengthBohr - exponent * squared);
            var gradients = coordinates.particles().stream().map(p -> new StateGradient.Vector3(
                    QuantumAmplitude.real(-2 * exponent * p.xBohr() * psi),
                    QuantumAmplitude.real(-2 * exponent * p.yBohr() * psi),
                    QuantumAmplitude.real(-2 * exponent * p.zBohr() * psi))).toList();
            var evaluation = new DifferentiableStateEvaluation(QuantumAmplitude.real(psi),
                    new StateGradient(gradients),
                    new StateLaplacian(QuantumAmplitude.real(
                            (4 * exponent * exponent * squared - 12 * exponent) * psi)),
                    new ParameterGradient(List.of()), CanonicalHashing.sha256Hex(
                            representationId() + "|" + bondLengthBohr + "|" + coordinates));
            return new GeometryStateEvaluation(evaluation, -response);
        }
        private static double squaredRadius(QuantumCoordinates coordinates) {
            double result = 0;
            for (var p : coordinates.particles()) {
                result += p.xBohr() * p.xBohr() + p.yBohr() * p.yBohr() + p.zBohr() * p.zBohr();
            }
            return result;
        }
    }
}
