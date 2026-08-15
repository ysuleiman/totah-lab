package totah.lab.prometheus.recovery.authoritative;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.ingest.authoritative.AmberRespReader;
import totah.lab.prometheus.ingest.authoritative.AuthoritativeOptimizationReader;
import totah.lab.prometheus.ingest.authoritative.AuthoritativeOptimizationRecord;
import totah.lab.prometheus.ingest.authoritative.AuthoritativeProbeReader;
import totah.lab.prometheus.ingest.authoritative.AuthoritativeProbeRecord;
import totah.lab.prometheus.ingest.authoritative.HistoricalValueComparison;
import totah.lab.prometheus.ingest.authoritative.PyscfGeometricArtifactReader;
import totah.lab.prometheus.ingest.authoritative.PyscfGeometricOptimization;
import totah.lab.prometheus.inventory.EvidenceInventoryService;
import totah.lab.prometheus.inventory.ProvenanceGap;
import totah.lab.prometheus.inventory.ProvenanceGapType;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryAuditEntry;
import totah.lab.prometheus.recovery.RecoveryAuditRenderer;
import totah.lab.prometheus.recovery.RecoveryAuditReport;
import totah.lab.prometheus.recovery.RecoveryClassification;
import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

/**
 * Read-only end-to-end scientific reconstruction audit. It loads the frozen
 * canonical generation only to obtain the exact gap list and historical record
 * identities; all recovered values come from authoritative raw artifacts.
 */
public final class AuthoritativeScientificAuditRunner {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PYSCF_VERSION = Pattern.compile("PySCF version\\s+([^\\s]+)");

    /** Runs the audit and writes deterministic publication-facing artifacts. */
    public Result run(Path archiveRoot, Path evidenceStore, Path outputDirectory) throws IOException {
        Objects.requireNonNull(archiveRoot, "archiveRoot");
        Objects.requireNonNull(evidenceStore, "evidenceStore");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        CanonicalEvidenceStore.LoadedEvidence loaded = new CanonicalEvidenceStore().loadCurrent(evidenceStore);
        EvidenceMemoryIndex index = loaded.index();
        RecoveryAuditReport report = recoverGaps(archiveRoot, index,
                loaded.manifest().importDescriptor().generationId());
        new RecoveryAuditRenderer().write(outputDirectory, report);

        Reconstruction reconstruction = reconstructTrustedEvidence(archiveRoot);
        writeReconstruction(outputDirectory, reconstruction);
        writeStrategyReassessment(outputDirectory, report, reconstruction);
        writeChecksums(outputDirectory);
        return new Result(report, reconstruction);
    }

    /** Reusable in-memory recovery pass; reads raw artifacts and performs no writes. */
    public RecoveryAuditReport recoverGaps(
            Path archiveRoot, EvidenceMemoryIndex index, String sourceGenerationId) {
        List<ProvenanceGap> gaps = new EvidenceInventoryService(index).snapshot().provenanceGaps();
        List<RecoveryAuditEntry> entries = gaps.stream()
                .sorted(Comparator.comparing(ProvenanceGap::evidenceHash).thenComparing(gap -> gap.type().name()))
                .map(gap -> recoverGap(archiveRoot, index, gap))
                .toList();
        return new RecoveryAuditReport(sourceGenerationId, entries);
    }

    private RecoveryAuditEntry recoverGap(Path archiveRoot, EvidenceMemoryIndex index, ProvenanceGap gap) {
        Historical historical = historical(index, gap);
        RecoveredField<String> recovery;
        try {
            recovery = switch (gap.type()) {
                case PROTOCOL_METHOD_MISSING -> recoverMethod(archiveRoot, historical.provenance());
                case PROTOCOL_SOFTWARE_MISSING -> recoverSoftware(archiveRoot, historical.provenance());
                case SOFTWARE_VERSION_MISSING -> recoverSoftwareVersion(archiveRoot, historical.provenance());
                case SOURCE_PATH_MISSING, SOURCE_CHECKSUM_MISSING,
                        TOPOLOGY_REFERENCE_MISSING, DERIVED_EVIDENCE_NOT_IN_INVENTORY ->
                        RecoveredField.unrecoverable(gap.type().name(),
                                "No authoritative artifact linkage exists in the frozen canonical record");
            };
        } catch (IOException exception) {
            recovery = RecoveredField.unrecoverable(gap.type().name(),
                    "Authoritative artifact recovery failed: " + exception.getMessage());
        }
        return new RecoveryAuditEntry(gap.evidenceHash(), gap.type().name(), historical.value(),
                rename(recovery, gap.type().name()), Optional.empty());
    }

    private RecoveredField<String> recoverMethod(Path archiveRoot, EvidenceProvenance provenance) throws IOException {
        Optional<JsonField> field = jsonField(archiveRoot, provenance,
                List.of("method", "geometry_method", "energy_method"));
        if (field.isPresent()) {
            return raw("PROTOCOL_METHOD_MISSING", field.get().value(), field.get().path(), field.get().pointer(),
                    "structured calculation input/output JSON field");
        }
        return RecoveredField.unrecoverable("PROTOCOL_METHOD_MISSING",
                "No method field exists in an artifact linked to this calculation");
    }

    private RecoveredField<String> recoverSoftware(Path archiveRoot, EvidenceProvenance provenance)
            throws IOException {
        Optional<JsonField> pyscf = softwareJsonField(archiveRoot, provenance, "pyscf");
        if (pyscf.isPresent()) {
            return environment("PROTOCOL_SOFTWARE_MISSING", "PySCF", pyscf.get().path(),
                    pyscf.get().pointer(), "structured calculation environment metadata");
        }
        Optional<LogMatch> log = pyscfLog(archiveRoot, provenance);
        if (log.isPresent()) {
            return raw("PROTOCOL_SOFTWARE_MISSING", "PySCF", log.get().path(),
                    "line " + log.get().line(), "PySCF version banner parser");
        }
        return RecoveredField.unrecoverable("PROTOCOL_SOFTWARE_MISSING",
                "No software identity exists in a calculation-linked input, environment file, or raw output");
    }

    private RecoveredField<String> recoverSoftwareVersion(Path archiveRoot, EvidenceProvenance provenance)
            throws IOException {
        Optional<JsonField> pyscf = softwareJsonField(archiveRoot, provenance, "pyscf");
        if (pyscf.isPresent()) {
            return environment("SOFTWARE_VERSION_MISSING", pyscf.get().value(), pyscf.get().path(),
                    pyscf.get().pointer(), "structured calculation environment metadata");
        }
        Optional<LogMatch> log = pyscfLog(archiveRoot, provenance);
        if (log.isPresent()) {
            return raw("SOFTWARE_VERSION_MISSING", log.get().value(), log.get().path(),
                    "line " + log.get().line(), "PySCF version banner parser");
        }
        return RecoveredField.unrecoverable("SOFTWARE_VERSION_MISSING",
                "No exact version exists in a calculation-linked environment artifact or raw output");
    }

    private static Optional<JsonField> softwareJsonField(
            Path archiveRoot, EvidenceProvenance provenance, String key) throws IOException {
        for (Path candidate : jsonCandidates(archiveRoot, provenance)) {
            JsonNode root = JSON.readTree(candidate.toFile());
            JsonNode value = root.path("software").get(key);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                return Optional.of(new JsonField(value.asText(), candidate, "/software/" + key));
            }
        }
        return Optional.empty();
    }

    private static Optional<JsonField> jsonField(
            Path archiveRoot, EvidenceProvenance provenance, List<String> names) throws IOException {
        for (Path candidate : jsonCandidates(archiveRoot, provenance)) {
            JsonNode root = JSON.readTree(candidate.toFile());
            for (String name : names) {
                JsonNode value = root.get(name);
                if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                    return Optional.of(new JsonField(value.asText(), candidate, "/" + name));
                }
            }
        }
        return Optional.empty();
    }

    private static List<Path> jsonCandidates(Path archiveRoot, EvidenceProvenance provenance) {
        if (provenance.sourcePath().isBlank()) {
            return List.of();
        }
        Path source = resolve(archiveRoot, provenance.sourcePath());
        List<Path> candidates = new ArrayList<>();
        if (source.getFileName().toString().endsWith(".json") && Files.isRegularFile(source)) {
            candidates.add(source);
        }
        Path parent = source.getParent();
        if (parent != null) {
            addIfRegular(candidates, parent.resolve("input.json"));
            String name = source.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot > 0) {
                addIfRegular(candidates, parent.resolve(name.substring(0, dot) + ".json"));
            }
            addIfRegular(candidates, parent.resolve("software_environment.json"));
        }
        return candidates.stream().distinct().toList();
    }

    private static Optional<LogMatch> pyscfLog(Path archiveRoot, EvidenceProvenance provenance) throws IOException {
        if (provenance.sourcePath().isBlank()) {
            return Optional.empty();
        }
        Path source = resolve(archiveRoot, provenance.sourcePath());
        Path parent = source.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(parent)) {
            for (Path log : files.filter(path -> path.getFileName().toString().endsWith(".log")).sorted().toList()) {
                List<String> lines = Files.readAllLines(log);
                for (int i = 0; i < lines.size(); i++) {
                    Matcher matcher = PYSCF_VERSION.matcher(lines.get(i));
                    if (matcher.find()) {
                        return Optional.of(new LogMatch(matcher.group(1), log, i + 1));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Reconstruction reconstructTrustedEvidence(Path archiveRoot) throws IOException {
        Path unit05O = archiveRoot.resolve("execution-unit-05O");
        PyscfGeometricArtifactReader pyscf = new PyscfGeometricArtifactReader();
        List<PyscfGeometricOptimization> minima = new ArrayList<>();
        for (String id : List.of("MIN01", "MIN02", "MIN04")) {
            minima.add(pyscf.readOptimization(unit05O.resolve("qm-native-minima").resolve(id)));
            pyscf.readHessian(unit05O.resolve("hessians").resolve(id));
        }
        List<HistoricalValueComparison> comparisons = new ArrayList<>(pyscf.compareHistoricalEnergies(minima,
                unit05O.resolve("QM_NATIVE_ENDPOINT_SUMMARY.csv"), "minimum_id", "energy_hartree", 5.0e-10));

        AuthoritativeOptimizationReader optimizations = new AuthoritativeOptimizationReader();
        List<AuthoritativeOptimizationRecord> unit05hRecords = readOptimizationDirectories(
                archiveRoot.resolve("execution-unit-05H/points"), optimizations);
        comparisons.addAll(compare05h(unit05hRecords,
                archiveRoot.resolve("execution-unit-05H/HIGHER_LEVEL_CONFIRMATION_RESULTS.csv"), 5.0e-10));
        List<AuthoritativeOptimizationRecord> unit05lRecords = readOptimizationDirectories(
                archiveRoot.resolve("execution-unit-05L/points"), optimizations);
        Map<Integer, Double> parents = new LinkedHashMap<>();
        unit05hRecords.forEach(record -> {
            String id = record.pointId().value().orElseThrow();
            if (id.equals("phi060_psi+060")) parents.put(60, record.finalEnergyHartree().value().orElseThrow());
            if (id.equals("phi300_psi+060")) parents.put(300, record.finalEnergyHartree().value().orElseThrow());
        });
        comparisons.addAll(optimizations.compareHistoricalRelativeEnergies(unit05lRecords, parents,
                archiveRoot.resolve("execution-unit-05L/SPARSE_TWO_ANGLE_QM_RESULTS.csv"), 1.0e-6));

        AuthoritativeProbeReader probeReader = new AuthoritativeProbeReader();
        List<AuthoritativeProbeRecord> probeRecords = probeReader.readShardedDataset(
                unit05O.resolve("vdw-probe-validation-aws"),
                unit05O.resolve("final-19-point-force-field-diagnostic/ALL_19_PROBE_GEOMETRY_AUDIT.csv"));
        comparisons.addAll(probeReader.compareHistoricalInteractionEnergies(probeRecords,
                unit05O.resolve("final-19-point-force-field-diagnostic/MASTER_19_POINT_TABLE.csv"), 1.0e-8));
        new AmberRespReader().read(unit05O.resolve(
                "native-amber-resp3min-hf631gd/regeneration-A/all-three"));
        return new Reconstruction(3, 3, unit05hRecords.size(), unit05lRecords.size(), probeRecords.size(), 1,
                comparisons);
    }

    private static List<HistoricalValueComparison> compare05h(
            List<AuthoritativeOptimizationRecord> records, Path csv, double tolerance) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        String[] header = lines.getFirst().split(",", -1);
        int phi = indexOf(header, "phi_target_deg");
        int psi = indexOf(header, "psi_target_deg");
        int energy = indexOf(header, "energy_hartree");
        Map<String, Cell> cells = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",", -1);
            int p = Integer.parseInt(values[phi]);
            int s = Integer.parseInt(values[psi]);
            String id = "phi%03d_psi%s%03d".formatted(p, s >= 0 ? "+" : "-", Math.abs(s));
            cells.put(id, new Cell(Double.parseDouble(values[energy]), i + 1));
        }
        String checksum = ArtifactChecksums.sha256(csv);
        List<HistoricalValueComparison> result = new ArrayList<>();
        for (AuthoritativeOptimizationRecord record : records) {
            String id = record.pointId().value().orElseThrow();
            Cell cell = cells.get(id);
            if (cell == null) throw new IOException("05H historical CSV missing " + id);
            double recovered = record.finalEnergyHartree().value().orElseThrow();
            double difference = Math.abs(recovered - cell.value());
            result.add(new HistoricalValueComparison(id, "energy_hartree", recovered, cell.value(), difference,
                    difference <= tolerance, csv.toString(), checksum,
                    "line " + cell.line() + ", column energy_hartree"));
        }
        return result;
    }

    private static int indexOf(String[] fields, String expected) throws IOException {
        for (int i = 0; i < fields.length; i++) if (fields[i].equals(expected)) return i;
        throw new IOException("missing CSV column " + expected);
    }

    private static List<AuthoritativeOptimizationRecord> readOptimizationDirectories(
            Path root, AuthoritativeOptimizationReader reader) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("optimization root absent: " + root);
        }
        List<AuthoritativeOptimizationRecord> records = new ArrayList<>();
        try (Stream<Path> directories = Files.list(root)) {
            for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
                if (Files.isRegularFile(directory.resolve("result.json"))) {
                    records.add(reader.read(directory));
                }
            }
        }
        return List.copyOf(records);
    }

    private static void writeReconstruction(Path output, Reconstruction result) throws IOException {
        Files.createDirectories(output);
        List<String> csv = new ArrayList<>();
        csv.add("record_id,field,recovered_value,historical_value,absolute_difference,matches_tolerance,historical_source,historical_checksum,historical_locator");
        for (HistoricalValueComparison value : result.historicalComparisons()) {
            csv.add(String.join(",", quote(value.recordId()), quote(value.field()),
                    Double.toString(value.recoveredValue()), Double.toString(value.historicalValue()),
                    Double.toString(value.absoluteDifference()), Boolean.toString(value.matchesTolerance()),
                    quote(value.historicalSourcePath()), quote(value.historicalSourceSha256()),
                    quote(value.historicalLocator())));
        }
        Files.writeString(output.resolve("AUTHORITATIVE_RECONSTRUCTION_COMPARISONS.csv"),
                String.join("\n", csv) + "\n", StandardCharsets.UTF_8);
        String markdown = """
                # Authoritative scientific reconstruction

                - QM-native minima reconstructed: %d
                - Analytic Hessians reconstructed: %d
                - Unit 05H constrained calculations reconstructed: %d
                - Unit 05L constrained calculations reconstructed: %d
                - Counterpoise probe calculations reconstructed: %d
                - Multiconformer RESP fits reconstructed: %d
                - Historical scalar comparisons: %d
                - Historical comparisons within tolerance: %d

                No canonical evidence was modified by this audit.
                """.formatted(result.minima(), result.hessians(), result.unit05h(), result.unit05l(),
                result.probes(), result.resp(), result.historicalComparisons().size(),
                result.historicalComparisons().stream().filter(HistoricalValueComparison::matchesTolerance).count());
        Files.writeString(output.resolve("AUTHORITATIVE_RECONSTRUCTION_SUMMARY.md"), markdown,
                StandardCharsets.UTF_8);
    }

    private static void writeStrategyReassessment(
            Path output, RecoveryAuditReport audit, Reconstruction reconstruction) throws IOException {
        long recovered = audit.entries().stream()
                .filter(entry -> entry.recovery().classification()
                        != RecoveryClassification.GENUINELY_UNRECOVERABLE).count();
        long unrecoverable = audit.entries().size() - recovered;
        boolean comparisonsPass = reconstruction.historicalComparisons().stream()
                .allMatch(HistoricalValueComparison::matchesTolerance);
        String classification = comparisonsPass
                ? "VERIFIED_RAW_FOUNDATION_READY_FOR_CANONICAL_REGENERATION"
                : "RAW_RECONSTRUCTION_DISCREPANCY_REQUIRES_REVIEW";
        String json = """
                {
                  "classification" : "%s",
                  "canonical_evidence_modified" : false,
                  "fields_audited" : %d,
                  "fields_recovered" : %d,
                  "fields_genuinely_unrecoverable" : %d,
                  "raw_records_reconstructed" : %d,
                  "historical_comparisons" : %d,
                  "all_historical_comparisons_pass" : %s,
                  "strategy_conclusion" : "Trusted QM/RESP evidence is reconstructable; production force-field readiness must be reassessed only after a separately authorized canonical regeneration."
                }
                """.formatted(classification, audit.entries().size(), recovered, unrecoverable,
                reconstruction.totalRecords(), reconstruction.historicalComparisons().size(), comparisonsPass);
        Files.writeString(output.resolve("AUTHORITATIVE_STRATEGY_REASSESSMENT.json"), json,
                StandardCharsets.UTF_8);
        String markdown = """
                # Strategy reassessment from authoritative raw evidence

                Classification: `%s`

                - Fields audited: %d
                - Recovered from linked authoritative artifacts: %d
                - Genuinely unrecoverable after conservative search: %d
                - Trusted raw calculation records reconstructed: %d
                - Historical derived-value comparisons: %d (%s)

                The raw QM minima, Hessians, Unit 05H/05L calculations, probe calculations, and accepted RESP
                fit form a reproducible scientific foundation. This does not itself validate a production force
                field. The earlier strategy decision must be rerun only after a separately authorized canonical
                regeneration incorporates these recovered fields.
                """.formatted(classification, audit.entries().size(), recovered, unrecoverable,
                reconstruction.totalRecords(), reconstruction.historicalComparisons().size(),
                comparisonsPass ? "all pass" : "one or more fail");
        Files.writeString(output.resolve("AUTHORITATIVE_STRATEGY_REASSESSMENT.md"), markdown,
                StandardCharsets.UTF_8);
    }

    private static void writeChecksums(Path output) throws IOException {
        List<String> rows = new ArrayList<>();
        try (Stream<Path> files = Files.list(output)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SHA256SUMS"))
                    .sorted().toList()) {
                rows.add(ArtifactChecksums.sha256(file) + "  " + file.getFileName());
            }
        }
        Files.writeString(output.resolve("SHA256SUMS"), String.join("\n", rows) + "\n",
                StandardCharsets.UTF_8);
    }

    private static Historical historical(EvidenceMemoryIndex index, ProvenanceGap gap) {
        Optional<QuantumEvidence> quantum = index.quantum(gap.evidenceHash());
        if (quantum.isPresent()) {
            return new Historical(protocolValue(quantum.get(), gap.type()), quantum.get().provenance());
        }
        ClassicalEvidence classical = index.classical(gap.evidenceHash()).orElseThrow();
        return new Historical(protocolValue(classical, gap.type()), classical.provenance());
    }

    private static String protocolValue(QuantumEvidence evidence, ProvenanceGapType type) {
        return switch (type) {
            case PROTOCOL_METHOD_MISSING -> evidence.identity().protocol().method();
            case PROTOCOL_SOFTWARE_MISSING -> evidence.identity().protocol().software();
            case SOFTWARE_VERSION_MISSING -> evidence.identity().protocol().softwareVersion();
            default -> "";
        };
    }

    private static String protocolValue(ClassicalEvidence evidence, ProvenanceGapType type) {
        return switch (type) {
            case PROTOCOL_METHOD_MISSING -> evidence.identity().protocol().method();
            case PROTOCOL_SOFTWARE_MISSING -> evidence.identity().protocol().software();
            case SOFTWARE_VERSION_MISSING -> evidence.identity().protocol().softwareVersion();
            case TOPOLOGY_REFERENCE_MISSING -> evidence.topologyReference();
            default -> "";
        };
    }

    private static RecoveredField<String> rename(RecoveredField<String> field, String name) {
        return new RecoveredField<>(name, field.value(), field.classification(), field.provenance(), field.rationale());
    }

    private static RecoveredField<String> raw(
            String name, String value, Path path, String locator, String extraction) throws IOException {
        return recovered(name, value, RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                path, locator, extraction);
    }

    private static RecoveredField<String> environment(
            String name, String value, Path path, String locator, String extraction) throws IOException {
        return recovered(name, value, RecoveryClassification.RECOVERABLE_FROM_SOFTWARE_ENVIRONMENT_ARTIFACT,
                path, locator, extraction);
    }

    private static RecoveredField<String> recovered(
            String name, String value, RecoveryClassification classification,
            Path path, String locator, String extraction) throws IOException {
        return new RecoveredField<>(name, Optional.of(value), classification,
                List.of(new FieldSourceProvenance(path.toString(), ArtifactChecksums.sha256(path), locator, extraction)),
                "Recovered from an artifact directly linked to the canonical calculation record");
    }

    private static Path resolve(Path archiveRoot, String sourcePath) {
        Path source = Path.of(sourcePath);
        return source.isAbsolute() ? source : archiveRoot.resolve(source).normalize();
    }

    private static void addIfRegular(List<Path> paths, Path candidate) {
        if (Files.isRegularFile(candidate)) {
            paths.add(candidate);
        }
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** CLI: archive-root evidence-store output-directory. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "usage: AuthoritativeScientificAuditRunner <archive-root> <evidence-store> <output-directory>");
        }
        Result result = new AuthoritativeScientificAuditRunner().run(
                Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]));
        System.out.printf(Locale.ROOT, "audited=%d reconstructed=%d%n",
                result.audit().entries().size(), result.reconstruction().totalRecords());
    }

    public record Result(RecoveryAuditReport audit, Reconstruction reconstruction) { }

    public record Reconstruction(
            int minima,
            int hessians,
            int unit05h,
            int unit05l,
            int probes,
            int resp,
            List<HistoricalValueComparison> historicalComparisons) {
        public Reconstruction {
            historicalComparisons = List.copyOf(historicalComparisons);
        }

        public int totalRecords() {
            return minima + hessians + unit05h + unit05l + probes + resp;
        }
    }

    private record Historical(String value, EvidenceProvenance provenance) { }
    private record JsonField(String value, Path path, String pointer) { }
    private record LogMatch(String value, Path path, int line) { }
    private record Cell(double value, int line) { }
}
