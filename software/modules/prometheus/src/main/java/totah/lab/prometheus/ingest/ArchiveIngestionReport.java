package totah.lab.prometheus.ingest;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.ingest.LegacyPhase2ArchiveIngester.IngestionResult;

/**
 * Summary counts and renderers for a {@link IngestionResult}. Purely
 * derived — every number is computed from the ingested bundle, branch outcomes
 * and issues; nothing is restated from the archive by hand.
 */
public final class ArchiveIngestionReport {

    private final IngestionResult result;

    public ArchiveIngestionReport(IngestionResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }

    public int totalEvidence() {
        return result.bundle().size();
    }

    public int acceptedCount() {
        int count = 0;
        for (QuantumEvidence evidence : result.bundle().quantum()) {
            if (evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED) {
                count++;
            }
        }
        for (ClassicalEvidence evidence : result.bundle().classical()) {
            if (evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED) {
                count++;
            }
        }
        return count;
    }

    public Map<EvidenceAcceptanceState, Integer> byAcceptanceState() {
        Map<EvidenceAcceptanceState, Integer> counts = new EnumMap<>(EvidenceAcceptanceState.class);
        for (QuantumEvidence evidence : result.bundle().quantum()) {
            counts.merge(evidence.acceptance(), 1, Integer::sum);
        }
        for (ClassicalEvidence evidence : result.bundle().classical()) {
            counts.merge(evidence.acceptance(), 1, Integer::sum);
        }
        return counts;
    }

    public Map<String, Integer> byProtocolKey() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (QuantumEvidence evidence : result.bundle().quantum()) {
            counts.merge(evidence.identity().protocol().protocolKey(), 1, Integer::sum);
        }
        for (ClassicalEvidence evidence : result.bundle().classical()) {
            counts.merge(evidence.identity().protocol().protocolKey(), 1, Integer::sum);
        }
        return counts;
    }

    public Map<CalculationType, Integer> byCalculationType() {
        Map<CalculationType, Integer> counts = new EnumMap<>(CalculationType.class);
        for (QuantumEvidence evidence : result.bundle().quantum()) {
            counts.merge(evidence.identity().calculationType(), 1, Integer::sum);
        }
        for (ClassicalEvidence evidence : result.bundle().classical()) {
            counts.merge(evidence.identity().calculationType(), 1, Integer::sum);
        }
        return counts;
    }

    public int uniqueGeometries() {
        Map<String, Integer> geometries = new LinkedHashMap<>();
        for (QuantumEvidence evidence : result.bundle().quantum()) {
            geometries.merge(evidence.identity().geometry().sha256(), 1, Integer::sum);
        }
        for (ClassicalEvidence evidence : result.bundle().classical()) {
            geometries.merge(evidence.identity().geometry().sha256(), 1, Integer::sum);
        }
        return geometries.size();
    }

    public int failedBranchCount() {
        return result.branchOutcomes().size();
    }

    public int issueCount() {
        return result.issues().size();
    }

    /** One CSV row per evidence object: quantum and classical. */
    public String toCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("evidenceHash,calculationType,protocolKey,geometrySha256Short,acceptance,convergence,energy,sourcePath\n");
        for (QuantumEvidence evidence : result.bundle().quantum()) {
            String energy = evidence.energyHartree().map(Object::toString)
                    .orElseGet(() -> evidence.interactionEnergyKcalMol()
                            .map(v -> v + "_kcal_mol")
                            .orElse(""));
            row(csv,
                    evidence.identity().evidenceHash(),
                    evidence.identity().calculationType().name(),
                    evidence.identity().protocol().protocolKey(),
                    shortSha(evidence.identity().geometry().sha256()),
                    evidence.acceptance().name(),
                    evidence.convergence().name(),
                    energy,
                    evidence.provenance().sourcePath());
        }
        for (ClassicalEvidence evidence : result.bundle().classical()) {
            row(csv,
                    evidence.identity().evidenceHash(),
                    evidence.identity().calculationType().name(),
                    evidence.identity().protocol().protocolKey(),
                    shortSha(evidence.identity().geometry().sha256()),
                    evidence.acceptance().name(),
                    "-",
                    evidence.decomposition().totalKcalMol() + "_kcal_mol",
                    evidence.provenance().sourcePath());
        }
        return csv.toString();
    }

    /** Markdown summary: headline counts, then per-dimension breakdowns. */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# TSL-RSH archive ingestion report\n\n");
        md.append("- total evidence: ").append(totalEvidence()).append('\n');
        md.append("- accepted: ").append(acceptedCount()).append('\n');
        md.append("- unique geometries: ").append(uniqueGeometries()).append('\n');
        md.append("- closed branches recovered: ").append(failedBranchCount()).append('\n');
        md.append("- ingestion issues: ").append(issueCount()).append('\n');

        md.append("\n## Evidence by acceptance state\n\n");
        byAcceptanceState().forEach((state, count) ->
                md.append("- ").append(state).append(": ").append(count).append('\n'));

        md.append("\n## Evidence by calculation type\n\n");
        byCalculationType().forEach((type, count) ->
                md.append("- ").append(type).append(": ").append(count).append('\n'));

        md.append("\n## Evidence by protocol\n\n");
        Map<String, Integer> byProtocol = byProtocolKey();
        new TreeMap<>(byProtocol).forEach((key, count) -> {
            String description = result.protocolRegistry().getOrDefault(key, "");
            md.append("- `").append(key).append("`")
                    .append(description.isEmpty() ? "" : " — " + description)
                    .append(": ").append(count).append('\n');
        });

        md.append("\n## Branch outcomes\n\n");
        for (var branch : result.branchOutcomes()) {
            md.append("- ").append(branch.branch())
                    .append(": `").append(branch.classification()).append('`')
                    .append(" (").append(branch.evidenceClass()).append(')')
                    .append('\n');
        }

        if (!result.issues().isEmpty()) {
            md.append("\n## Issues\n\n");
            for (var issue : result.issues()) {
                md.append("- [").append(issue.severity()).append("] ")
                        .append(issue.path()).append(": ")
                        .append(issue.message()).append('\n');
            }
        }
        return md.toString();
    }

    private static void row(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(fields[i]));
        }
        csv.append('\n');
    }

    private static String escape(String field) {
        String value = field == null ? "" : field;
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String shortSha(String sha256) {
        return sha256.length() > 12 ? sha256.substring(0, 12) : sha256;
    }
}
