package totah.lab.prometheus.neural.ferminet.diagnostics;

import totah.lab.prometheus.neural.ferminet.pretraining.GaussianHartreeFockOrbitalTargetTest;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetSampleSpaceSrSolver;
import totah.lab.prometheus.numerics.FixedPreconditioners;
import totah.lab.prometheus.numerics.LinearOperator;
import totah.lab.prometheus.numerics.TrueResidualPreconditionedConjugateGradientSolver;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Exact H2O regression diagnostic:
 *
 * OLD:
 *   parameter-space covariance SR solved by PCG
 *
 * NEW:
 *   sample-space SR
 *
 * Both algorithms consume the SAME derivative and local-energy observations.
 *
 * No VMC sampling is performed.
 * No parameter file is written.
 * No optimized state is adopted.
 */
public final class FermiNetH2oOldVsSampleSpaceSrRegressionDriver {

    private static final int SAMPLE_COUNT = 64;

    private static final double DAMPING = 1.0;

    private static final int OBSERVATION_PARALLELISM = 12;
    private static final int BLOCK_SIZE = 8192;

    private static final int MAX_PCG_ITERATIONS = 50;
    private static final double RELATIVE_TOLERANCE = 1.0e-6;
    private static final double ABSOLUTE_TOLERANCE = 1.0e-8;

    private FermiNetH2oOldVsSampleSpaceSrRegressionDriver() {
    }

    public static void main(String[] args)
            throws Exception {

        Path root =
                args.length == 0
                        ? Path.of("/Users/yazan/totah-lab")
                        : Path.of(args[0]);

        root =
                root.toAbsolutePath()
                        .normalize();

        Path pretraining =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-pretraining");

        Path pilot =
                root.resolve(
                        "software/modules/analysis/"
                                + "prometheus-ferminet-h2o-sr-pilot");

        Path parameterFile =
                pretraining.resolve(
                        "pretrained-parameters.hex");

        Path walkerFile =
                pilot.resolve(
                        "baseline-retained-walkers.csv");

        requireFile(parameterFile);
        requireFile(walkerFile);

        Molecule molecule =
                GaussianHartreeFockOrbitalTargetTest.water();

        FermiNetV1Configuration networkConfiguration =
                FermiNetV1Configuration.locked();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        networkConfiguration,
                        molecule);

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        networkConfiguration,
                        FermiNetParameters.fromArray(
                                layout,
                                readParameters(
                                        parameterFile,
                                        layout.parameterCount())));

        List<QuantumCoordinates> allWalkers =
                readWalkers(
                        walkerFile,
                        molecule);

        if (allWalkers.size() < SAMPLE_COUNT) {
            throw new IllegalStateException(
                    "need "
                            + SAMPLE_COUNT
                            + " walkers; found "
                            + allWalkers.size());
        }

        List<QuantumCoordinates> walkers =
                List.copyOf(
                        allWalkers.subList(
                                0,
                                SAMPLE_COUNT));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                walkers.stream()
                        .map(
                                coordinates ->
                                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                                1.0,
                                                coordinates))
                        .toList();

        System.out.printf("""
                FERMINET_H2O_OLD_VS_SAMPLE_SPACE_SR_REGRESSION
                  parameters=%d
                  samples=%d
                  damping=%.17g
                  observation_parallelism=%d
                  block_size=%d
                  max_pcg_iterations=%d
                  relative_tolerance=%.17g
                  absolute_tolerance=%.17g

                Building ONE shared derivative observation set...
                %n""",
                state.parameterCount(),
                samples.size(),
                DAMPING,
                OBSERVATION_PARALLELISM,
                BLOCK_SIZE,
                MAX_PCG_ITERATIONS,
                RELATIVE_TOLERANCE,
                ABSOLUTE_TOLERANCE);

        /*
         * ============================================================
         * Build the derivative observations ONCE.
         * ============================================================
         */

        try (FermiNetSrObservationFile observations =
                     FermiNetSrObservationFile.buildParallel(
                             state,
                             samples,
                             OBSERVATION_PARALLELISM)) {

            if (observations.neuralEvaluations()
                    != SAMPLE_COUNT) {

                throw new IllegalStateException(
                        "expected exactly "
                                + SAMPLE_COUNT
                                + " neural evaluations; found "
                                + observations.neuralEvaluations());
            }

            int sampleCount =
                    observations.sampleCount();

            int parameterCount =
                    observations.parameterCount();

            /*
             * Diagnostic-only cache.
             *
             * 64 x 737376 doubles ~= 377.5 MB.
             *
             * This is intentional here: it prevents the old PCG diagnostic
             * from reevaluating the neural network or rereading the derivative
             * file on every covariance action.
             */
            System.out.printf("""
                    Shared observations complete.
                      neural_evaluations=%d
                      caching_derivatives_bytes=%d
                    %n""",
                    observations.neuralEvaluations(),
                    Math.multiplyExact(
                            Math.multiplyExact(
                                    (long) sampleCount,
                                    parameterCount),
                            Double.BYTES));

            double[][] derivatives =
                    loadDerivativeMatrix(
                            observations,
                            sampleCount,
                            parameterCount);

            SharedStatistics statistics =
                    sharedStatistics(
                            observations,
                            derivatives,
                            sampleCount,
                            parameterCount);

            System.out.printf("""
                    Shared statistics complete.
                      mean_energy_hartree=%.17g
                      gradient_norm=%.17g

                    Running OLD parameter-space PCG...
                    %n""",
                    statistics.meanEnergy(),
                    norm(statistics.gradient()));

            /*
             * ========================================================
             * OLD implementation
             *
             * S_ij = sum_k w_k
             *        (O_ki - mean_i)
             *        (O_kj - mean_j)
             *
             * solve:
             *
             * (S + lambda I) delta = -gradient
             * ========================================================
             */

            LinearOperator oldOperator =
                    new LinearOperator() {

                        @Override
                        public int dimension() {
                            return parameterCount;
                        }

                        @Override
                        public double[] apply(
                                double[] vector) {

                            if (vector.length
                                    != parameterCount) {

                                throw new IllegalArgumentException(
                                        "vector dimension mismatch");
                            }

                            double[] projection =
                                    new double[sampleCount];

                            /*
                             * projection_k =
                             *
                             * (O_k - mean) dot vector
                             */
                            for (int sample = 0;
                                 sample < sampleCount;
                                 sample++) {

                                double[] row =
                                        derivatives[sample];

                                double value =
                                        0.0;

                                for (int parameter = 0;
                                     parameter < parameterCount;
                                     parameter++) {

                                    value +=
                                            (row[parameter]
                                                    - statistics.meanDerivative()[parameter])
                                                    * vector[parameter];
                                }

                                projection[sample] =
                                        value;
                            }

                            double[] result =
                                    new double[parameterCount];

                            /*
                             * result =
                             *
                             * sum_k w_k
                             *   (O_k - mean)
                             *   projection_k
                             *
                             * + damping * vector
                             */
                            for (int sample = 0;
                                 sample < sampleCount;
                                 sample++) {

                                double weightedProjection =
                                        statistics.normalizedWeight()[sample]
                                                * projection[sample];

                                double[] row =
                                        derivatives[sample];

                                for (int parameter = 0;
                                     parameter < parameterCount;
                                     parameter++) {

                                    result[parameter] +=
                                            weightedProjection
                                                    * (row[parameter]
                                                    - statistics.meanDerivative()[parameter]);
                                }
                            }

                            for (int parameter = 0;
                                 parameter < parameterCount;
                                 parameter++) {

                                result[parameter] +=
                                        DAMPING
                                                * vector[parameter];
                            }

                            requireFinite(
                                    result,
                                    "old covariance action");

                            return result;
                        }
                    };

            double[] rhs =
                    negate(
                            statistics.gradient());

            long oldStarted =
                    System.nanoTime();

            TrueResidualPreconditionedConjugateGradientSolver.Result oldSolve =
                    new TrueResidualPreconditionedConjugateGradientSolver()
                            .solve(
                                    oldOperator,
                                    FixedPreconditioners.identity(
                                            parameterCount),
                                    rhs,
                                    new TrueResidualPreconditionedConjugateGradientSolver.Configuration(
                                            MAX_PCG_ITERATIONS,
                                            RELATIVE_TOLERANCE,
                                            ABSOLUTE_TOLERANCE));

            long oldNanos =
                    System.nanoTime()
                            - oldStarted;

            if (!oldSolve.converged()) {

                throw new IllegalStateException(
                        "OLD PCG failed convergence"
                                + System.lineSeparator()
                                + "iterations="
                                + oldSolve.iterations()
                                + System.lineSeparator()
                                + "absoluteResidual="
                                + oldSolve.absoluteTrueResidual()
                                + System.lineSeparator()
                                + "relativeResidual="
                                + oldSolve.relativeTrueResidual());
            }

            double[] deltaOld =
                    oldSolve.solution();

            requireFinite(
                    deltaOld,
                    "old SR delta");

            System.out.printf("""
                    OLD PCG complete.
                      iterations=%d
                      relative_residual=%.17g
                      wall_seconds=%.6f

                    Running NEW sample-space solver on SAME observations...
                    %n""",
                    oldSolve.iterations(),
                    oldSolve.relativeTrueResidual(),
                    oldNanos / 1.0e9);

            /*
             * ========================================================
             * NEW implementation
             *
             * Uses exactly the same FermiNetSrObservationFile that was
             * used to populate the old diagnostic cache.
             * ========================================================
             */

            long newStarted =
                    System.nanoTime();

            FermiNetSampleSpaceSrSolver.Result newSolve =
                    new FermiNetSampleSpaceSrSolver()
                            .solve(
                                    observations,
                                    DAMPING,
                                    BLOCK_SIZE);

            long newNanos =
                    System.nanoTime()
                            - newStarted;

            double[] deltaNew =
                    newSolve.delta();

            requireFinite(
                    deltaNew,
                    "sample-space SR delta");

            /*
             * ========================================================
             * Direct comparison
             * ========================================================
             */

            double oldNorm =
                    norm(deltaOld);

            double newNorm =
                    norm(deltaNew);

            double dotOldNew =
                    dot(
                            deltaOld,
                            deltaNew);

            double cosine =
                    cosine(
                            deltaOld,
                            deltaNew);

            double cosineOldNegativeNew =
                    cosine(
                            deltaOld,
                            negate(deltaNew));

            double[] difference =
                    subtract(
                            deltaNew,
                            deltaOld);

            double differenceNorm =
                    norm(difference);

            double relativeDifference =
                    oldNorm == 0.0
                            ? Double.NaN
                            : differenceNorm
                            / oldNorm;

            double maximumDifference =
                    maxAbsoluteDifference(
                            deltaOld,
                            deltaNew);

            double gradientDotOld =
                    dot(
                            statistics.gradient(),
                            deltaOld);

            double gradientDotNew =
                    dot(
                            statistics.gradient(),
                            deltaNew);

            System.out.printf("""
                    ============================================================
                    FERMINET_H2O_SR_REGRESSION_RESULT
                    ============================================================

                    SHARED_INPUT
                      neural_evaluations=%d
                      mean_energy_hartree=%.17g
                      gradient_norm=%.17g

                    OLD_PARAMETER_SPACE_PCG
                      iterations=%d
                      absolute_true_residual=%.17g
                      relative_true_residual=%.17g
                      delta_norm=%.17g
                      gradient_dot_delta=%.17g
                      wall_seconds=%.6f

                    NEW_SAMPLE_SPACE
                      absolute_sample_space_residual=%.17g
                      relative_sample_space_residual=%.17g
                      delta_norm=%.17g
                      gradient_dot_delta=%.17g
                      wall_seconds=%.6f

                    OLD_VS_NEW
                      dot=%.17g
                      cosine_similarity=%.17g
                      cosine_old_vs_negative_new=%.17g
                      difference_norm=%.17g
                      relative_difference_vs_old=%.17g
                      max_absolute_component_difference=%.17g

                    ============================================================
                    %n""",
                    observations.neuralEvaluations(),
                    statistics.meanEnergy(),
                    norm(statistics.gradient()),

                    oldSolve.iterations(),
                    oldSolve.absoluteTrueResidual(),
                    oldSolve.relativeTrueResidual(),
                    oldNorm,
                    gradientDotOld,
                    oldNanos / 1.0e9,

                    newSolve.absoluteSampleSpaceResidual(),
                    newSolve.relativeSampleSpaceResidual(),
                    newNorm,
                    gradientDotNew,
                    newNanos / 1.0e9,

                    dotOldNew,
                    cosine,
                    cosineOldNegativeNew,
                    differenceNorm,
                    relativeDifference,
                    maximumDifference);

            /*
             * ========================================================
             * Diagnosis
             * ========================================================
             */

            if (cosine > 0.9999) {

                System.out.println(
                        "DIAGNOSIS: OLD AND NEW SR DIRECTIONS ARE ESSENTIALLY ALIGNED.");

            } else if (cosine < -0.9999) {

                System.out.println(
                        "DIAGNOSIS: OLD AND NEW SR DIRECTIONS ARE SIGN-REVERSED.");

            } else {

                System.out.println(
                        "DIAGNOSIS: OLD AND NEW SR DIRECTIONS ARE MATERIALLY DIFFERENT.");
            }

            if (gradientDotOld < 0.0) {

                System.out.println(
                        "OLD_DIRECTION: locally descending.");

            } else {

                System.out.println(
                        "OLD_DIRECTION: NOT locally descending.");
            }

            if (gradientDotNew < 0.0) {

                System.out.println(
                        "NEW_DIRECTION: locally descending.");

            } else {

                System.out.println(
                        "NEW_DIRECTION: NOT locally descending.");
            }

            System.out.println();
            System.out.println(
                    "No VMC performed. No files written. No parameters adopted.");
        }
    }

    private static double[][] loadDerivativeMatrix(
            FermiNetSrObservationFile observations,
            int samples,
            int parameters)
            throws IOException {

        double[][] derivatives =
                new double[samples][parameters];

        int maximumBlock =
                Math.min(
                        BLOCK_SIZE,
                        parameters);

        double[] block =
                new double[
                        Math.multiplyExact(
                                samples,
                                maximumBlock)];

        for (int start = 0;
             start < parameters;
             start += maximumBlock) {

            int length =
                    Math.min(
                            maximumBlock,
                            parameters - start);

            observations.readParameterBlock(
                    start,
                    length,
                    block);

            for (int sample = 0;
                 sample < samples;
                 sample++) {

                System.arraycopy(
                        block,
                        sample * length,
                        derivatives[sample],
                        start,
                        length);
            }
        }

        return derivatives;
    }

    private static SharedStatistics sharedStatistics(
            FermiNetSrObservationFile observations,
            double[][] derivatives,
            int samples,
            int parameters) {

        double weightSum =
                0.0;

        double weightedEnergy =
                0.0;

        for (int sample = 0;
             sample < samples;
             sample++) {

            double weight =
                    observations.weight(sample);

            if (!Double.isFinite(weight)
                    || weight < 0.0) {

                throw new IllegalStateException(
                        "invalid sample weight");
            }

            weightSum +=
                    weight;

            weightedEnergy +=
                    weight
                            * observations.localEnergyHartree(sample);
        }

        if (!(weightSum > 0.0)
                || !Double.isFinite(weightSum)) {

            throw new IllegalStateException(
                    "non-positive total sample weight");
        }

        double meanEnergy =
                weightedEnergy
                        / weightSum;

        double[] normalizedWeight =
                new double[samples];

        for (int sample = 0;
             sample < samples;
             sample++) {

            normalizedWeight[sample] =
                    observations.weight(sample)
                            / weightSum;
        }

        double[] meanDerivative =
                new double[parameters];

        double[] meanDerivativeEnergy =
                new double[parameters];

        for (int sample = 0;
             sample < samples;
             sample++) {

            double weight =
                    normalizedWeight[sample];

            double energy =
                    observations.localEnergyHartree(sample);

            double[] row =
                    derivatives[sample];

            for (int parameter = 0;
                 parameter < parameters;
                 parameter++) {

                double derivative =
                        row[parameter];

                meanDerivative[parameter] +=
                        weight
                                * derivative;

                meanDerivativeEnergy[parameter] +=
                        weight
                                * derivative
                                * energy;
            }
        }

        double[] gradient =
                new double[parameters];

        for (int parameter = 0;
             parameter < parameters;
             parameter++) {

            gradient[parameter] =
                    2.0
                            * (meanDerivativeEnergy[parameter]
                            - meanDerivative[parameter]
                            * meanEnergy);
        }

        requireFinite(
                meanDerivative,
                "mean derivative");

        requireFinite(
                gradient,
                "energy gradient");

        return new SharedStatistics(
                meanEnergy,
                normalizedWeight,
                meanDerivative,
                gradient);
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

    private static double[] subtract(
            double[] left,
            double[] right) {

        if (left.length
                != right.length) {

            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double[] result =
                new double[left.length];

        for (int i = 0;
             i < result.length;
             i++) {

            result[i] =
                    left[i]
                            - right[i];
        }

        return result;
    }

    private static double dot(
            double[] left,
            double[] right) {

        if (left.length
                != right.length) {

            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double value =
                0.0;

        for (int i = 0;
             i < left.length;
             i++) {

            value +=
                    left[i]
                            * right[i];
        }

        return value;
    }

    private static double cosine(
            double[] left,
            double[] right) {

        double leftNorm =
                norm(left);

        double rightNorm =
                norm(right);

        if (leftNorm == 0.0
                || rightNorm == 0.0) {

            return Double.NaN;
        }

        return dot(left, right)
                / (leftNorm * rightNorm);
    }

    private static double norm(
            double[] values) {

        double scale =
                0.0;

        double sum =
                1.0;

        for (double value : values) {

            double absolute =
                    Math.abs(value);

            if (absolute == 0.0) {
                continue;
            }

            if (scale < absolute) {

                double ratio =
                        scale
                                / absolute;

                sum =
                        1.0
                                + sum
                                * ratio
                                * ratio;

                scale =
                        absolute;

            } else {

                double ratio =
                        absolute
                                / scale;

                sum +=
                        ratio
                                * ratio;
            }
        }

        return scale == 0.0
                ? 0.0
                : scale
                * Math.sqrt(sum);
    }

    private static double maxAbsoluteDifference(
            double[] left,
            double[] right) {

        if (left.length
                != right.length) {

            throw new IllegalArgumentException(
                    "vector dimension mismatch");
        }

        double maximum =
                0.0;

        for (int i = 0;
             i < left.length;
             i++) {

            maximum =
                    Math.max(
                            maximum,
                            Math.abs(
                                    left[i]
                                            - right[i]));
        }

        return maximum;
    }

    private static void requireFinite(
            double[] values,
            String label) {

        for (int i = 0;
             i < values.length;
             i++) {

            if (!Double.isFinite(
                    values[i])) {

                throw new IllegalStateException(
                        "non-finite "
                                + label
                                + " at index "
                                + i);
            }
        }
    }

    private static void requireFile(
            Path file)
            throws IOException {

        if (!Files.isRegularFile(file)) {

            throw new IOException(
                    "missing file: "
                            + file);
        }
    }

    private static double[] readParameters(
            Path file,
            int count)
            throws IOException {

        double[] values =
                new double[count];

        boolean[] seen =
                new boolean[count];

        for (String line :
                Files.readAllLines(file)) {

            String trimmed =
                    line.trim();

            if (trimmed.isEmpty()
                    || trimmed.startsWith("#")) {

                continue;
            }

            String[] fields =
                    trimmed.split("\\s+");

            if (fields.length != 2) {

                throw new IOException(
                        "invalid parameter line: "
                                + line);
            }

            int index =
                    Integer.parseInt(
                            fields[0]);

            if (index < 0
                    || index >= count
                    || seen[index]) {

                throw new IOException(
                        "invalid parameter index: "
                                + index);
            }

            double value =
                    Double.parseDouble(
                            fields[1]);

            if (!Double.isFinite(value)) {

                throw new IOException(
                        "non-finite parameter "
                                + index);
            }

            values[index] =
                    value;

            seen[index] =
                    true;
        }

        for (int i = 0;
             i < count;
             i++) {

            if (!seen[i]) {

                throw new IOException(
                        "missing parameter "
                                + i);
            }
        }

        return values;
    }

    private static List<QuantumCoordinates> readWalkers(
            Path file,
            Molecule molecule)
            throws IOException {

        List<String> lines =
                Files.readAllLines(file);

        Map<Integer, List<QuantumCoordinates.ParticleCoordinate>> grouped =
                new LinkedHashMap<>();

        for (int lineIndex = 1;
             lineIndex < lines.size();
             lineIndex++) {

            String line =
                    lines.get(lineIndex)
                            .trim();

            if (line.isEmpty()) {
                continue;
            }

            String[] fields =
                    line.split(",");

            if (fields.length != 6) {

                throw new IOException(
                        "invalid walker line: "
                                + line);
            }

            int walker =
                    Integer.parseInt(
                            fields[0]);

            int electron =
                    Integer.parseInt(
                            fields[1]);

            SpinProjection spin =
                    SpinProjection.valueOf(
                            fields[2]);

            double x =
                    Double.parseDouble(
                            fields[3]);

            double y =
                    Double.parseDouble(
                            fields[4]);

            double z =
                    Double.parseDouble(
                            fields[5]);

            grouped.computeIfAbsent(
                            walker,
                            ignored ->
                                    new ArrayList<>())
                    .add(
                            new QuantumCoordinates.ParticleCoordinate(
                                    electron,
                                    x,
                                    y,
                                    z,
                                    spin));
        }

        List<QuantumCoordinates> result =
                new ArrayList<>();

        for (var entry :
                grouped.entrySet()) {

            List<QuantumCoordinates.ParticleCoordinate> particles =
                    entry.getValue();

            particles.sort(
                    Comparator.comparingInt(
                            QuantumCoordinates.ParticleCoordinate::particleIndex));

            if (particles.size()
                    != molecule.electrons().value()) {

                throw new IOException(
                        "walker electron count mismatch");
            }

            result.add(
                    new QuantumCoordinates(
                            particles));
        }

        return result;
    }

    private record SharedStatistics(
            double meanEnergy,
            double[] normalizedWeight,
            double[] meanDerivative,
            double[] gradient) {

        private SharedStatistics {

            normalizedWeight =
                    normalizedWeight.clone();

            meanDerivative =
                    meanDerivative.clone();

            gradient =
                    gradient.clone();
        }

        @Override
        public double[] normalizedWeight() {
            return normalizedWeight;
        }

        @Override
        public double[] meanDerivative() {
            return meanDerivative;
        }

        @Override
        public double[] gradient() {
            return gradient;
        }
    }
}