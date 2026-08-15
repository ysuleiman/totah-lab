package totah.lab.prometheus.reporting;

import java.io.IOException;
import java.io.OutputStream;
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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import totah.lab.prometheus.diagnosis.FunctionalFormDiagnostic;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.inventory.ProvenanceGap;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.RequirementResolution;

/** Deterministic renderer for the required TSL review deliverables. */
public final class ReviewDeliverableRenderer {

    public static final List<String> DELIVERABLE_FILENAMES = List.of(
            "PROMETHEUS_TSL_EVIDENCE_INVENTORY.md",
            "PROMETHEUS_TSL_EVIDENCE_INVENTORY.csv",
            "PROMETHEUS_TSL_PROTOCOL_GROUPS.csv",
            "PROMETHEUS_TSL_MODEL_DIAGNOSIS.md",
            "PROMETHEUS_TSL_STRATEGY_COMPARISON.md",
            "PROMETHEUS_TSL_MISSING_EVIDENCE_PLAN.csv",
            "PROMETHEUS_TSL_COST_ESTIMATE.md",
            "PROMETHEUS_TSL_EXECUTION_DECISION.json");

    private final ObjectMapper json = JsonMapper.builder()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    public void write(Path outputDirectory, ReviewDeliverableInput input) throws IOException {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(input, "input");
        Files.createDirectories(outputDirectory);
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(0), inventoryMarkdown(input));
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(1), inventoryCsv(input));
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(2), protocolCsv(input));
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(3), diagnosisMarkdown(input));
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(4), strategyMarkdown(input));
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(5), planCsv(input));
        writeText(outputDirectory, DELIVERABLE_FILENAMES.get(6), costMarkdown(input));
        try (OutputStream output = Files.newOutputStream(
                outputDirectory.resolve(DELIVERABLE_FILENAMES.get(7)))) {
            json.writeValue(output, input.executionDecision());
        }
        writeManifest(outputDirectory);
    }

    private String inventoryMarkdown(ReviewDeliverableInput input) {
        StringBuilder out = new StringBuilder("# Prometheus TSL Evidence Inventory\n\n");
        out.append("Quantum evidence: ").append(input.inventory().quantum().totalCount()).append("\n\n")
                .append("Classical evidence: ").append(input.inventory().classical().totalCount()).append("\n\n")
                .append("Provenance gaps: ").append(input.inventory().provenanceGaps().size()).append("\n");
        appendDimension(out, "Quantum", input.inventory().quantum());
        appendDimension(out, "Classical", input.inventory().classical());
        if (!input.inventory().provenanceGaps().isEmpty()) {
            out.append("\n## Provenance gaps\n\n");
            input.inventory().provenanceGaps().stream()
                    .sorted(Comparator.comparing(ProvenanceGap::evidenceHash)
                            .thenComparing(gap -> gap.type().name()))
                    .forEach(gap -> out.append("- ").append(gap.dimension()).append(" ")
                            .append(gap.evidenceHash()).append(": ")
                            .append(gap.type()).append(" — ").append(gap.detail()).append("\n"));
        }
        return out.toString();
    }

    private static void appendDimension(
            StringBuilder out,
            String label,
            totah.lab.prometheus.inventory.EvidenceDimensionSummary summary) {
        out.append("\n## ").append(label).append(" evidence by acceptance\n\n");
        summary.countsByAcceptance().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n"));
        out.append("\n## ").append(label).append(" evidence by calculation type\n\n");
        summary.countsByCalculationType().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.append("- ").append(entry.getKey()).append(": ")
                        .append(entry.getValue()).append("\n"));
        out.append("\n## ").append(label).append(" evidence by protocol\n\n");
        summary.countsByProtocol().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> out.append("- `").append(entry.getKey()).append("`: ")
                        .append(entry.getValue()).append("\n"));
    }

    private String inventoryCsv(ReviewDeliverableInput input) {
        List<String> rows = new ArrayList<>();
        rows.add("dimension,evidence_hash,molecule_id,calculation_type,protocol_key,acceptance,source_path,source_sha256");
        input.evidence().quantum().stream()
                .sorted(Comparator.comparing(item -> item.identity().evidenceHash()))
                .map(this::quantumRow).forEach(rows::add);
        input.evidence().classical().stream()
                .sorted(Comparator.comparing(item -> item.identity().evidenceHash()))
                .map(this::classicalRow).forEach(rows::add);
        return String.join("\n", rows) + "\n";
    }

    private String quantumRow(QuantumEvidence item) {
        return csv("QUANTUM", item.identity().evidenceHash(), item.identity().molecule().moleculeId(),
                item.identity().calculationType().name(), item.identity().protocol().protocolKey(),
                item.acceptance().name(), item.provenance().sourcePath(), item.provenance().sha256());
    }

    private String classicalRow(ClassicalEvidence item) {
        return csv("CLASSICAL", item.identity().evidenceHash(), item.identity().molecule().moleculeId(),
                item.identity().calculationType().name(), item.identity().protocol().protocolKey(),
                item.acceptance().name(), item.provenance().sourcePath(), item.provenance().sha256());
    }

    private String protocolCsv(ReviewDeliverableInput input) {
        List<String> rows = new ArrayList<>();
        rows.add("group_id,protocol_key,calculation_types,evidence_hashes,comparability_note");
        input.protocolGroups().stream().sorted(Comparator.comparing(ProtocolGroupRow::groupId))
                .map(row -> csv(row.groupId(), row.protocolKey(), String.join(";", row.calculationTypes()),
                        String.join(";", row.evidenceHashes()), row.comparabilityNote()))
                .forEach(rows::add);
        return String.join("\n", rows) + "\n";
    }

    private String diagnosisMarkdown(ReviewDeliverableInput input) {
        StringBuilder out = new StringBuilder("# Prometheus TSL Model Diagnosis\n\n");
        out.append("Molecule: ").append(input.diagnosis().molecule().moleculeId()).append("\n");
        for (FunctionalFormDiagnostic diagnostic : input.diagnosis().diagnostics()) {
            out.append("\n## ").append(diagnostic.classification()).append("\n\n")
                    .append("Reasons: ").append(String.join("; ", diagnostic.reasons())).append("\n\n")
                    .append("Evidence: ")
                    .append(String.join("; ", diagnostic.supportingEvidenceHashes())).append("\n\n")
                    .append("Diagnostic version: ").append(diagnostic.diagnosticVersion()).append("\n");
        }
        return out.toString();
    }

    private String strategyMarkdown(ReviewDeliverableInput input) {
        StringBuilder out = new StringBuilder("# Prometheus TSL Strategy Comparison\n\n")
                .append("| Strategy | Method family | Readiness | Reusable evidence | Missing evidence | Reasons |\n")
                .append("|---|---|---|---|---|---|\n");
        input.strategyComparisons().stream()
                .sorted(Comparator.comparing(StrategyComparisonRow::strategyId))
                .forEach(row -> out.append("| ").append(md(row.strategyId())).append(" | ")
                        .append(md(row.methodFamily())).append(" | ").append(md(row.readiness())).append(" | ")
                        .append(md(String.join("; ", row.reusableEvidenceHashes()))).append(" | ")
                        .append(md(String.join("; ", row.missingEvidencePurposes()))).append(" | ")
                        .append(md(String.join("; ", row.reasons()))).append(" |\n"));
        return out.toString();
    }

    private String planCsv(ReviewDeliverableInput input) {
        List<String> rows = new ArrayList<>();
        rows.add("purpose,molecule_id,calculation_type,protocol_key,dataset_role,decision,reusable_evidence_hashes,reason");
        input.missingEvidencePlan().resolutions().stream()
                .sorted(Comparator.comparing(item -> item.requirement().purpose()))
                .map(this::resolutionRow).forEach(rows::add);
        return String.join("\n", rows) + "\n";
    }

    private String resolutionRow(RequirementResolution resolution) {
        var requirement = resolution.requirement();
        return csv(requirement.purpose(), requirement.molecule().moleculeId(),
                requirement.calculationType().name(), requirement.protocol().protocolKey(),
                requirement.role().name(), resolution.decision().name(),
                String.join(";", resolution.reusableEvidenceHashes()), resolution.reason());
    }

    private String costMarkdown(ReviewDeliverableInput input) {
        CostEstimate cost = input.missingEvidencePlan().totalCost();
        StringBuilder out = new StringBuilder("# Prometheus TSL Cost Estimate\n\n")
                .append("Jobs: ").append(cost.jobCount()).append("\n\n")
                .append("CPU hours per job (aggregate field): ")
                .append(cost.cpuHoursPerJob()).append("\n\n")
                .append("Expected wall hours: ").append(cost.expectedWallHours()).append("\n\n")
                .append("Expected local runtime hours: ")
                .append(cost.expectedLocalRuntimeHours()).append("\n\n")
                .append("Estimated remote cost USD: ")
                .append(cost.estimatedRemoteCostUsd()).append("\n");
        if (!input.costNotes().isEmpty()) {
            out.append("\n## Caller-supplied assumptions and ranges\n\n");
            input.costNotes().forEach(note -> out.append("- ").append(note).append("\n"));
        }
        return out.toString();
    }

    private void writeManifest(Path outputDirectory) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String filename : DELIVERABLE_FILENAMES.stream().sorted().toList()) {
            byte[] bytes = Files.readAllBytes(outputDirectory.resolve(filename));
            lines.add(sha256(bytes) + "  " + filename);
        }
        writeText(outputDirectory, "SHA256SUMS", String.join("\n", lines) + "\n");
    }

    private void writeText(Path directory, String filename, String value) throws IOException {
        Files.writeString(directory.resolve(filename), value, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String csv(String... values) {
        return java.util.Arrays.stream(values).map(ReviewDeliverableRenderer::csvField)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String csvField(String value) {
        Objects.requireNonNull(value, "CSV value");
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String md(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }
}
