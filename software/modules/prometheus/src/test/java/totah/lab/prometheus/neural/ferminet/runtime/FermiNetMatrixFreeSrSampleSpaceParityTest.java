package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

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

/**
 * Optimizer-level parity test proving the production sample-space optimizer
 * reproduces the already-qualified damped SR update on the reduced fixture.
 */
final class FermiNetMatrixFreeSrSampleSpaceParityTest {

    @Test
    void sampleSpaceOptimizerMatchesDenseSrReference() {
        Fixture fixture =
                fixture();

        var optimizer =
                new FermiNetMatrixFreeSrOptimizer();

        var configuration =
                new FermiNetMatrixFreeSrOptimizer.Configuration(
                        0.03,
                        0.2,
                        0.1,
                        1);

        FermiNetMatrixFreeSrOptimizer.Result result =
                optimizer.oneIteration(
                        fixture.state,
                        fixture.samples,
                        configuration);

        DenseReference dense =
                denseReference(
                        fixture.state,
                        fixture.samples,
                        configuration.damping());

        double[] expectedDelta =
                conjugateGradient(
                        dense.system,
                        negate(dense.gradient),
                        1e-10,
                        100);

        double[] expectedUpdate =
                expectedDelta.clone();

        for (int i = 0; i < expectedUpdate.length; i++) {
            expectedUpdate[i] *=
                    configuration.learningRate();
        }

        double expectedNorm =
                norm(
                        expectedUpdate);

        if (expectedNorm > configuration.maxUpdateNorm()) {
            double scale =
                    configuration.maxUpdateNorm()
                            / expectedNorm;

            for (int i = 0; i < expectedUpdate.length; i++) {
                expectedUpdate[i] *=
                        scale;
            }
        }

        double[] actualUpdate =
                subtract(
                        result.state().parameterArray(),
                        fixture.state.parameterArray());

        double updateError =
                maxError(
                        expectedUpdate,
                        actualUpdate);

        double gradientError =
                maxError(
                        dense.gradient,
                        result.energyGradient());

        System.out.printf(
                """
                FERMINET_SAMPLE_SPACE_OPTIMIZER_PARITY
                  parameters=%d
                  sample_evaluations=%d
                  gradient_max_error=%.17g
                  sr_update_max_error=%.17g
                  relative_sample_space_residual=%.17g

                """,
                fixture.state.parameterCount(),
                result.sampleEvaluations(),
                gradientError,
                updateError,
                result.relativeTrueResidual());

        assertTrue(
                result.sampleEvaluations()
                        == 4L,
                "expected one neural evaluation per nonzero sample");

        assertTrue(
                gradientError
                        < 2e-10,
                "gradient error="
                        + gradientError);

        assertTrue(
                updateError
                        < 2e-9,
                "sample-space optimizer update error="
                        + updateError);

        assertTrue(
                result.relativeTrueResidual()
                        < 1e-12,
                "sample-space residual="
                        + result.relativeTrueResidual());
    }

    private static DenseReference denseReference(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            double damping) {

        int p = state.parameterCount();
        double[][] derivatives = new double[samples.size()][];
        double[] energies = new double[samples.size()];
        double weightSum = 0.0;

        for (int k = 0; k < samples.size(); k++) {
            var sample = samples.get(k);

            derivatives[k] =
                    state.evaluate(sample.coordinates())
                            .parameterLogDerivatives();

            energies[k] =
                    FermiNetVmc.localEnergy(
                                    state,
                                    sample.coordinates())
                            .totalHartree();

            weightSum += sample.weight();
        }

        double energy = 0.0;
        double[] mean = new double[p];
        double[] meanEnergyDerivative = new double[p];

        for (int k = 0; k < samples.size(); k++) {
            double weight =
                    samples.get(k).weight()
                            / weightSum;

            energy += weight * energies[k];

            for (int i = 0; i < p; i++) {
                mean[i] += weight * derivatives[k][i];

                meanEnergyDerivative[i] +=
                        weight
                                * derivatives[k][i]
                                * energies[k];
            }
        }

        double[] gradient = new double[p];

        for (int i = 0; i < p; i++) {
            gradient[i] =
                    2.0
                            * (meanEnergyDerivative[i]
                            - mean[i] * energy);
        }

        double[][] system = new double[p][p];

        for (int k = 0; k < samples.size(); k++) {
            double weight =
                    samples.get(k).weight()
                            / weightSum;

            for (int i = 0; i < p; i++) {
                double left =
                        derivatives[k][i]
                                - mean[i];

                for (int j = 0; j < p; j++) {
                    system[i][j] +=
                            weight
                                    * left
                                    * (derivatives[k][j] - mean[j]);
                }
            }
        }

        for (int i = 0; i < p; i++) {
            system[i][i] += damping;
        }

        return new DenseReference(
                gradient,
                system);
    }

    private static double[] conjugateGradient(
            double[][] matrix,
            double[] rhs,
            double tolerance,
            int maximumIterations) {

        double[] x = new double[rhs.length];
        double[] residual = rhs.clone();
        double[] direction = residual.clone();

        double rr = dot(residual, residual);

        for (int iteration = 0;
             iteration < maximumIterations;
             iteration++) {

            double[] action =
                    multiply(
                            matrix,
                            direction);

            double alpha =
                    rr
                            / dot(
                            direction,
                            action);

            for (int i = 0; i < x.length; i++) {
                x[i] += alpha * direction[i];
                residual[i] -= alpha * action[i];
            }

            double next = dot(residual, residual);

            if (Math.sqrt(next) <= tolerance) {
                return x;
            }

            double beta = next / rr;

            for (int i = 0; i < x.length; i++) {
                direction[i] =
                        residual[i]
                                + beta
                                * direction[i];
            }

            rr = next;
        }

        throw new AssertionError(
                "independent dense CG did not converge");
    }

    private static double[] multiply(
            double[][] matrix,
            double[] vector) {

        double[] result =
                new double[vector.length];

        for (int i = 0; i < result.length; i++) {
            for (int j = 0; j < result.length; j++) {
                result[i] +=
                        matrix[i][j]
                                * vector[j];
            }
        }

        return result;
    }

    private static double[] subtract(
            double[] left,
            double[] right) {

        double[] result =
                new double[left.length];

        for (int i = 0; i < result.length; i++) {
            result[i] =
                    left[i]
                            - right[i];
        }

        return result;
    }

    private static double dot(
            double[] a,
            double[] b) {

        double result = 0.0;

        for (int i = 0; i < a.length; i++) {
            result += a[i] * b[i];
        }

        return result;
    }

    private static double[] negate(
            double[] values) {

        double[] result = values.clone();

        for (int i = 0; i < result.length; i++) {
            result[i] = -result[i];
        }

        return result;
    }

    private static double norm(
            double[] values) {

        return Math.sqrt(
                dot(values, values));
    }

    private static double maxError(
            double[] expected,
            double[] actual) {

        double result = 0.0;

        for (int i = 0; i < expected.length; i++) {
            result =
                    Math.max(
                            result,
                            Math.abs(
                                    expected[i]
                                            - actual[i]));
        }

        return result;
    }

    private static Fixture fixture() {
        Molecule molecule =
                water();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.initialize(
                                new FermiNetParameterLayout(
                                        configuration,
                                        molecule),
                                44017L));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                List.of(
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                1.0,
                                coordinates(0.0)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                2.0,
                                coordinates(.015)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                .75,
                                coordinates(-.021)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                1.25,
                                coordinates(.033)));

        return new Fixture(
                state,
                samples);
    }

    private static QuantumCoordinates coordinates(
            double shift) {

        double[][] xyz = {
                {.18, .11, .27},
                {-.31, .42, -.16},
                {.57, -.28, .33},
                {-.63, -.37, .21},
                {.24, .71, -.45},
                {-.22, -.15, -.38},
                {.36, -.54, .19},
                {-.48, .26, .51},
                {.69, .18, -.24},
                {-.12, .61, .37}
        };

        List<QuantumCoordinates.ParticleCoordinate> result =
                new ArrayList<>();

        for (int i = 0; i < xyz.length; i++) {
            double signed =
                    i % 2 == 0
                            ? shift
                            : -shift;

            result.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            xyz[i][0] + signed,
                            xyz[i][1] - .5 * signed,
                            xyz[i][2] + .25 * signed,
                            i < 5
                                    ? SpinProjection.ALPHA
                                    : SpinProjection.BETA));
        }

        return new QuantumCoordinates(result);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-sample-space-optimizer-test-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -.46464225035067114,
                                        1.7340684963325879,
                                        0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }

    private record Fixture(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {
    }

    private record DenseReference(
            double[] gradient,
            double[][] system) {
    }
}
