package totah.lab.report.analysis;

import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static totah.lab.report.model.DockingAggregateKeys.CHAIN;
import static totah.lab.report.model.DockingAggregateKeys.CONTACTING_LIGAND_FRACTION;
import static totah.lab.report.model.DockingAggregateKeys.ENRICHMENT_RATIO;
import static totah.lab.report.model.DockingAggregateKeys.RESIDUE_NAME;
import static totah.lab.report.model.DockingAggregateKeys.RESIDUE_NUMBER;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_CONTACTING_LIGAND_FRACTION;

/**
 * Selects descriptive docking-signal leaders without assigning biological
 * residue roles. Role assignment requires a separately agreed scientific
 * policy.
 */
public final class DefaultPocketHotspotAnalyzer
        implements PocketHotspotAnalyzer {

    @Override
    public PocketAnalysisResult analyze(
            PocketAnalysisResult residues,
            PocketAnalysisResult docking,
            PocketReportConfiguration configuration
    ) {
        Objects.requireNonNull(residues, "residues");
        Objects.requireNonNull(docking, "docking");
        Objects.requireNonNull(configuration, "configuration");

        List<Map<String, Object>> rows = residueRows(docking);
        Map<String, Object> values = new LinkedHashMap<>();
        List<ReportEvidence> evidence = new ArrayList<>();

        maximum(rows, CONTACTING_LIGAND_FRACTION)
                .ifPresent(candidate -> {
                    values.put("contactFrequencyLeader",
                            candidateValue(candidate));
                    evidence.add(evidence(
                            "H-001",
                            candidate,
                            CONTACTING_LIGAND_FRACTION,
                            "has the highest observed unique-ligand contact "
                                    + "fraction in this pocket"
                    ));
                });
        maximum(rows, ENRICHMENT_RATIO)
                .ifPresent(candidate -> {
                    values.put("enrichmentLeader",
                            candidateValue(candidate));
                    evidence.add(evidence(
                            "H-002",
                            candidate,
                            ENRICHMENT_RATIO,
                            "has the highest available enrichment ratio "
                                    + "in this pocket"
                    ));
                });
        maximumScoreFilteredIncrease(rows)
                .ifPresent(candidate -> {
                    values.put("scoreFilteredContactIncreaseLeader",
                            candidateValue(candidate));
                    double increase = candidate.scoreFilteredIncrease();
                    evidence.add(new ReportEvidence(
                            "H-003",
                            EvidenceCategory.HOTSPOT,
                            label(candidate.row())
                                    + " has the largest observed increase in "
                                    + "unique-ligand contact fraction after "
                                    + "score filtering ("
                                    + percent(increase) + ").",
                            Map.of(
                                    "contactFractionIncrease",
                                    increase,
                                    CONTACTING_LIGAND_FRACTION,
                                    requiredMetric(
                                            candidate.row(),
                                            CONTACTING_LIGAND_FRACTION),
                                    SCORE_FILTERED_CONTACTING_LIGAND_FRACTION,
                                    requiredMetric(
                                            candidate.row(),
                                            SCORE_FILTERED_CONTACTING_LIGAND_FRACTION)
                            )
                    ));
                });
        values.put("roleAssignments", List.of());
        values.put(
                "roleAssignmentStatus",
                "NOT_ASSIGNED_WITHOUT_SCIENTIFIC_POLICY"
        );
        return new PocketAnalysisResult(values, evidence);
    }

    private Optional<Candidate> maximum(
            List<Map<String, Object>> rows,
            String metric
    ) {
        return rows.stream()
                .filter(row -> optionalMetric(row, metric).isPresent())
                .map(row -> new Candidate(
                        row,
                        requiredMetric(row, metric),
                        Double.NaN
                ))
                .max(Comparator
                        .comparingDouble(Candidate::metricValue)
                        .thenComparing(candidate -> label(candidate.row())));
    }

    private Optional<Candidate> maximumScoreFilteredIncrease(
            List<Map<String, Object>> rows
    ) {
        return rows.stream()
                .filter(row -> optionalMetric(
                        row,
                        CONTACTING_LIGAND_FRACTION).isPresent())
                .filter(row -> optionalMetric(
                        row,
                        SCORE_FILTERED_CONTACTING_LIGAND_FRACTION).isPresent())
                .map(row -> new Candidate(
                        row,
                        requiredMetric(
                                row,
                                SCORE_FILTERED_CONTACTING_LIGAND_FRACTION),
                        requiredMetric(
                                row,
                                SCORE_FILTERED_CONTACTING_LIGAND_FRACTION)
                                - requiredMetric(
                                        row,
                                        CONTACTING_LIGAND_FRACTION)
                ))
                .max(Comparator
                        .comparingDouble(Candidate::scoreFilteredIncrease)
                        .thenComparing(candidate -> label(candidate.row())));
    }

    private ReportEvidence evidence(
            String id,
            Candidate candidate,
            String metric,
            String description
    ) {
        return new ReportEvidence(
                id,
                EvidenceCategory.HOTSPOT,
                label(candidate.row()) + " " + description + " ("
                        + decimal(candidate.metricValue()) + ").",
                Map.of(metric, candidate.metricValue())
        );
    }

    private Map<String, Object> candidateValue(Candidate candidate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(CHAIN, candidate.row().get(CHAIN));
        value.put(RESIDUE_NUMBER, candidate.row().get(RESIDUE_NUMBER));
        value.put(RESIDUE_NAME, candidate.row().get(RESIDUE_NAME));
        value.put("metricValue", candidate.metricValue());
        if (Double.isFinite(candidate.scoreFilteredIncrease())) {
            value.put(
                    "contactFractionIncrease",
                    candidate.scoreFilteredIncrease()
            );
        }
        return Map.copyOf(value);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> residueRows(
            PocketAnalysisResult docking
    ) {
        Object value = docking.values().get("residues");
        if (!(value instanceof List<?> rows)) {
            throw new IllegalArgumentException(
                    "Docking analysis has no residue list");
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                        "Docking residue list contains a non-object value");
            }
        }
        return (List<Map<String, Object>>) (List<?>) rows;
    }

    private Optional<Double> optionalMetric(
            Map<String, Object> row,
            String name
    ) {
        Object value = row.get(name);
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result)) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    private double requiredMetric(
            Map<String, Object> row,
            String name
    ) {
        return optionalMetric(row, name).orElseThrow();
    }

    private String label(Map<String, Object> row) {
        return row.get(CHAIN) + ":" + row.get(RESIDUE_NAME)
                + row.get(RESIDUE_NUMBER);
    }

    private String percent(double fraction) {
        return String.format(Locale.ROOT, "%+.1f%%", fraction * 100.0);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record Candidate(
            Map<String, Object> row,
            double metricValue,
            double scoreFilteredIncrease
    ) {
    }
}
