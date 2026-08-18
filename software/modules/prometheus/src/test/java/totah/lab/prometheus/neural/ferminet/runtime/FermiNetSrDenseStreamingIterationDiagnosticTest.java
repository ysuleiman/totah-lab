package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
 * Diagnostic-only comparison of explicit dense SR matrix arithmetic against
 * FermiNetMatrixFreeSrOptimizer.explicitJacobianCovarianceActionReference().
 *
 * <p>This test does NOT call oneIteration(), does NOT modify parameters, and
 * does NOT exercise the production PCG solver. It isolates the dense-vs-streamed
 * linear algebra for the same reduced fixture used by
 * FermiNetMatrixFreeSrOptimizerTest.
 */
final class FermiNetSrDenseStreamingIterationDiagnosticTest {

    private static final double DAMPING = 0.2;
    private static final int ITERATIONS = 6;

    @Test
    void printDenseVsStreamingCgTrajectory() {
        Fixture fixture = fixture();
        FermiNetV1State state = fixture.state();
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples = fixture.samples();

        DenseReference dense = denseReference(state, samples, DAMPING);
        FermiNetMatrixFreeSrOptimizer optimizer = new FermiNetMatrixFreeSrOptimizer();

        double[] rhs = negate(dense.gradient());

        /*
         * Dense trajectory.
         */
        double[] xd = new double[rhs.length];
        double[] rd = rhs.clone();
        double[] pd = rd.clone();
        double rrd = dot(rd, rd);

        /*
         * Streamed trajectory.
         */
        double[] xs = new double[rhs.length];
        double[] rs = rhs.clone();
        double[] ps = rs.clone();
        double rrs = dot(rs, rs);

        System.out.printf(Locale.ROOT, """
                FERMINET_SR_DENSE_STREAMING_ITERATION_DIAGNOSTIC
                  parameters=%d
                  nonzero_samples=4
                  damping=%.17g
                  requested_iterations=%d
                  initial_rhs_norm=%.17g

                """,
                rhs.length,
                DAMPING,
                ITERATIONS,
                norm(rhs));

        for (int iteration = 1; iteration <= ITERATIONS; iteration++) {

            /*
             * A p using the explicit dense matrix.
             */
            double[] apDense = multiply(dense.system(), pd);

            /*
             * A p using the explicit-Jacobian reference-oracle seam.
             */
            double[] apStreamed = optimizer.explicitJacobianCovarianceActionReference(
                    state,
                    samples,
                    DAMPING,
                    ps);

            double apError = maxError(apDense, apStreamed);

            double curvatureDense = dot(pd, apDense);
            double curvatureStreamed = dot(ps, apStreamed);

            double alphaDense = rrd / curvatureDense;
            double alphaStreamed = rrs / curvatureStreamed;

            /*
             * Conventional recursive residual updates.
             */
            for (int i = 0; i < rhs.length; i++) {
                xd[i] += alphaDense * pd[i];
                rd[i] -= alphaDense * apDense[i];

                xs[i] += alphaStreamed * ps[i];
                rs[i] -= alphaStreamed * apStreamed[i];
            }

            /*
             * Independently recomputed true residuals b - A x.
             */
            double[] axDense = multiply(dense.system(), xd);
            double[] axStreamed = optimizer.explicitJacobianCovarianceActionReference(
                    state,
                    samples,
                    DAMPING,
                    xs);

            double[] trueDense = subtract(rhs, axDense);
            double[] trueStreamed = subtract(rhs, axStreamed);

            double nextRrDense = dot(rd, rd);
            double nextRrStreamed = dot(rs, rs);

            double betaDense = nextRrDense / rrd;
            double betaStreamed = nextRrStreamed / rrs;

            double xError = maxError(xd, xs);
            double recursiveResidualError = maxError(rd, rs);
            double trueResidualError = maxError(trueDense, trueStreamed);

            double denseRecursiveVsTrue = maxError(rd, trueDense);
            double streamedRecursiveVsTrue = maxError(rs, trueStreamed);

            System.out.printf(Locale.ROOT, """
                    iteration %d
                      Ap_max_error                    = %.17g
                      curvature_dense                 = %.17g
                      curvature_streamed              = %.17g
                      curvature_abs_error             = %.17g
                      alpha_dense                     = %.17g
                      alpha_streamed                  = %.17g
                      alpha_abs_error                 = %.17g
                      x_max_error                     = %.17g
                      recursive_residual_max_error    = %.17g
                      true_residual_max_error         = %.17g
                      dense_recursive_vs_true_error   = %.17g
                      stream_recursive_vs_true_error  = %.17g
                      dense_recursive_residual_norm   = %.17g
                      stream_recursive_residual_norm  = %.17g
                      dense_true_residual_norm        = %.17g
                      stream_true_residual_norm       = %.17g
                      beta_dense                      = %.17g
                      beta_streamed                   = %.17g
                      beta_abs_error                  = %.17g

                    """,
                    iteration,
                    apError,
                    curvatureDense,
                    curvatureStreamed,
                    Math.abs(curvatureDense - curvatureStreamed),
                    alphaDense,
                    alphaStreamed,
                    Math.abs(alphaDense - alphaStreamed),
                    xError,
                    recursiveResidualError,
                    trueResidualError,
                    denseRecursiveVsTrue,
                    streamedRecursiveVsTrue,
                    norm(rd),
                    norm(rs),
                    norm(trueDense),
                    norm(trueStreamed),
                    betaDense,
                    betaStreamed,
                    Math.abs(betaDense - betaStreamed));

            assertFinite(apDense);
            assertFinite(apStreamed);
            assertFinite(xd);
            assertFinite(xs);
            assertFinite(rd);
            assertFinite(rs);
            assertFinite(trueDense);
            assertFinite(trueStreamed);

            assertTrue(Double.isFinite(alphaDense));
            assertTrue(Double.isFinite(alphaStreamed));
            assertTrue(Double.isFinite(betaDense));
            assertTrue(Double.isFinite(betaStreamed));

            /*
             * Advance both search directions using their own recursive
             * residuals. We intentionally do not force either path to use the
             * other's numbers.
             */
            for (int i = 0; i < rhs.length; i++) {
                pd[i] = rd[i] + betaDense * pd[i];
                ps[i] = rs[i] + betaStreamed * ps[i];
            }

            rrd = nextRrDense;
            rrs = nextRrStreamed;
        }

        System.out.println(
                "Diagnostic complete. Inspect the first iteration where "
                        + "dense/streamed errors cease to be near roundoff.");
    }

    private static DenseReference denseReference(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            double damping) {

        int p = state.parameterCount();

        double[][] derivatives =
                new double[samples.size()][];

        double[] energies =
                new double[samples.size()];

        double weightSum = 0.0;

        for (int k = 0; k < samples.size(); k++) {
            var sample = samples.get(k);

            derivatives[k] =
                    state.evaluate(
                                    sample.coordinates())
                            .parameterLogDerivatives();

            energies[k] =
                    FermiNetVmc.localEnergy(
                                    state,
                                    sample.coordinates())
                            .totalHartree();

            weightSum +=
                    sample.weight();
        }

        double energy = 0.0;
        double[] mean = new double[p];
        double[] meanEnergyDerivative = new double[p];

        for (int k = 0; k < samples.size(); k++) {
            double weight =
                    samples.get(k).weight()
                            / weightSum;

            energy +=
                    weight
                            * energies[k];

            for (int i = 0; i < p; i++) {
                mean[i] +=
                        weight
                                * derivatives[k][i];

                meanEnergyDerivative[i] +=
                        weight
                                * derivatives[k][i]
                                * energies[k];
            }
        }

        double[] gradient =
                new double[p];

        for (int i = 0; i < p; i++) {
            gradient[i] =
                    2.0
                            * (meanEnergyDerivative[i]
                            - mean[i] * energy);
        }

        double[][] system =
                new double[p][p];

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
            system[i][i] +=
                    damping;
        }

        return new DenseReference(
                gradient,
                system);
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

    private static double[] negate(
            double[] values) {

        double[] result =
                values.clone();

        for (int i = 0; i < result.length; i++) {
            result[i] =
                    -result[i];
        }

        return result;
    }

    private static double dot(
            double[] a,
            double[] b) {

        double value = 0.0;

        for (int i = 0; i < a.length; i++) {
            value +=
                    a[i]
                            * b[i];
        }

        return value;
    }

    private static double norm(
            double[] values) {

        return Math.sqrt(
                dot(values, values));
    }

    private static double maxError(
            double[] expected,
            double[] actual) {

        double maximum = 0.0;

        for (int i = 0; i < expected.length; i++) {
            maximum =
                    Math.max(
                            maximum,
                            Math.abs(
                                    expected[i]
                                            - actual[i]));
        }

        return maximum;
    }

    private static void assertFinite(
            double[] values) {

        for (double value : values) {
            assertTrue(
                    Double.isFinite(value),
                    "non-finite diagnostic value");
        }
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
                                coordinates(.033)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                0.0,
                                coordinates(.2)));

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

        return new QuantumCoordinates(
                result);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-sr-test-water",
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
