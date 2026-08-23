package totah.lab.prometheus.persistence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable complete input to a weighted conservative linear fit. */
public record ConservativeLinearFitRequest(
        String modelFamily,
        String modelVersion,
        String basisDefinition,
        List<String> parameterNames,
        List<String> parameterUnits,
        double[][] designMatrix,
        double[] targets,
        double[] rowWeights,
        Map<String, Double> frozenParameters,
        String objectiveDefinition,
        Map<String, Double> objectiveWeights,
        List<String> trainingIds,
        List<String> validationIds,
        Map<String, String> normalizationState,
        Map<String, String> optimizerConfiguration,
        long seed,
        Map<String, String> sourceDatasetChecksums,
        String codeCommitSha) {

    public ConservativeLinearFitRequest {
        modelFamily = required(modelFamily, "modelFamily");
        modelVersion = required(modelVersion, "modelVersion");
        basisDefinition = required(basisDefinition, "basisDefinition");
        parameterNames = List.copyOf(Objects.requireNonNull(parameterNames, "parameterNames"));
        parameterUnits = List.copyOf(Objects.requireNonNull(parameterUnits, "parameterUnits"));
        frozenParameters = Map.copyOf(Objects.requireNonNull(frozenParameters, "frozenParameters"));
        objectiveDefinition = required(objectiveDefinition, "objectiveDefinition");
        objectiveWeights = Map.copyOf(Objects.requireNonNull(objectiveWeights, "objectiveWeights"));
        trainingIds = List.copyOf(Objects.requireNonNull(trainingIds, "trainingIds"));
        validationIds = List.copyOf(Objects.requireNonNull(validationIds, "validationIds"));
        normalizationState = Map.copyOf(Objects.requireNonNull(normalizationState, "normalizationState"));
        optimizerConfiguration = Map.copyOf(Objects.requireNonNull(optimizerConfiguration, "optimizerConfiguration"));
        sourceDatasetChecksums = Map.copyOf(Objects.requireNonNull(sourceDatasetChecksums, "sourceDatasetChecksums"));
        codeCommitSha = required(codeCommitSha, "codeCommitSha");
        designMatrix = deepCopy(Objects.requireNonNull(designMatrix, "designMatrix"));
        targets = Objects.requireNonNull(targets, "targets").clone();
        rowWeights = Objects.requireNonNull(rowWeights, "rowWeights").clone();
        int columns = parameterNames.size();
        if (columns == 0 || parameterUnits.size() != columns) {
            throw new IllegalArgumentException("parameter names and units must have equal nonzero length");
        }
        if (designMatrix.length != targets.length || targets.length != rowWeights.length || targets.length < columns) {
            throw new IllegalArgumentException("fit rows, targets and weights must agree and rows must cover parameters");
        }
        for (int row = 0; row < designMatrix.length; row++) {
            if (designMatrix[row].length != columns) throw new IllegalArgumentException("ragged fit design matrix");
            for (double value : designMatrix[row]) requireFinite(value, "designMatrix");
            requireFinite(targets[row], "targets");
            requireFinite(rowWeights[row], "rowWeights");
            if (!(rowWeights[row] > 0.0)) throw new IllegalArgumentException("row weights must be positive");
        }
    }

    @Override public double[][] designMatrix() { return deepCopy(designMatrix); }
    @Override public double[] targets() { return targets.clone(); }
    @Override public double[] rowWeights() { return rowWeights.clone(); }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
        return value;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    private static double[][] deepCopy(double[][] source) {
        double[][] copy = new double[source.length][];
        for (int index = 0; index < source.length; index++) copy[index] = source[index].clone();
        return copy;
    }
}
