package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import totah.lab.prometheus.numerics.FermiNetSampleSpaceSrSolver;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Exact parity test between sample-space SR and the explicit high-dimensional
 * parameter-space SR equations on the reduced FermiNet fixture.
 */
final class FermiNetSampleSpaceSrSolverTest {

    @Test
    void sampleSpaceSolveMatchesExplicitParameterSpaceSolve()
            throws IOException {

        Fixture fixture =
                fixture();

        double damping =
                0.2;

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.build(
                             fixture.state,
                             fixture.samples)) {

            FermiNetSampleSpaceSrSolver.Result result =
                    new FermiNetSampleSpaceSrSolver()
                            .solve(
                                    observations,
                                    damping,
                                    128);

            double[] legacyGradient =
                    legacyEnergyGradient(observations);

            long gradientBitMismatches =
                    bitMismatches(
                            legacyGradient,
                            result.energyGradient());

            DenseReference dense =
                    denseReference(
                            fixture.state,
                            fixture.samples,
                            damping);

            double[] expected =
                    conjugateGradient(
                            dense.system,
                            negate(dense.gradient),
                            1e-10,
                            100);

            double maxError =
                    maxError(
                            expected,
                            result.delta());

            System.out.printf(
                    """
                    FERMINET_SAMPLE_SPACE_SR_PARITY
                      parameters=%d
                      stored_samples=%d
                      derivative_file_bytes=%d
                      neural_evaluations=%d
                      sample_space_relative_residual=%.17g
                      delta_max_error=%.17g
                      gradient_bit_mismatches=%d
                      gram_derivative_values_read=%d
                      reconstruction_derivative_values_read=%d

                    """,
                    fixture.state.parameterCount(),
                    observations.sampleCount(),
                    observations.derivativeBytes(),
                    observations.neuralEvaluations(),
                    result.relativeSampleSpaceResidual(),
                    maxError,
                    gradientBitMismatches,
                    result.gramDerivativeValuesRead(),
                    result.reconstructionDerivativeValuesRead());

            assertTrue(
                    result.relativeSampleSpaceResidual()
                            < 1e-12,
                    "sample-space residual="
                            + result.relativeSampleSpaceResidual());

            assertTrue(
                    maxError
                            < 2e-9,
                    "sample-space/parameter-space SR delta error="
                            + maxError);

            assertEquals(
                    0L,
                    gradientBitMismatches,
                    "fused gradient must be bit-identical to the removed traversal");

            long derivativeValues =
                    (long) observations.sampleCount()
                            * observations.parameterCount();

            assertEquals(derivativeValues, result.gramDerivativeValuesRead());
            assertEquals(derivativeValues, result.reconstructionDerivativeValuesRead());
        }
    }

    @Test
    void structuredSolveMatchesExplicitDerivativeOracle()
            throws IOException {

        Fixture fixture = fixture();
        double damping = 0.2;

        try (FermiNetSrObservationFile explicit =
                     FermiNetSrObservationFile.build(
                             fixture.state,
                             fixture.samples);
             FermiNetStructuredSrObservationFile structured =
                     FermiNetStructuredSrObservationFile.buildParallel(
                             fixture.state,
                             fixture.samples,
                             2)) {

            double[] explicitDerivatives =
                    new double[Math.multiplyExact(
                            explicit.sampleCount(),
                            explicit.parameterCount())];
            explicit.readParameterBlock(
                    0,
                    explicit.parameterCount(),
                    explicitDerivatives);

            for (FermiNetStructuredSrStatistics.Family family
                    : structured.schema().families()) {
                double[] familyStatistics =
                        structured.readFamily(family);
                FermiNetParameterLayout.Block block =
                        structured.schema().layout().block(
                                family.blockName());
                double familyMaxError = 0.0;
                double[][] reconstructedSamples =
                        new double[structured.sampleCount()][];
                for (int sample = 0;
                     sample < structured.sampleCount();
                     sample++) {
                    double[] reconstructed =
                            FermiNetStructuredSampleSpaceSrSolver
                                    .materializeSampleFamily(
                                            familyStatistics,
                                            family,
                                            block,
                                            structured.sampleCount(),
                                            sample);
                    reconstructedSamples[sample] = reconstructed;
                    for (int parameter = 0;
                         parameter < block.size();
                         parameter++) {
                        familyMaxError = Math.max(
                                familyMaxError,
                                Math.abs(
                                        reconstructed[parameter]
                                                - explicitDerivatives[
                                                sample * explicit.parameterCount()
                                                        + block.startInclusive()
                                                        + parameter]));
                    }
                }
                assertTrue(
                        familyMaxError < 2e-12,
                        family.blockName()
                                + " structured family derivative error="
                                + familyMaxError);

                double familyGramError = 0.0;
                double familyGramScale = 0.0;
                for (int left = 0;
                     left < structured.sampleCount();
                     left++) {
                    for (int right = 0;
                         right < structured.sampleCount();
                         right++) {
                        double explicitFamilyDot = 0.0;
                        for (int parameter = 0;
                             parameter < block.size();
                             parameter++) {
                            explicitFamilyDot +=
                                    reconstructedSamples[left][parameter]
                                            * reconstructedSamples[right][parameter];
                        }
                        double structuredFamilyDot =
                                FermiNetStructuredSampleSpaceSrSolver.familyDot(
                                        familyStatistics,
                                        family,
                                        left,
                                        right);
                        familyGramError = Math.max(
                                familyGramError,
                                Math.abs(explicitFamilyDot
                                        - structuredFamilyDot));
                        familyGramScale = Math.max(
                                familyGramScale,
                                Math.abs(explicitFamilyDot));
                    }
                }
                assertTrue(
                        familyGramError
                                <= 1e-12 * Math.max(1.0, familyGramScale),
                        family.blockName()
                                + " structured family Gram error="
                                + familyGramError);

                System.out.printf(
                        "FERMINET_STRUCTURED_FAMILY_PARITY family=%s derivative_max_abs=%.17g gram_max_abs=%.17g gram_max_rel=%.17g%n",
                        family.blockName(),
                        familyMaxError,
                        familyGramError,
                        familyGramError / Math.max(1.0, familyGramScale));
            }

            FermiNetSampleSpaceSrSolver.Result expected =
                    new FermiNetSampleSpaceSrSolver()
                            .solve(explicit, damping, 128);

            FermiNetStructuredSampleSpaceSrSolver.Result actual =
                    new FermiNetStructuredSampleSpaceSrSolver()
                            .solve(structured, damping);

            double gradientError = maxError(
                    expected.energyGradient(),
                    actual.energyGradient());
            double deltaError = maxError(
                    expected.delta(),
                    actual.delta());

            double[] explicitGram = centeredDampedGram(
                    explicit,
                    explicitDerivatives,
                    damping);
            double gramError = maxError(
                    explicitGram,
                    actual.centeredDampedGram());
            double gramRelativeError = maxRelativeError(
                    explicitGram,
                    actual.centeredDampedGram());
            double gradientRelativeError = maxRelativeError(
                    expected.energyGradient(),
                    actual.energyGradient());
            double deltaRelativeError = maxRelativeError(
                    expected.delta(),
                    actual.delta());
            double deltaNormDifference = Math.abs(
                    norm(expected.delta()) - norm(actual.delta()));
            double deltaCosine = cosine(
                    expected.delta(),
                    actual.delta());

            System.out.printf("""
                    FERMINET_STRUCTURED_SR_PARITY
                      statistics_spool_bytes=%d
                      explicit_derivative_bytes=%d
                      mean_energy_error=%.17g
                      centered_gram_max_error=%.17g
                      centered_gram_max_relative_error=%.17g
                      gradient_max_error=%.17g
                      gradient_max_relative_error=%.17g
                      delta_max_error=%.17g
                      delta_max_relative_error=%.17g
                      delta_norm_difference=%.17g
                      delta_cosine=%.17g

                    """,
                    actual.statisticsSpoolBytes(),
                    explicit.derivativeBytes(),
                    Math.abs(expected.meanEnergyHartree()
                            - actual.meanEnergyHartree()),
                    gramError,
                    gramRelativeError,
                    gradientError,
                    gradientRelativeError,
                    deltaError,
                    deltaRelativeError,
                    deltaNormDifference,
                    deltaCosine);

            assertEquals(
                    Double.doubleToLongBits(expected.meanEnergyHartree()),
                    Double.doubleToLongBits(actual.meanEnergyHartree()));
            assertTrue(gramError < 2e-9,
                    "structured centered Gram error=" + gramError);
            assertTrue(gradientError < 2e-10,
                    "structured gradient error=" + gradientError);
            assertTrue(deltaError < 2e-9,
                    "structured delta error=" + deltaError);
        }
    }

    @Test
    void structuredStatisticsSpoolIsRemovedAfterWorkerFailure()
            throws IOException {
        Fixture fixture = fixture();
        long before = structuredSpoolCount();

        assertThrows(
                IOException.class,
                () -> FermiNetStructuredSrObservationFile.buildParallel(
                        fixture.state,
                        fixture.samples,
                        2,
                        row -> {
                            if (row == 1) {
                                throw new IllegalStateException(
                                        "injected structured spool failure");
                            }
                        }));

        assertEquals(before, structuredSpoolCount(),
                "failed structured SR build leaked its ephemeral spool");
    }

    private static long structuredSpoolCount()
            throws IOException {
        Path temporary = Path.of(System.getProperty("java.io.tmpdir"));
        try (var paths = Files.list(temporary)) {
            return paths
                    .filter(path -> path.getFileName().toString()
                            .startsWith("prometheus-ferminet-structured-sr-"))
                    .count();
        }
    }

    private static double[] centeredDampedGram(
            FermiNetSrObservationFile observations,
            double[] derivatives,
            double damping) {
        int samples = observations.sampleCount();
        int parameters = observations.parameterCount();
        double weightSum = 0.0;
        for (int sample = 0; sample < samples; sample++) {
            weightSum += observations.weight(sample);
        }
        double[] weight = new double[samples];
        double[] mean = new double[parameters];
        for (int sample = 0; sample < samples; sample++) {
            weight[sample] = observations.weight(sample) / weightSum;
            for (int parameter = 0; parameter < parameters; parameter++) {
                mean[parameter] += weight[sample]
                        * derivatives[sample * parameters + parameter];
            }
        }
        double[] gram = new double[samples * samples];
        for (int row = 0; row < samples; row++) {
            for (int column = 0; column < samples; column++) {
                double value = 0.0;
                for (int parameter = 0; parameter < parameters; parameter++) {
                    value += (derivatives[row * parameters + parameter]
                            - mean[parameter])
                            * (derivatives[column * parameters + parameter]
                            - mean[parameter]);
                }
                gram[row * samples + column] = Math.sqrt(
                        weight[row] * weight[column]) * value;
            }
            gram[row * samples + row] += damping;
        }
        return gram;
    }

    private static double maxRelativeError(
            double[] expected,
            double[] actual) {
        double maximum = 0.0;
        for (int i = 0; i < expected.length; i++) {
            maximum = Math.max(
                    maximum,
                    Math.abs(expected[i] - actual[i])
                            / Math.max(1.0, Math.abs(expected[i])));
        }
        return maximum;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        return dot / Math.sqrt(leftNorm * rightNorm);
    }

    private static double norm(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private static double[] legacyEnergyGradient(
            FermiNetSrObservationFile observations)
            throws IOException {

        int samples = observations.sampleCount();
        int parameters = observations.parameterCount();
        double weightSum = 0.0;
        double meanEnergy = 0.0;

        for (int sample = 0; sample < samples; sample++) {
            weightSum += observations.weight(sample);
            meanEnergy += observations.weight(sample)
                    * observations.localEnergyHartree(sample);
        }
        meanEnergy /= weightSum;

        double[] gradient = new double[parameters];
        int blockSize = Math.min(8192, parameters);
        double[] block = new double[Math.multiplyExact(samples, blockSize)];

        for (int start = 0; start < parameters; start += blockSize) {
            int length = Math.min(blockSize, parameters - start);
            observations.readParameterBlock(start, length, block);

            for (int local = 0; local < length; local++) {
                double value = 0.0;
                for (int sample = 0; sample < samples; sample++) {
                    double normalizedWeight = observations.weight(sample) / weightSum;
                    value += 2.0
                            * normalizedWeight
                            * (observations.localEnergyHartree(sample) - meanEnergy)
                            * block[sample * length + local];
                }
                gradient[start + local] = value;
            }
        }
        return gradient;
    }

    private static long bitMismatches(double[] left, double[] right) {
        long mismatches = 0L;
        for (int i = 0; i < left.length; i++) {
            if (Double.doubleToLongBits(left[i]) != Double.doubleToLongBits(right[i])) {
                mismatches++;
            }
        }
        return mismatches;
    }

    private static DenseReference denseReference(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples,
            double damping) {

        int p =
                state.parameterCount();

        double[][] derivatives =
                new double[samples.size()][];

        double[] energies =
                new double[samples.size()];

        double weightSum =
                0.0;

        for (int k = 0;
             k < samples.size();
             k++) {

            var sample =
                    samples.get(k);

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

        double energy =
                0.0;

        double[] mean =
                new double[p];

        double[] meanEnergyDerivative =
                new double[p];

        for (int k = 0;
             k < samples.size();
             k++) {

            double weight =
                    samples.get(k).weight()
                            / weightSum;

            energy +=
                    weight
                            * energies[k];

            for (int i = 0;
                 i < p;
                 i++) {

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

        for (int i = 0;
             i < p;
             i++) {

            gradient[i] =
                    2.0
                            * (meanEnergyDerivative[i]
                            - mean[i] * energy);
        }

        double[][] system =
                new double[p][p];

        for (int k = 0;
             k < samples.size();
             k++) {

            double weight =
                    samples.get(k).weight()
                            / weightSum;

            for (int i = 0;
                 i < p;
                 i++) {

                double left =
                        derivatives[k][i]
                                - mean[i];

                for (int j = 0;
                     j < p;
                     j++) {

                    system[i][j] +=
                            weight
                                    * left
                                    * (derivatives[k][j] - mean[j]);
                }
            }
        }

        for (int i = 0;
             i < p;
             i++) {

            system[i][i] +=
                    damping;
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

        double[] x =
                new double[rhs.length];

        double[] residual =
                rhs.clone();

        double[] direction =
                residual.clone();

        double rr =
                dot(
                        residual,
                        residual);

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

            for (int i = 0;
                 i < x.length;
                 i++) {

                x[i] +=
                        alpha
                                * direction[i];

                residual[i] -=
                        alpha
                                * action[i];
            }

            double next =
                    dot(
                            residual,
                            residual);

            if (Math.sqrt(next)
                    <= tolerance) {

                return x;
            }

            double beta =
                    next
                            / rr;

            for (int i = 0;
                 i < x.length;
                 i++) {

                direction[i] =
                        residual[i]
                                + beta
                                * direction[i];
            }

            rr =
                    next;
        }

        throw new AssertionError(
                "independent dense CG did not converge");
    }

    private static double[] multiply(
            double[][] matrix,
            double[] vector) {

        double[] result =
                new double[vector.length];

        for (int i = 0;
             i < result.length;
             i++) {

            for (int j = 0;
                 j < result.length;
                 j++) {

                result[i] +=
                        matrix[i][j]
                                * vector[j];
            }
        }

        return result;
    }

    private static double dot(
            double[] a,
            double[] b) {

        double result =
                0.0;

        for (int i = 0;
             i < a.length;
             i++) {

            result +=
                    a[i]
                            * b[i];
        }

        return result;
    }

    private static double[] negate(
            double[] values) {

        double[] result =
                values.clone();

        for (int i = 0;
             i < result.length;
             i++) {

            result[i] =
                    -result[i];
        }

        return result;
    }

    private static double maxError(
            double[] expected,
            double[] actual) {

        double result =
                0.0;

        for (int i = 0;
             i < expected.length;
             i++) {

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

        for (int i = 0;
             i < xyz.length;
             i++) {

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
                "ferminet-sample-space-test-water",
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
