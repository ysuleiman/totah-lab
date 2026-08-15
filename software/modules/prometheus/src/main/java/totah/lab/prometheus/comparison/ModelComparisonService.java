package totah.lab.prometheus.comparison;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Builds a comparison matrix solely from frozen, already-computed observations. */
public final class ModelComparisonService {

    public ModelComparisonResult compare(
            List<ModelReference> models,
            List<ValidationMetricDefinition> metrics,
            List<CandidateMetricObservation> observations) {

        models = List.copyOf(Objects.requireNonNull(models, "models"));
        metrics = List.copyOf(Objects.requireNonNull(metrics, "metrics"));
        observations = List.copyOf(Objects.requireNonNull(observations, "observations"));
        rejectDuplicateModelIds(models);
        rejectUnknownModels(models, observations);

        List<ModelComparisonRow> rows = new ArrayList<>();
        for (ValidationMetricDefinition metric : metrics) {
            List<ModelMetricCell> cells = new ArrayList<>();
            for (ModelReference model : models) {
                cells.add(resolve(model, metric, observations));
            }
            rows.add(new ModelComparisonRow(metric, cells));
        }
        return new ModelComparisonResult(models, rows);
    }

    private ModelMetricCell resolve(
            ModelReference model,
            ValidationMetricDefinition metric,
            List<CandidateMetricObservation> observations) {

        List<CandidateMetricObservation> matches = observations.stream()
                .filter(item -> item.model().candidateId().equals(model.candidateId()))
                .filter(item -> item.dimension() == metric.dimension())
                .filter(item -> item.metricId().equals(metric.metricId()))
                .toList();
        if (matches.isEmpty()) {
            return missing(model, "metric was not supplied");
        }
        Optional<CandidateMetricObservation> exact = matches.stream()
                .filter(item -> item.validationDefinitionChecksum()
                        .equals(metric.validationDefinitionChecksum()))
                .filter(item -> item.protocolKey().equals(metric.protocolKey()))
                .filter(item -> item.unit().equals(metric.unit()))
                .findFirst();
        if (exact.isPresent()) {
            if (matches.stream()
                    .filter(item -> item.validationDefinitionChecksum()
                            .equals(metric.validationDefinitionChecksum()))
                    .filter(item -> item.protocolKey().equals(metric.protocolKey()))
                    .filter(item -> item.unit().equals(metric.unit()))
                    .count() > 1) {
                throw new IllegalArgumentException(
                        "duplicate compatible observations for " + model.candidateId()
                                + "/" + metric.metricId());
            }
            CandidateMetricObservation item = exact.orElseThrow();
            if (item.state() == ObservationState.UNEVALUATED) {
                return new ModelMetricCell(
                        model, ComparisonCellState.UNEVALUATED, item.value(),
                        item.provenanceReferences(), item.reason());
            }
            return new ModelMetricCell(
                    model, ComparisonCellState.EVALUATED, item.value(),
                    item.provenanceReferences(), item.reason());
        }

        CandidateMetricObservation item = matches.getFirst();
        if (!item.validationDefinitionChecksum().equals(metric.validationDefinitionChecksum())) {
            return incompatible(model, item, ComparisonCellState.INCOMPATIBLE_VALIDATION_DEFINITION,
                    "metric used a different validation definition checksum");
        }
        if (!item.protocolKey().equals(metric.protocolKey())) {
            return incompatible(model, item, ComparisonCellState.INCOMPATIBLE_PROTOCOL,
                    "metric used a different scientific protocol");
        }
        return incompatible(model, item, ComparisonCellState.INCOMPATIBLE_UNIT,
                "metric used a different unit");
    }

    private ModelMetricCell missing(ModelReference model, String reason) {
        return new ModelMetricCell(
                model, ComparisonCellState.MISSING, java.util.OptionalDouble.empty(),
                List.of(), reason);
    }

    private ModelMetricCell incompatible(
            ModelReference model,
            CandidateMetricObservation observation,
            ComparisonCellState state,
            String reason) {
        return new ModelMetricCell(
                model, state, java.util.OptionalDouble.empty(),
                observation.provenanceReferences(), reason);
    }

    private void rejectDuplicateModelIds(List<ModelReference> models) {
        Set<String> ids = new HashSet<>();
        for (ModelReference model : models) {
            if (!ids.add(model.candidateId())) {
                throw new IllegalArgumentException("duplicate candidate id: " + model.candidateId());
            }
        }
    }

    private void rejectUnknownModels(
            List<ModelReference> models,
            List<CandidateMetricObservation> observations) {
        Set<ModelReference> known = Set.copyOf(models);
        for (CandidateMetricObservation observation : observations) {
            if (!known.contains(observation.model())) {
                throw new IllegalArgumentException(
                        "observation references a model outside comparison: "
                                + observation.model().candidateId());
            }
        }
    }
}
