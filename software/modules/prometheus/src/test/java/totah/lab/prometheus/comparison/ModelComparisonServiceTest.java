package totah.lab.prometheus.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalDouble;

import org.junit.jupiter.api.Test;

class ModelComparisonServiceTest {

    private static final ModelReference GAFF2 =
            new ModelReference("GAFF2", "gaff2-checksum", List.of("/models/gaff2/provenance.json"));
    private static final ModelReference QUBE =
            new ModelReference("QUBE", "qube-checksum", List.of("/models/qube/provenance.json"));
    private static final ValidationMetricDefinition ENERGY_RMSE = new ValidationMetricDefinition(
            MetricDimension.ENERGY,
            "holdout-rmse",
            "kcal/mol",
            "PBE|def2-SVP|D3(BJ)|none|false|ORCA|5.0.4",
            "validation-plan-checksum");

    @Test
    void comparesOnlyMetricsWithTheSameProtocolAndValidationDefinition() {
        CandidateMetricObservation baseline = evaluated(GAFF2, ENERGY_RMSE, 2.4, "raw-gaff2-hash");
        CandidateMetricObservation candidate = evaluated(QUBE, ENERGY_RMSE, 1.8, "raw-qube-hash");

        ModelComparisonResult result = new ModelComparisonService().compare(
                List.of(GAFF2, QUBE), List.of(ENERGY_RMSE), List.of(baseline, candidate));

        ModelComparisonRow row = result.rows().getFirst();
        assertThat(row.cells()).extracting(ModelMetricCell::state)
                .containsExactly(ComparisonCellState.EVALUATED, ComparisonCellState.EVALUATED);
        assertThat(row.cells().get(0).value()).hasValue(2.4);
        assertThat(row.cells().get(1).value()).hasValue(1.8);
        assertThat(row.cells().get(1).model().candidateChecksum()).isEqualTo("qube-checksum");
        assertThat(row.cells().get(1).provenanceReferences()).containsExactly("raw-qube-hash");
    }

    @Test
    void explicitlyMarksMissingUnevaluatedAndIncompatibleMetrics() {
        CandidateMetricObservation incompatibleProtocol = new CandidateMetricObservation(
                GAFF2,
                ENERGY_RMSE.dimension(),
                ENERGY_RMSE.metricId(),
                ENERGY_RMSE.unit(),
                "PBE0|def2-TZVP|D3(BJ)|none|false|ORCA|5.0.4",
                ENERGY_RMSE.validationDefinitionChecksum(),
                ObservationState.EVALUATED,
                OptionalDouble.of(2.1),
                List.of("different-protocol-result"),
                "already computed elsewhere");
        CandidateMetricObservation unevaluated = new CandidateMetricObservation(
                QUBE,
                ENERGY_RMSE.dimension(),
                ENERGY_RMSE.metricId(),
                ENERGY_RMSE.unit(),
                ENERGY_RMSE.protocolKey(),
                ENERGY_RMSE.validationDefinitionChecksum(),
                ObservationState.UNEVALUATED,
                OptionalDouble.empty(),
                List.of("preregistered-plan"),
                "holdout was not opened");

        ModelComparisonResult result = new ModelComparisonService().compare(
                List.of(GAFF2, QUBE),
                List.of(
                        ENERGY_RMSE,
                        new ValidationMetricDefinition(
                                MetricDimension.GEOMETRY, "bond-rms", "angstrom",
                                "geometry-protocol", "validation-plan-checksum")),
                List.of(incompatibleProtocol, unevaluated));

        assertThat(result.rows().get(0).cells()).extracting(ModelMetricCell::state)
                .containsExactly(
                        ComparisonCellState.INCOMPATIBLE_PROTOCOL,
                        ComparisonCellState.UNEVALUATED);
        assertThat(result.rows().get(1).cells()).extracting(ModelMetricCell::state)
                .containsOnly(ComparisonCellState.MISSING);
        assertThat(result.rows().get(0).cells()).allSatisfy(cell -> {
            if (cell.state() != ComparisonCellState.EVALUATED) {
                assertThat(cell.value()).isEmpty();
                assertThat(cell.reason()).isNotBlank();
            }
        });
    }

    @Test
    void keepsScientificDimensionsSeparateAndDefinesNoMasterScore() {
        ValidationMetricDefinition force = new ValidationMetricDefinition(
                MetricDimension.FORCE,
                "force-rmse",
                "kcal/mol/angstrom",
                ENERGY_RMSE.protocolKey(),
                ENERGY_RMSE.validationDefinitionChecksum());
        ModelComparisonResult result = new ModelComparisonService().compare(
                List.of(GAFF2),
                List.of(ENERGY_RMSE, force),
                List.of(
                        evaluated(GAFF2, ENERGY_RMSE, 2.4, "energy-source"),
                        evaluated(GAFF2, force, 4.2, "force-source")));

        assertThat(result.rowsFor(MetricDimension.ENERGY)).hasSize(1);
        assertThat(result.rowsFor(MetricDimension.FORCE)).hasSize(1);
        assertThat(ModelComparisonResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("models", "rows");
        assertThatThrownBy(() -> result.rows().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsObservationFromDifferentCandidateChecksum() {
        ModelReference altered =
                new ModelReference("GAFF2", "post-validation-change", List.of("/changed"));
        CandidateMetricObservation observation = evaluated(
                altered, ENERGY_RMSE, 1.0, "changed-result");

        assertThatThrownBy(() -> new ModelComparisonService().compare(
                List.of(GAFF2), List.of(ENERGY_RMSE), List.of(observation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside comparison");
    }

    @Test
    void refusesDuplicateCompatibleObservationsInsteadOfChoosingOne() {
        CandidateMetricObservation first = evaluated(GAFF2, ENERGY_RMSE, 2.4, "first");
        CandidateMetricObservation second = evaluated(GAFF2, ENERGY_RMSE, 2.3, "second");

        assertThatThrownBy(() -> new ModelComparisonService().compare(
                List.of(GAFF2), List.of(ENERGY_RMSE), List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate compatible observations");
    }

    private static CandidateMetricObservation evaluated(
            ModelReference model,
            ValidationMetricDefinition definition,
            double value,
            String provenance) {
        return new CandidateMetricObservation(
                model,
                definition.dimension(),
                definition.metricId(),
                definition.unit(),
                definition.protocolKey(),
                definition.validationDefinitionChecksum(),
                ObservationState.EVALUATED,
                OptionalDouble.of(value),
                List.of(provenance),
                "precomputed metric");
    }
}
