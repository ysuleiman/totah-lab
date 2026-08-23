package totah.lab.prometheus.persistence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete immutable state of a numerical fit. The format is shared by Delta,
 * Amber/RESP/vdW and future ML fits; family-specific state is carried in the
 * basis, normalization, optimizer and frozen-parameter fields.
 */
public record FitArtifact(
        String modelFamily,
        String modelVersion,
        String basisDefinition,
        List<String> basisOrder,
        List<String> parameterNames,
        List<String> parameterUnits,
        List<Double> initialParameterVector,
        List<Double> finalParameterVector,
        Map<String, Double> frozenParameters,
        List<String> parameterBounds,
        String regularization,
        String objectiveDefinition,
        Map<String, Double> objectiveWeights,
        List<String> trainingIds,
        List<String> validationIds,
        Map<String, String> normalizationState,
        String optimizer,
        Map<String, String> optimizerConfiguration,
        Map<String, String> optimizerState,
        long seed,
        ConvergenceStatus convergenceStatus,
        List<String> iterationHistory,
        List<Double> predictions,
        List<Double> residuals,
        Map<String, Double> finalMetrics,
        Map<String, String> sourceDatasetChecksums,
        String codeCommitSha) {

    public FitArtifact {
        modelFamily = required(modelFamily, "modelFamily");
        modelVersion = required(modelVersion, "modelVersion");
        basisDefinition = required(basisDefinition, "basisDefinition");
        basisOrder = strings(basisOrder, "basisOrder", false);
        parameterNames = strings(parameterNames, "parameterNames", false);
        parameterUnits = strings(parameterUnits, "parameterUnits", false);
        initialParameterVector = finite(initialParameterVector, "initialParameterVector");
        finalParameterVector = finite(finalParameterVector, "finalParameterVector");
        frozenParameters = finiteMap(frozenParameters, "frozenParameters");
        parameterBounds = strings(parameterBounds, "parameterBounds", true);
        regularization = required(regularization, "regularization");
        objectiveDefinition = required(objectiveDefinition, "objectiveDefinition");
        objectiveWeights = finiteMap(objectiveWeights, "objectiveWeights");
        trainingIds = strings(trainingIds, "trainingIds", false);
        validationIds = strings(validationIds, "validationIds", true);
        normalizationState = stringMap(normalizationState, "normalizationState");
        optimizer = required(optimizer, "optimizer");
        optimizerConfiguration = stringMap(optimizerConfiguration, "optimizerConfiguration");
        optimizerState = stringMap(optimizerState, "optimizerState");
        Objects.requireNonNull(convergenceStatus, "convergenceStatus");
        iterationHistory = strings(iterationHistory, "iterationHistory", false);
        predictions = finite(predictions, "predictions");
        residuals = finite(residuals, "residuals");
        finalMetrics = finiteMap(finalMetrics, "finalMetrics");
        sourceDatasetChecksums = stringMap(sourceDatasetChecksums, "sourceDatasetChecksums");
        codeCommitSha = required(codeCommitSha, "codeCommitSha");
        int count = parameterNames.size();
        if (basisOrder.size() != count || parameterUnits.size() != count
                || initialParameterVector.size() != count || finalParameterVector.size() != count) {
            throw new IllegalArgumentException("basis order, names, units and parameter vectors must have equal length");
        }
        if (predictions.size() != residuals.size()) {
            throw new IllegalArgumentException("predictions and residuals must have equal length");
        }
        if (convergenceStatus == ConvergenceStatus.SUCCESS && optimizerState.isEmpty()) {
            throw new IllegalArgumentException("successful fit requires persisted optimizer state or explicit NOT_APPLICABLE state");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
        return value;
    }

    private static List<String> strings(List<String> values, String name, boolean allowEmpty) {
        values = List.copyOf(Objects.requireNonNull(values, name));
        if (!allowEmpty && values.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        for (String value : values) required(value, name + " item");
        return values;
    }

    private static List<Double> finite(List<Double> values, String name) {
        values = List.copyOf(Objects.requireNonNull(values, name));
        if (values.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        for (Double value : values) {
            if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
        }
        return values;
    }

    private static Map<String, Double> finiteMap(Map<String, Double> values, String name) {
        values = Map.copyOf(Objects.requireNonNull(values, name));
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            required(entry.getKey(), name + " key");
            if (entry.getValue() == null || !Double.isFinite(entry.getValue())) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
        return values;
    }

    private static Map<String, String> stringMap(Map<String, String> values, String name) {
        values = Map.copyOf(Objects.requireNonNull(values, name));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            required(entry.getKey(), name + " key");
            required(entry.getValue(), name + " value");
        }
        return values;
    }

    public enum ConvergenceStatus { SUCCESS, FAILED, ABORTED }
}
