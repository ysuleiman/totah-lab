package totah.lab.prometheus.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Receipt-producing weighted linear least-squares fitter for conservative energy bases.
 * Success is impossible without atomic persistence and verified read-back.
 */
public final class ConservativeLinearFitter {
    public SuccessfulFit fitAndPersist(Path directory, ConservativeLinearFitRequest request) throws IOException {
        double[][] design = request.designMatrix();
        double[] targets = request.targets();
        double[] weights = request.rowWeights();
        int rows = design.length;
        int columns = design[0].length;
        double[][] weighted = new double[rows][columns];
        double[] weightedTarget = new double[rows];
        double[] scales = new double[columns];
        for (int row = 0; row < rows; row++) {
            double rootWeight = Math.sqrt(weights[row]);
            weightedTarget[row] = targets[row] * rootWeight;
            for (int column = 0; column < columns; column++) {
                weighted[row][column] = design[row][column] * rootWeight;
                scales[column] += weighted[row][column] * weighted[row][column];
            }
        }
        for (int column = 0; column < columns; column++) {
            scales[column] = Math.sqrt(scales[column]);
            if (!Double.isFinite(scales[column]) || scales[column] <= 1.0e-14) {
                throw new IllegalArgumentException("unidentifiable zero-norm parameter column: " + request.parameterNames().get(column));
            }
            for (int row = 0; row < rows; row++) weighted[row][column] /= scales[column];
        }
        QrResult qr = modifiedGramSchmidt(weighted, weightedTarget);
        double[] parameters = qr.scaledCoefficients().clone();
        for (int column = 0; column < columns; column++) parameters[column] /= scales[column];
        double[] predictions = multiply(design, parameters);
        double[] residuals = new double[rows];
        double weightedSquare = 0.0;
        double weightSum = 0.0;
        double square = 0.0;
        for (int row = 0; row < rows; row++) {
            residuals[row] = targets[row] - predictions[row];
            square += residuals[row] * residuals[row];
            weightedSquare += weights[row] * residuals[row] * residuals[row];
            weightSum += weights[row];
        }
        Map<String, String> normalization = new LinkedHashMap<>(request.normalizationState());
        normalization.put("weighted_column_l2_scales", Arrays.toString(scales));
        FitArtifact artifact = new FitArtifact(
                request.modelFamily(), request.modelVersion(), request.basisDefinition(),
                request.parameterNames(), request.parameterNames(), request.parameterUnits(),
                zeros(columns), boxed(parameters), request.frozenParameters(),
                request.parameterNames().stream().map(name -> name + ":unbounded").toList(), "NONE",
                request.objectiveDefinition(), request.objectiveWeights(), request.trainingIds(),
                request.validationIds(), normalization, "WEIGHTED_MODIFIED_GRAM_SCHMIDT_QR",
                request.optimizerConfiguration(),
                Map.of("state", "NOT_APPLICABLE_STATELESS_CLOSED_FORM", "rank", Integer.toString(qr.rank())),
                request.seed(), FitArtifact.ConvergenceStatus.SUCCESS,
                List.of("iteration=0;converged=true;rank=" + qr.rank()), boxed(predictions), boxed(residuals),
                Map.of("training_unweighted_rms", Math.sqrt(square / rows),
                        "training_weighted_rms", Math.sqrt(weightedSquare / weightSum)),
                request.sourceDatasetChecksums(), request.codeCommitSha());
        FitArtifactWriter.Receipt receipt = new FitArtifactWriter().persistSuccessful(directory, artifact);
        return new SuccessfulFit(receipt, parameters);
    }

    private static QrResult modifiedGramSchmidt(double[][] matrix, double[] target) {
        int rows = matrix.length;
        int columns = matrix[0].length;
        double[][] q = new double[columns][rows];
        double[][] r = new double[columns][columns];
        for (int column = 0; column < columns; column++) {
            double[] vector = new double[rows];
            for (int row = 0; row < rows; row++) vector[row] = matrix[row][column];
            for (int prior = 0; prior < column; prior++) {
                r[prior][column] = dot(q[prior], vector);
                axpy(vector, q[prior], -r[prior][column]);
            }
            // Reorthogonalization materially improves stability for correlated physical bases.
            for (int prior = 0; prior < column; prior++) {
                double correction = dot(q[prior], vector);
                r[prior][column] += correction;
                axpy(vector, q[prior], -correction);
            }
            r[column][column] = Math.sqrt(dot(vector, vector));
            if (!Double.isFinite(r[column][column]) || r[column][column] <= 1.0e-12) {
                throw new IllegalArgumentException("rank-deficient fit design at column " + column);
            }
            for (int row = 0; row < rows; row++) q[column][row] = vector[row] / r[column][column];
        }
        double[] qty = new double[columns];
        for (int column = 0; column < columns; column++) qty[column] = dot(q[column], target);
        double[] coefficients = new double[columns];
        for (int row = columns - 1; row >= 0; row--) {
            double value = qty[row];
            for (int column = row + 1; column < columns; column++) value -= r[row][column] * coefficients[column];
            coefficients[row] = value / r[row][row];
            if (!Double.isFinite(coefficients[row])) throw new IllegalArgumentException("nonfinite fitted coefficient");
        }
        return new QrResult(coefficients, columns);
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] result = new double[matrix.length];
        for (int row = 0; row < matrix.length; row++) result[row] = dot(matrix[row], vector);
        return result;
    }

    private static double dot(double[] left, double[] right) {
        double result = 0.0;
        for (int index = 0; index < left.length; index++) result += left[index] * right[index];
        return result;
    }

    private static void axpy(double[] target, double[] source, double scale) {
        for (int index = 0; index < target.length; index++) target[index] += scale * source[index];
    }

    private static List<Double> zeros(int size) {
        var values = new ArrayList<Double>(size);
        for (int index = 0; index < size; index++) values.add(0.0);
        return List.copyOf(values);
    }

    private static List<Double> boxed(double[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private record QrResult(double[] scaledCoefficients, int rank) { }
    public record SuccessfulFit(FitArtifactWriter.Receipt receipt, double[] parameters) {
        public SuccessfulFit { parameters = parameters.clone(); }
        @Override public double[] parameters() { return parameters.clone(); }
    }
}
