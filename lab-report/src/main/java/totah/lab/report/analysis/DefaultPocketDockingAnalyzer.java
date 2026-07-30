package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.ResidueRef;
import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.config.PocketReportThresholds;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;
import totah.lab.report.model.DockingAggregateKeys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static totah.lab.report.model.DockingAggregateKeys.AVERAGE_CONTACTING_SCORE;
import static totah.lab.report.model.DockingAggregateKeys.AVERAGE_LIGAND_MIN_DISTANCE;
import static totah.lab.report.model.DockingAggregateKeys.AVERAGE_POSE_MIN_DISTANCE;
import static totah.lab.report.model.DockingAggregateKeys.BEST_CONTACTING_SCORE;
import static totah.lab.report.model.DockingAggregateKeys.CHAIN;
import static totah.lab.report.model.DockingAggregateKeys.CLOSEST_DISTANCE;
import static totah.lab.report.model.DockingAggregateKeys.CONTACTING_LIGAND_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.CONTACTING_LIGAND_FRACTION;
import static totah.lab.report.model.DockingAggregateKeys.CONTACTING_POSE_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.CONTACTING_POSE_FRACTION;
import static totah.lab.report.model.DockingAggregateKeys.CONTACT_FRACTION_DIFFERENCE;
import static totah.lab.report.model.DockingAggregateKeys.CONTACT_SCORE_THRESHOLD;
import static totah.lab.report.model.DockingAggregateKeys.ENRICHMENT_RATIO;
import static totah.lab.report.model.DockingAggregateKeys.LOG2_ENRICHMENT;
import static totah.lab.report.model.DockingAggregateKeys.MEDIAN_CONTACTING_SCORE;
import static totah.lab.report.model.DockingAggregateKeys.RESIDUE_ID;
import static totah.lab.report.model.DockingAggregateKeys.RESIDUE_NAME;
import static totah.lab.report.model.DockingAggregateKeys.RESIDUE_NUMBER;
import static totah.lab.report.model.DockingAggregateKeys.RESIDUE_ROLES;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_BANDS;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_CONTACTING_LIGAND_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_CONTACTING_LIGAND_FRACTION;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_CONTACTING_POSE_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_CONTACTING_POSE_FRACTION;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_LIGAND_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_POSE_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_LIGAND_RETENTION;
import static totah.lab.report.model.DockingAggregateKeys.SCORE_FILTERED_POSE_RETENTION;
import static totah.lab.report.model.DockingAggregateKeys.TOTAL_LIGAND_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.TOTAL_POSE_COUNT;
import static totah.lab.report.model.DockingAggregateKeys.WORST_CONTACTING_SCORE;

public final class DefaultPocketDockingAnalyzer
        implements PocketDockingAnalyzer {

    private final PocketReportThresholds thresholds;

    public DefaultPocketDockingAnalyzer() {
        this(PocketReportThresholds.defaults());
    }

    public DefaultPocketDockingAnalyzer(
            PocketReportThresholds thresholds
    ) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
    }

    private static final List<String> OPTIONAL_RESIDUE_METRICS = List.of(
            RESIDUE_ID,
            RESIDUE_NAME,
            CONTACTING_LIGAND_COUNT,
            CONTACTING_LIGAND_FRACTION,
            CONTACTING_POSE_COUNT,
            CONTACTING_POSE_FRACTION,
            SCORE_FILTERED_LIGAND_COUNT,
            SCORE_FILTERED_CONTACTING_LIGAND_COUNT,
            SCORE_FILTERED_CONTACTING_LIGAND_FRACTION,
            SCORE_FILTERED_POSE_COUNT,
            SCORE_FILTERED_CONTACTING_POSE_COUNT,
            SCORE_FILTERED_CONTACTING_POSE_FRACTION,
            ENRICHMENT_RATIO,
            LOG2_ENRICHMENT,
            CONTACT_FRACTION_DIFFERENCE,
            AVERAGE_CONTACTING_SCORE,
            MEDIAN_CONTACTING_SCORE,
            BEST_CONTACTING_SCORE,
            WORST_CONTACTING_SCORE,
            CLOSEST_DISTANCE,
            AVERAGE_LIGAND_MIN_DISTANCE,
            AVERAGE_POSE_MIN_DISTANCE
    );

    @Override
    public PocketAnalysisResult analyze(
            Pocket pocket,
            Map<String, Object> aggregatedDockingData,
            PocketReportConfiguration configuration
    ) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(aggregatedDockingData,
                "aggregatedDockingData");
        Objects.requireNonNull(configuration, "configuration");

        long totalLigands = requiredLong(
                aggregatedDockingData,
                TOTAL_LIGAND_COUNT
        );
        long totalPoses = requiredLong(
                aggregatedDockingData,
                TOTAL_POSE_COUNT
        );
        requireNonNegative(totalLigands, TOTAL_LIGAND_COUNT);
        requireNonNegative(totalPoses, TOTAL_POSE_COUNT);
        double scoreThreshold = requiredDouble(
                aggregatedDockingData,
                CONTACT_SCORE_THRESHOLD
        );
        List<Map<String, Object>> sourceRows = rows(
                aggregatedDockingData,
                DockingAggregateKeys.RESIDUES
        );
        long filteredLigands = totalFromAggregateOrRows(
                aggregatedDockingData,
                sourceRows,
                SCORE_FILTERED_LIGAND_COUNT,
                totalLigands
        );
        long filteredPoses = totalFromAggregateOrRows(
                aggregatedDockingData,
                sourceRows,
                SCORE_FILTERED_POSE_COUNT,
                totalPoses
        );
        Set<ResidueKey> pocketResidues = pocketResidues(pocket);
        List<Map<String, Object>> residueRows = new ArrayList<>();
        List<ReportEvidence> evidence = new ArrayList<>();

        for (Map<String, Object> sourceRow : sourceRows) {
            ResidueKey key = residueKey(sourceRow);
            if (!pocketResidues.contains(key)) {
                continue;
            }
            Map<String, Object> row = copyResidueMetrics(sourceRow, key);
            applyEnrichmentAndRoles(row, filteredLigands);
            validateResidueRow(row, totalLigands, totalPoses);
            residueRows.add(row);
            evidence.add(residueEvidence(key, row));
        }

        Map<String, Object> values = new LinkedHashMap<>();
        copyIfPresent(aggregatedDockingData, values,
                DockingAggregateKeys.RUN_ID);
        values.put(TOTAL_LIGAND_COUNT, totalLigands);
        values.put(TOTAL_POSE_COUNT, totalPoses);
        values.put(CONTACT_SCORE_THRESHOLD, scoreThreshold);
        values.put(SCORE_FILTERED_LIGAND_COUNT, filteredLigands);
        values.put(SCORE_FILTERED_POSE_COUNT, filteredPoses);
        values.put(
                SCORE_FILTERED_LIGAND_RETENTION,
                fraction(filteredLigands, totalLigands)
        );
        values.put(
                SCORE_FILTERED_POSE_RETENTION,
                fraction(filteredPoses, totalPoses)
        );
        values.put(
                "filterValidationStatus",
                filterValidationStatus(
                        totalLigands,
                        filteredLigands,
                        residueRows
                )
        );
        values.put("analyzedPocketResidueCount", residueRows.size());
        values.put(DockingAggregateKeys.RESIDUES, List.copyOf(residueRows));
        values.put(SCORE_BANDS, pocketScoreBands(
                aggregatedDockingData,
                pocketResidues
        ));

        evidence.add(0, new ReportEvidence(
                "D-001",
                EvidenceCategory.DOCKING,
                dockingScopeStatement(
                        totalLigands,
                        totalPoses,
                        filteredLigands,
                        filteredPoses,
                        scoreThreshold
                ),
                Map.of(
                        TOTAL_LIGAND_COUNT, (double) totalLigands,
                        TOTAL_POSE_COUNT, (double) totalPoses,
                        SCORE_FILTERED_LIGAND_COUNT,
                        (double) filteredLigands,
                        SCORE_FILTERED_POSE_COUNT,
                        (double) filteredPoses,
                        CONTACT_SCORE_THRESHOLD, scoreThreshold
                )
        ));
        return new PocketAnalysisResult(values, evidence);
    }

    private ReportEvidence residueEvidence(
            ResidueKey key,
            Map<String, Object> row
    ) {
        double ligandFraction = optionalDouble(
                row,
                CONTACTING_LIGAND_FRACTION
        ).orElseThrow();
        double poseFraction = optionalDouble(
                row,
                CONTACTING_POSE_FRACTION
        ).orElseThrow();
        String residueName = (String) row.get(RESIDUE_NAME);
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put(CONTACTING_LIGAND_FRACTION, ligandFraction);
        metrics.put(CONTACTING_POSE_FRACTION, poseFraction);
        optionalDouble(row, ENRICHMENT_RATIO)
                .ifPresent(value -> metrics.put(ENRICHMENT_RATIO, value));
        optionalDouble(row, CLOSEST_DISTANCE)
                .ifPresent(value -> metrics.put(CLOSEST_DISTANCE, value));

        return new ReportEvidence(
                evidenceId(key),
                EvidenceCategory.DOCKING,
                key.label(residueName) + " contacts "
                        + percent(ligandFraction)
                        + " of unique ligands and "
                        + percent(poseFraction) + " of poses.",
                Map.copyOf(metrics)
        );
    }

    private Map<String, Object> copyResidueMetrics(
            Map<String, Object> source,
            ResidueKey key
    ) {
        Map<String, Object> copy = new LinkedHashMap<>();
        copy.put(CHAIN, key.chain());
        copy.put(RESIDUE_NUMBER, key.number());
        OPTIONAL_RESIDUE_METRICS.forEach(metric ->
                copyIfPresent(source, copy, metric));
        return copy;
    }

    private void applyEnrichmentAndRoles(
            Map<String, Object> row,
            long filteredLigands
    ) {
        double contactFraction = requiredDouble(
                row,
                CONTACTING_LIGAND_FRACTION
        );
        java.util.Optional<Double> filteredFraction = optionalDouble(
                row,
                SCORE_FILTERED_CONTACTING_LIGAND_FRACTION
        );
        row.remove(ENRICHMENT_RATIO);
        row.remove(LOG2_ENRICHMENT);
        boolean lowConfidence =
                filteredLigands < thresholds.minimumFilteredLigands();
        row.put(
                DockingAggregateKeys.ENRICHMENT_LOW_CONFIDENCE,
                lowConfidence
        );
        double enrichment = Double.NaN;
        if (contactFraction > 0.0 && filteredFraction.isPresent()) {
            enrichment = filteredFraction.get() / contactFraction;
            row.put(ENRICHMENT_RATIO, enrichment);
            if (enrichment > 0.0) {
                row.put(
                        LOG2_ENRICHMENT,
                        Math.log(enrichment) / Math.log(2.0)
                );
            }
        }

        List<String> roles = new ArrayList<>();
        if (contactFraction >= thresholds.coreContactFraction()) {
            roles.add("CORE_CONTACT");
        } else if (contactFraction >= thresholds.frequentContactFraction()) {
            roles.add("FREQUENT_CONTACT");
        } else if (contactFraction >= thresholds.variableContactFraction()) {
            roles.add("VARIABLE_CONTACT");
        } else {
            roles.add("PERIPHERAL");
        }
        if (Double.isFinite(enrichment)
                && contactFraction > 0.0
                && enrichment >= thresholds.stronglyEnrichedRatio()) {
            roles.add("STRONGLY_ENRICHED");
        } else if (Double.isFinite(enrichment)
                && contactFraction > 0.0
                && enrichment >= thresholds.enrichedRatio()) {
            roles.add("ENRICHED");
        }
        if ("CYS".equals(row.get(RESIDUE_NAME))) {
            roles.add("CYSTEINE");
        }
        row.put(RESIDUE_ROLES, List.copyOf(roles));
    }

    private long totalFromAggregateOrRows(
            Map<String, Object> aggregate,
            List<Map<String, Object>> rows,
            String key,
            long fallback
    ) {
        Object aggregateValue = aggregate.get(key);
        if (aggregateValue instanceof Number number) {
            return number.longValue();
        }
        if (!rows.isEmpty() && rows.getFirst().get(key)
                instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    private double fraction(long count, long total) {
        return total == 0 ? 0.0 : (double) count / total;
    }

    private String filterValidationStatus(
            long totalLigands,
            long filteredLigands,
            List<Map<String, Object>> rows
    ) {
        if (filteredLigands == totalLigands) {
            return "ALL_LIGANDS_PASS_THRESHOLD";
        }
        boolean identical = rows.stream().allMatch(row ->
                optionalDouble(row, CONTACTING_LIGAND_FRACTION)
                        .equals(optionalDouble(
                                row,
                                SCORE_FILTERED_CONTACTING_LIGAND_FRACTION
                        )));
        return identical
                ? "FILTERED_AND_OVERALL_FRACTIONS_IDENTICAL"
                : "FILTER_APPLIED";
    }

    private String dockingScopeStatement(
            long totalLigands,
            long totalPoses,
            long filteredLigands,
            long filteredPoses,
            double threshold
    ) {
        String poseScope = totalLigands > 0 && totalPoses == totalLigands
                ? totalLigands + " docked ligands with 1 pose per ligand"
                : totalLigands + " unique ligands and " + totalPoses
                        + " poses";
        return "Docking evidence contains " + poseScope + "; "
                + filteredLigands + " ligands and " + filteredPoses
                + " poses pass the score threshold below "
                + decimal(threshold) + " ("
                + percent(fraction(filteredLigands, totalLigands))
                + " of ligands retained).";
    }

    private void validateResidueRow(
            Map<String, Object> row,
            long totalLigands,
            long totalPoses
    ) {
        Object residueName = row.get(RESIDUE_NAME);
        if (!(residueName instanceof String name) || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Docking residue row requires a non-blank residueName");
        }
        long ligandCount = requiredLong(row, CONTACTING_LIGAND_COUNT);
        long poseCount = requiredLong(row, CONTACTING_POSE_COUNT);
        requireCountWithinTotal(
                ligandCount,
                totalLigands,
                CONTACTING_LIGAND_COUNT
        );
        requireCountWithinTotal(
                poseCount,
                totalPoses,
                CONTACTING_POSE_COUNT
        );
        requireFraction(row, CONTACTING_LIGAND_FRACTION);
        requireFraction(row, CONTACTING_POSE_FRACTION);
    }

    private void requireCountWithinTotal(
            long count,
            long total,
            String name
    ) {
        if (count < 0 || count > total) {
            throw new IllegalArgumentException(
                    name + " must be between zero and its total");
        }
    }

    private void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    name + " must not be negative");
        }
    }

    private void requireFraction(
            Map<String, Object> values,
            String key
    ) {
        double fraction = requiredDouble(values, key);
        if (fraction < 0.0 || fraction > 1.0) {
            throw new IllegalArgumentException(
                    key + " must be between zero and one");
        }
    }

    private List<Map<String, Object>> pocketScoreBands(
            Map<String, Object> aggregatedDockingData,
            Set<ResidueKey> pocketResidues
    ) {
        if (!aggregatedDockingData.containsKey(SCORE_BANDS)) {
            return List.of();
        }
        return rows(aggregatedDockingData, SCORE_BANDS).stream()
                .filter(row -> pocketResidues.contains(residueKey(row)))
                .map(Map::copyOf)
                .toList();
    }

    private Set<ResidueKey> pocketResidues(Pocket pocket) {
        Set<ResidueKey> keys = new LinkedHashSet<>();
        for (ResidueRef reference : pocket.getResidueRefs()) {
            keys.add(new ResidueKey(reference.chain(), reference.number()));
        }
        return Set.copyOf(keys);
    }

    private ResidueKey residueKey(Map<String, Object> row) {
        Object chainValue = row.get(CHAIN);
        if (!(chainValue instanceof String chain) || chain.isBlank()) {
            throw new IllegalArgumentException(
                    "Docking residue row requires a non-blank chain");
        }
        return new ResidueKey(chain, Math.toIntExact(
                requiredLong(row, RESIDUE_NUMBER)));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(
            Map<String, Object> data,
            String key
    ) {
        Object value = data.get(key);
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException(
                    "Docking aggregate requires list field " + key);
        }
        for (Object row : values) {
            if (!(row instanceof Map<?, ?>)) {
                throw new IllegalArgumentException(
                        "Docking aggregate " + key
                                + " must contain object rows");
            }
        }
        return (List<Map<String, Object>>) (List<?>) values;
    }

    private long requiredLong(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(
                    "Docking aggregate requires numeric field " + key);
        }
        return number.longValue();
    }

    private double requiredDouble(Map<String, Object> values, String key) {
        return optionalDouble(values, key).orElseThrow(() ->
                new IllegalArgumentException(
                        "Docking aggregate requires finite numeric field "
                                + key));
    }

    private java.util.Optional<Double> optionalDouble(
            Map<String, Object> values,
            String key
    ) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result)) {
                return java.util.Optional.of(result);
            }
        }
        return java.util.Optional.empty();
    }

    private void copyIfPresent(
            Map<String, Object> source,
            Map<String, Object> destination,
            String key
    ) {
        Object value = source.get(key);
        if (value != null) {
            destination.put(key, value);
        }
    }

    private String evidenceId(ResidueKey key) {
        String safeChain = key.chain().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_");
        return "D-R-" + safeChain + "-" + key.number();
    }

    private String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record ResidueKey(String chain, int number) {

        private ResidueKey {
            Objects.requireNonNull(chain, "chain");
        }

        private String label(String residueName) {
            return chain + ":" + residueName + number;
        }
    }
}
