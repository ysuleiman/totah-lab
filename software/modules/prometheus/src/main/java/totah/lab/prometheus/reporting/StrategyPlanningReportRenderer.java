package totah.lab.prometheus.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Deterministically renders a strategy-planning evidence package; it executes no calculations. */
public final class StrategyPlanningReportRenderer {

    private static final List<String> OUTPUTS = List.of(
            "PROMETHEUS_STRATEGY_REQUIREMENT_MODEL.md",
            "PROMETHEUS_TSL_QUBE_REQUIREMENTS.csv",
            "PROMETHEUS_TSL_QFORCE_REQUIREMENTS.csv",
            "PROMETHEUS_TSL_FORCEBALANCE_REQUIREMENTS.csv",
            "PROMETHEUS_TSL_EVIDENCE_REUSE_MATRIX.csv",
            "PROMETHEUS_TSL_HOLDOUT_PLANS.md",
            "PROMETHEUS_TSL_MINIMUM_MISSING_EVIDENCE_PLAN.csv",
            "PROMETHEUS_TSL_STRATEGY_COST_COMPARISON.csv",
            "PROMETHEUS_TSL_STRATEGY_RECOMMENDATION.md",
            "PROMETHEUS_TSL_STRATEGY_DECISION.json");

    public void render(Path outputDirectory, StrategyPlanningReport report) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(report, "report");
        Files.createDirectories(outputDirectory);
        write(outputDirectory, OUTPUTS.get(0), report.requirementModel());
        writeCsv(outputDirectory, OUTPUTS.get(1), RequirementRow.header(), report.qubeRequirements());
        writeCsv(outputDirectory, OUTPUTS.get(2), RequirementRow.header(), report.qforceRequirements());
        writeCsv(outputDirectory, OUTPUTS.get(3), RequirementRow.header(), report.forceBalanceRequirements());
        writeCsv(outputDirectory, OUTPUTS.get(4), ReuseRow.header(), report.reuseMatrix());
        write(outputDirectory, OUTPUTS.get(5), report.holdoutPlans());
        writeCsv(outputDirectory, OUTPUTS.get(6), MissingEvidenceRow.header(), report.missingEvidence());
        writeCsv(outputDirectory, OUTPUTS.get(7), CostComparisonRow.header(), report.costComparison());
        write(outputDirectory, OUTPUTS.get(8), report.recommendation());
        write(outputDirectory, OUTPUTS.get(9), report.decisionJson());
        writeChecksums(outputDirectory);
    }

    private static void writeCsv(Path directory, String name, String header, List<? extends CsvRow> rows)
            throws IOException {
        StringBuilder text = new StringBuilder(header).append('\n');
        rows.stream().sorted(Comparator.comparing(CsvRow::sortKey))
                .forEach(row -> text.append(row.csv()).append('\n'));
        write(directory, name, text.toString());
    }

    private static void write(Path directory, String name, String content) throws IOException {
        Files.writeString(directory.resolve(name), content.endsWith("\n") ? content : content + "\n",
                StandardCharsets.UTF_8);
    }

    private static void writeChecksums(Path directory) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String name : OUTPUTS) {
            lines.add(sha256(Files.readAllBytes(directory.resolve(name))) + "  " + name);
        }
        Files.writeString(directory.resolve("SHA256SUMS"), String.join("\n", lines) + "\n",
                StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public interface CsvRow {
        String sortKey();
        String csv();
    }

    public record StrategyPlanningReport(String requirementModel,
            List<RequirementRow> qubeRequirements, List<RequirementRow> qforceRequirements,
            List<RequirementRow> forceBalanceRequirements, List<ReuseRow> reuseMatrix,
            String holdoutPlans, List<MissingEvidenceRow> missingEvidence,
            List<CostComparisonRow> costComparison, String recommendation, String decisionJson) {
        public StrategyPlanningReport {
            Objects.requireNonNull(requirementModel);
            qubeRequirements = List.copyOf(qubeRequirements);
            qforceRequirements = List.copyOf(qforceRequirements);
            forceBalanceRequirements = List.copyOf(forceBalanceRequirements);
            reuseMatrix = List.copyOf(reuseMatrix);
            Objects.requireNonNull(holdoutPlans);
            missingEvidence = List.copyOf(missingEvidence);
            costComparison = List.copyOf(costComparison);
            Objects.requireNonNull(recommendation);
            Objects.requireNonNull(decisionJson);
        }
    }

    public record RequirementRow(String strategy, String requirement, String protocolConstraint,
            boolean exactProtocol, String role, String decision, int matchedRecords,
            String derivation, String reason, String outputCapability) implements CsvRow {
        static String header() {
            return "strategy,requirement,protocol_constraint,exact_protocol,role,decision,matched_records,derivation,reason,output_capability";
        }
        @Override public String sortKey() { return strategy + "|" + role + "|" + requirement; }
        @Override public String csv() { return StrategyPlanningReportRenderer.csv(strategy, requirement, protocolConstraint, exactProtocol, role,
                decision, matchedRecords, derivation, reason, outputCapability); }
    }

    public record ReuseRow(String strategy, String requirement, String evidenceKind, String decision,
            int records, String protocol, String evidenceHashes, String scientificReason) implements CsvRow {
        static String header() {
            return "strategy,requirement,evidence_kind,decision,records,protocol,evidence_hashes,scientific_reason";
        }
        @Override public String sortKey() { return strategy + "|" + requirement; }
        @Override public String csv() { return StrategyPlanningReportRenderer.csv(strategy, requirement, evidenceKind, decision, records,
                protocol, evidenceHashes, scientificReason); }
    }

    public record MissingEvidenceRow(String strategy, String requirement, String missingValue,
            String reason, String protocol, int calculations, String dependencies, String estimatedLocalRuntime,
            String estimatedRemoteRuntime, String estimatedRemoteCost, String hardware,
            boolean licensedSoftware, String prerequisite) implements CsvRow {
        static String header() {
            return "strategy,requirement,missing_scientific_value,reason,proposed_protocol,calculations,dependencies,estimated_local_runtime,estimated_remote_runtime,estimated_remote_cost,hardware,licensed_software,prerequisite";
        }
        @Override public String sortKey() { return strategy + "|" + prerequisite + "|" + requirement; }
        @Override public String csv() { return StrategyPlanningReportRenderer.csv(strategy, requirement, missingValue, reason, protocol,
                calculations, dependencies, estimatedLocalRuntime, estimatedRemoteRuntime, estimatedRemoteCost,
                hardware, licensedSoftware, prerequisite); }
    }

    public record CostComparisonRow(String strategy, String functionalFormSuitability,
            int reusedRecords, int derivedValues, int newQmJobs, int newMmJobs, String dependencies,
            String wallTime, String cost, String openMm, String amber, String validationStrength,
            String primaryRisk, String recommendation) implements CsvRow {
        static String header() {
            return "strategy,functional_form_suitability,existing_evidence_reused,derived_values,new_qm_jobs,new_mm_jobs,external_dependencies,estimated_wall_time,estimated_cost,openmm_compatibility,amber_compatibility,validation_strength,primary_scientific_risk,recommendation";
        }
        @Override public String sortKey() { return strategy; }
        @Override public String csv() { return StrategyPlanningReportRenderer.csv(strategy, functionalFormSuitability, reusedRecords,
                derivedValues, newQmJobs, newMmJobs, dependencies, wallTime, cost, openMm, amber,
                validationStrength, primaryRisk, recommendation); }
    }

    private static String csv(Object... values) {
        List<String> cells = new ArrayList<>();
        for (Object value : values) {
            String cell = String.valueOf(value == null ? "" : value).replace("\"", "\"\"");
            cells.add("\"" + cell + "\"");
        }
        return String.join(",", cells);
    }
}
