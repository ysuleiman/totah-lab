package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetMatrixFreeSrOptimizerTest {

    @Test
    void streamingSrMatchesIndependentDenseReference() {
        Fixture fixture = fixture();
        int p = fixture.state.parameterCount();
        DenseReference dense = denseReference(fixture.state, fixture.samples, .2);
        double[] probe = new double[p];
        for (int i = 0; i < p; i++) probe[i] = Math.sin(.17 * (i + 1));
        double[] denseAction = multiply(dense.system, probe);
        var optimizer = new FermiNetMatrixFreeSrOptimizer();
        double[] streamingAction = optimizer.covarianceAction(
                fixture.state, fixture.samples, .2, probe);
        double actionError = maxError(denseAction, streamingAction);

        var configuration = new FermiNetMatrixFreeSrOptimizer.Configuration(
                .03, .2, 100.0, 1, 64, 300, 1e-11, 1e-12);
        var result = optimizer.oneIteration(fixture.state, fixture.samples, configuration);
        double gradientError = maxError(dense.gradient, result.energyGradient());
        double[] denseDelta = conjugateGradient(dense.system, negate(dense.gradient), 1e-12, 20);
        double[] expectedUpdate = scale(denseDelta, configuration.learningRate());
        double[] actualUpdate = difference(result.state().parameterArray(),
                fixture.state.parameterArray());
        double updateError = maxError(expectedUpdate, actualUpdate);

        System.out.printf("""
                FERMINET_CACHED_SR_DENSE_PARITY
                  parameters=%d
                  gradient_max_error=%.17g
                  covariance_action_max_error=%.17g
                  sr_update_max_error=%.17g
                  pcg_relative_true_residual=%.17g
                %n""", p, gradientError, actionError, updateError,
                result.relativeTrueResidual());

        assertTrue(gradientError < 1e-10, "dense/streaming gradient error=" + gradientError);
        assertTrue(actionError < 2e-10, "dense/matrix-free covariance action");
        assertTrue(updateError < 3e-9, "dense/matrix-free SR update");
        assertTrue(Double.isFinite(result.relativeTrueResidual()));
        assertTrue(result.relativeTrueResidual() <= configuration.relativeTolerance());
        assertTrue(result.solverIterations() > 0);
        assertTrue(result.streamedOperatorPasses() > 0);
        assertEquals(4L,
                result.sampleEvaluations(),
                "cached SR must evaluate each nonzero sample exactly once");
        assertTrue(!result.updateRescaled());

    }

    @Test
    void globalNormRescalingPreservesTheSrDirection() {
        Fixture fixture = fixture();
        var optimizer = new FermiNetMatrixFreeSrOptimizer();
        var unbounded = optimizer.oneIteration(fixture.state, fixture.samples,
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        .03, .2, 100.0, 1, 64, 300, 1e-11, 1e-12));
        var bounded = optimizer.oneIteration(fixture.state, fixture.samples,
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        .03, .2, 1e-5, 1, 64, 300, 1e-11, 1e-12));
        double[] raw = difference(unbounded.state().parameterArray(),
                fixture.state.parameterArray());
        double[] applied = difference(bounded.state().parameterArray(),
                fixture.state.parameterArray());
        double ratio = bounded.appliedUpdateNorm() / unbounded.appliedUpdateNorm();
        double directionError = 0.0;
        for (int i = 0; i < raw.length; i++) {
            directionError = Math.max(directionError, Math.abs(applied[i] - ratio * raw[i]));
        }
        assertTrue(bounded.updateRescaled());
        assertEquals(1e-5, bounded.appliedUpdateNorm(), 2e-15);
        assertTrue(directionError < 2e-16, "uniform rescaling must preserve direction");
    }

    @Test
    void invalidInputsFailClosedAndCachedMemoryTracksSamplesTimesParameters() {
        Fixture fixture = fixture();
        var optimizer = new FermiNetMatrixFreeSrOptimizer();
        var configuration = new FermiNetMatrixFreeSrOptimizer.Configuration(
                .03, .2, .1, 1, 64, 30, 1e-9, 1e-12);

        assertThrows(IllegalArgumentException.class,
                () -> optimizer.oneIteration(
                        fixture.state,
                        List.of(),
                        configuration));

        assertThrows(IllegalArgumentException.class,
                () -> new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                        Double.NaN,
                        coordinates(0.0)));

        assertThrows(IllegalArgumentException.class,
                () -> new FermiNetMatrixFreeSrOptimizer.Configuration(
                        Double.NaN,
                        .2,
                        .1,
                        1,
                        64,
                        30,
                        1e-9,
                        1e-12));

        /*
         * Production-scale retained numeric storage estimate for the cached
         * SR architecture. This is deliberately NOT called a JVM peak-memory
         * estimate because transient FermiNet evaluation/JIT/GC allocations
         * are outside this accounting.
         */
        long parameters = 737_376L;
        long cachedSamples = 64L;
        long configuredBlockSize = 128L;

        long derivativeStoreBytes =
                Math.multiplyExact(
                        Double.BYTES,
                        Math.multiplyExact(
                                cachedSamples,
                                parameters));

        long sampleMetadataBytes =
                Math.multiplyExact(
                        2L * Double.BYTES,
                        cachedSamples);

        long meanDerivativeBytes =
                Math.multiplyExact(
                        Double.BYTES,
                        parameters);

        /*
         * Conservative O(p) allowance for PCG vectors, result/update vectors,
         * gradient/statistics arrays, and related SR numerical workspace.
         */
        long conservativeLinearVectors = 18L;

        long linearWorkspaceBytes =
                Math.multiplyExact(
                        Double.BYTES,
                        Math.multiplyExact(
                                conservativeLinearVectors,
                                parameters));

        long estimatedRetainedNumericBytes =
                Math.addExact(
                        derivativeStoreBytes,
                        Math.addExact(
                                sampleMetadataBytes,
                                Math.addExact(
                                        meanDerivativeBytes,
                                        linearWorkspaceBytes)));

        assertEquals(
                377_536_512L,
                derivativeStoreBytes);

        assertTrue(
                estimatedRetainedNumericBytes < 600_000_000L,
                "64-sample cached SR retained numeric storage unexpectedly exceeds 600 MB");

        System.out.printf("""
                FERMINET_CACHED_SR_MEMORY
                  production_parameters=%d
                  cached_samples=%d
                  configured_block_size=%d
                  preconditioner=IDENTITY
                  derivative_store_bytes=%d
                  mean_derivative_bytes=%d
                  conservative_linear_workspace_bytes=%d
                  estimated_retained_sr_numeric_bytes=%d
                  complexity=O(samples*parameters) derivative cache + O(parameters) workspace
                  neural_evaluation_consequence=one derivative-complete evaluation per nonzero sample per SR iteration
                  note=estimate excludes transient FermiNet/JVM/JIT/GC allocations
                %n""",
                parameters,
                cachedSamples,
                configuredBlockSize,
                derivativeStoreBytes,
                meanDerivativeBytes,
                linearWorkspaceBytes,
                estimatedRetainedNumericBytes);
    }

    private static DenseReference denseReference(FermiNetV1State state,
                                                 List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples, double damping) {
        int p = state.parameterCount();
        double[][] derivatives = new double[samples.size()][];
        double[] energies = new double[samples.size()];
        double weightSum = 0.0;
        for (int k = 0; k < samples.size(); k++) {
            var sample = samples.get(k);
            derivatives[k] = state.evaluate(sample.coordinates()).parameterLogDerivatives();
            energies[k] = FermiNetVmc.localEnergy(state, sample.coordinates()).totalHartree();
            weightSum += sample.weight();
        }
        double energy = 0.0;
        double[] mean = new double[p];
        double[] meanEnergyDerivative = new double[p];
        for (int k = 0; k < samples.size(); k++) {
            double weight = samples.get(k).weight() / weightSum;
            energy += weight * energies[k];
            for (int i = 0; i < p; i++) {
                mean[i] += weight * derivatives[k][i];
                meanEnergyDerivative[i] += weight * derivatives[k][i] * energies[k];
            }
        }
        double[] gradient = new double[p];
        for (int i = 0; i < p; i++) gradient[i] = 2.0
                * (meanEnergyDerivative[i] - mean[i] * energy);
        double[][] system = new double[p][p];
        for (int k = 0; k < samples.size(); k++) {
            double weight = samples.get(k).weight() / weightSum;
            for (int i = 0; i < p; i++) {
                double left = derivatives[k][i] - mean[i];
                for (int j = 0; j < p; j++) {
                    system[i][j] += weight * left * (derivatives[k][j] - mean[j]);
                }
            }
        }
        for (int i = 0; i < p; i++) system[i][i] += damping;
        return new DenseReference(gradient, system);
    }

    private static double[] conjugateGradient(double[][] matrix, double[] rhs,
                                              double tolerance, int maximumIterations) {
        double[] x = new double[rhs.length];
        double[] residual = rhs.clone();
        double[] direction = residual.clone();
        double rr = dot(residual, residual);
        for (int iteration = 0; iteration < maximumIterations; iteration++) {
            double[] action = multiply(matrix, direction);
            double alpha = rr / dot(direction, action);
            for (int i = 0; i < x.length; i++) x[i] += alpha * direction[i];
            for (int i = 0; i < x.length; i++) residual[i] -= alpha * action[i];
            double next = dot(residual, residual);
            if (Math.sqrt(next) <= tolerance) return x;
            double beta = next / rr;
            for (int i = 0; i < x.length; i++) direction[i] = residual[i] + beta * direction[i];
            rr = next;
        }
        throw new AssertionError("independent dense CG did not converge");
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] result = new double[vector.length];
        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result.length; j++) result[i] += matrix[i][j] * vector[j];
        }
        return result;
    }

    private static double dot(double[] a, double[] b) {
        double value = 0.0;
        for (int i = 0; i < a.length; i++) value += a[i] * b[i];
        return value;
    }

    private static double[] negate(double[] values) {
        double[] result = values.clone();
        for (int i = 0; i < result.length; i++) result[i] = -result[i];
        return result;
    }

    private static double[] scale(double[] values, double factor) {
        double[] result = values.clone();
        for (int i = 0; i < result.length; i++) result[i] *= factor;
        return result;
    }

    private static double[] difference(double[] left, double[] right) {
        double[] result = new double[left.length];
        for (int i = 0; i < result.length; i++) result[i] = left[i] - right[i];
        return result;
    }

    private static double maxError(double[] expected, double[] actual) {
        double maximum = 0.0;
        for (int i = 0; i < expected.length; i++) {
            maximum = Math.max(maximum, Math.abs(expected[i] - actual[i]));
        }
        return maximum;
    }

    private static Fixture fixture() {
        Molecule molecule = water();
        var configuration = FermiNetV1Configuration.testFixture();
        var state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule), 44017L));
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples = List.of(
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.0, coordinates(0.0)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(2.0, coordinates(.015)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(.75, coordinates(-.021)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.25, coordinates(.033)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(0.0, coordinates(.2)));
        return new Fixture(state, samples);
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {{.18,.11,.27},{-.31,.42,-.16},{.57,-.28,.33},{-.63,-.37,.21},
                {.24,.71,-.45},{-.22,-.15,-.38},{.36,-.54,.19},{-.48,.26,.51},
                {.69,.18,-.24},{-.12,.61,.37}};
        List<QuantumCoordinates.ParticleCoordinate> result = new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            double signed = (i % 2 == 0 ? shift : -shift);
            result.add(new QuantumCoordinates.ParticleCoordinate(i, xyz[i][0] + signed,
                    xyz[i][1] - .5 * signed, xyz[i][2] + .25 * signed,
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(result);
    }

    private static Molecule water() {
        return new Molecule("ferminet-sr-test-water", List.of(
                new NuclearCenter(0,"O",new NuclearCharge(8),new CartesianPosition(0,0,0,LengthUnit.BOHR)),
                new NuclearCenter(1,"H",new NuclearCharge(1),new CartesianPosition(1.7952398191849366,0,0,LengthUnit.BOHR)),
                new NuclearCenter(2,"H",new NuclearCharge(1),new CartesianPosition(-.46464225035067114,1.7340684963325879,0,LengthUnit.BOHR))),
                new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1));
    }

    private record Fixture(FermiNetV1State state,
                           List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) { }
    private record DenseReference(double[] gradient, double[][] system) { }
}