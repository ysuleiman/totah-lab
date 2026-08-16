package totah.lab.prometheus.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EnergyDecomposition;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.EvidenceProvenance;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.GeometryIdentity;

/**
 * Targeted ingester recovering the TSL-RSH scientific evidence from the
 * mettl7-phase2 archive. Each known evidence location has its own ingester
 * method; nothing is parsed by blind recursive walk, and excluded trees
 * ({@link ArchiveExclusions}) are never descended.
 *
 * <p>Convergence is always taken from {@code result.json} status/scf fields —
 * never from log emptiness. An empty {@code raw_combined.log} (the disclosed
 * MIN02/MIN04 logging defect) is recorded as a note-level
 * {@link IngestionIssue}, not a failure. Classification strings of closed
 * branches are parsed out of decision JSON / Markdown reports, never hard-coded
 * (see {@link BranchClassificationParser} and
 * {@link FailedCandidateRecord#evidenceClassFor}).
 *
 * <p>Acceptance policy: ACCEPTED when converged and geometry-valid; the
 * 19-probe geometry audit CSV drives GEOMETRY_INVALID for
 * {@code EXCLUDE_PROBE_DESIGN_FAILURE} points; the format-rejected RESP
 * directory is kept as PROTOCOL_INCOMPLETE negative evidence; a SHA256SUMS
 * mismatch marks the evidence CHECKSUM_INVALID and raises an issue.
 */
public final class LegacyPhase2ArchiveIngester {

    private static final String UNIT_02 = "execution-unit-02";
    private static final String UNIT_05L = "execution-unit-05L";
    private static final String UNIT_05O = "execution-unit-05O";

    /** Closed development branches under execution-unit-05O, in report order. */
    private static final List<String> FAILED_BRANCH_DIRS = List.of(
            "thiol-literature-comparator",
            "charge-validation",
            "charge-validation/constrained-search",
            "offcenter-thiol-three-parameter",
            "local-polarization",
            "minimal-nonbonded-development",
            "short-range-sh-correction",
            "short-range-sh-correction/protein-carbonyl-transfer",
            "short-range-sh-correction/catalytic-scope-audit",
            "delta-model-data-audit",
            "hessian-bonded-v3",
            "replacement-sh-donor-final-evidence-package");

    /** The result of ingesting an archive tree. */
    public record IngestionResult(
            EvidenceBundle bundle,
            CanonicalAtomMap canonicalAtoms,
            List<FailedCandidateRecord> branchOutcomes,
            List<IngestionIssue> issues,
            Map<String, String> protocolRegistry) {

        public IngestionResult {
            Objects.requireNonNull(bundle, "bundle");
            Objects.requireNonNull(canonicalAtoms, "canonicalAtoms");
            branchOutcomes = List.copyOf(Objects.requireNonNull(branchOutcomes, "branchOutcomes"));
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            protocolRegistry = Map.copyOf(Objects.requireNonNull(protocolRegistry, "protocolRegistry"));
        }
    }

    /** Mutable per-run state, threaded through the targeted ingesters. */
    private static final class Context {
        final Path archiveRoot;
        final Instant ingestedAt = Instant.now();
        final EvidenceBundle bundle = new EvidenceBundle();
        final List<IngestionIssue> issues = new ArrayList<>();
        final List<FailedCandidateRecord> branchOutcomes = new ArrayList<>();
        final Map<String, String> protocolRegistry = new LinkedHashMap<>();
        final List<Sha256Index> checksumIndexes = new ArrayList<>();
        final Map<String, GeometryIdentity> minimaGeometries = new LinkedHashMap<>();
        final Map<String, String> minimaEvidenceHashes = new LinkedHashMap<>();
        final Map<String, String> probeEvidenceHashByPointId = new LinkedHashMap<>();
        final Map<String, Integer> probeAtomCountByPointId = new LinkedHashMap<>();
        final Set<String> coveredGeometryShas = new LinkedHashSet<>();
        int lastProbeAtomCount = 1;
        CanonicalAtomMap canonicalAtoms;
        String atomMapHash;

        Context(Path archiveRoot) {
            this.archiveRoot = archiveRoot;
        }

        String relativize(Path path) {
            Path absolute = path.toAbsolutePath().normalize();
            return absolute.startsWith(archiveRoot)
                    ? archiveRoot.relativize(absolute).toString()
                    : absolute.toString();
        }
    }

    /** Ingests the archive rooted at {@code archiveRoot}. */
    public IngestionResult ingest(Path archiveRoot) throws IOException {
        Objects.requireNonNull(archiveRoot, "archiveRoot");
        Path root = archiveRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IOException("archive root is not a directory: " + archiveRoot);
        }
        Context ctx = new Context(root);

        ctx.canonicalAtoms = LegacyCanonicalAtomLoader.load(root.resolve(UNIT_02));
        ctx.atomMapHash = ctx.canonicalAtoms.canonicalHash();

        buildChecksumIndexes(ctx, root);

        Path unit05O = root.resolve(UNIT_05O);
        if (Files.isDirectory(unit05O)) {
            ingestNativeMinima(ctx, unit05O.resolve("qm-native-minima"));
            ingestHessians(ctx, unit05O.resolve("hessians"));
            ingestElectrostaticDiagnostics(ctx, unit05O.resolve("electrostatic-diagnostics"));
            ingestNativeAmberResp(ctx, unit05O);
            ingestFormatRejectedResp(ctx, unit05O.resolve("native-amber-resp3min-hf631gd-format-rejected"));
            ingestVdwProbes(ctx, unit05O);
            ingestReplacementShDonor(ctx, unit05O);
            ingestGeometryInventory(ctx, unit05O.resolve("delta-model-data-audit"));
            ingestFailedBranches(ctx, unit05O);
        } else {
            ctx.issues.add(IngestionIssue.warning(UNIT_05O, "evidence unit directory not found"));
        }
        ingestAngleCrossDiagnostic(ctx, root.resolve(UNIT_05L));

        return new IngestionResult(
                ctx.bundle,
                ctx.canonicalAtoms,
                ctx.branchOutcomes,
                ctx.issues,
                ctx.protocolRegistry);
    }

    // ------------------------------------------------------------------
    // Checksum index
    // ------------------------------------------------------------------

    private void buildChecksumIndexes(Context ctx, Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root, 4)) {
            List<Path> sumsFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains("SHA256SUMS"))
                    .filter(p -> !ArchiveExclusions.isExcluded(p))
                    .sorted()
                    .toList();
            for (Path sums : sumsFiles) {
                try {
                    ctx.checksumIndexes.add(Sha256Index.parse(sums));
                } catch (IOException e) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(sums), "unparseable SHA256SUMS: " + e.getMessage()));
                }
            }
        }
    }

    /**
     * Verifies the named files of {@code dir} against every applicable checksum
     * index. Returns true on any mismatch (and records an error issue per file).
     */
    private boolean checksumMismatch(Context ctx, Path dir, String... fileNames) throws IOException {
        boolean mismatch = false;
        for (String name : fileNames) {
            Path file = dir.resolve(name).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                continue;
            }
            for (Sha256Index index : ctx.checksumIndexes) {
                Path base = index.baseDir();
                if (base == null || !file.startsWith(base)) {
                    continue;
                }
                Optional<String> expected = index.expectedHash(base.relativize(file).toString());
                if (expected.isEmpty()) {
                    continue;
                }
                String actual = Sha256Index.hashFile(file);
                if (!actual.equalsIgnoreCase(expected.get())) {
                    ctx.issues.add(IngestionIssue.error(
                            ctx.relativize(file),
                            "checksum mismatch: expected " + expected.get() + " but file hashes to " + actual));
                    mismatch = true;
                }
                break; // first index with an entry for this file wins
            }
        }
        return mismatch;
    }

    // ------------------------------------------------------------------
    // QM native minima (OPTIMIZATION)
    // ------------------------------------------------------------------

    private void ingestNativeMinima(Context ctx, Path minimaDir) throws IOException {
        if (!requireDir(ctx, minimaDir)) {
            return;
        }
        for (Path minDir : immediateSubdirs(minimaDir)) {
            Path inputFile = minDir.resolve("input.json");
            Path resultFile = minDir.resolve("result.json");
            Path finalXyz = minDir.resolve("final.xyz");
            if (!Files.isRegularFile(resultFile) || !Files.isRegularFile(finalXyz)) {
                ctx.issues.add(IngestionIssue.warning(
                        ctx.relativize(minDir), "minimum directory lacks result.json or final.xyz; skipped"));
                continue;
            }
            try {
                JsonNode input = Files.isRegularFile(inputFile) ? JsonArtifacts.readTree(inputFile) : null;
                JsonNode result = JsonArtifacts.readTree(resultFile);

                String method = firstText(input, "method").orElse(null);
                if (method == null) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(inputFile), "input.json has no method string; skipped"));
                    continue;
                }
                QmProtocol protocol = QmProtocolParser.fromMethodString(
                        method, "PySCF", softwareVersion(input, "pyscf"));
                registerProtocol(ctx, protocol, method);

                ConvergenceStatus convergence = convergenceFrom(result);
                int atomCount = XyzParser.declaredAtomCount(finalXyz);
                String geometrySha = Optional.ofNullable(
                                JsonArtifacts.asTextOrNull(result, "final_xyz_sha256"))
                        .orElse(Sha256Index.hashFile(finalXyz));
                GeometryIdentity geometry = new GeometryIdentity(geometrySha, atomCount);

                String minimumId = firstText(input, "minimum_id").orElse(minDir.getFileName().toString());
                ctx.minimaGeometries.put(minimumId, geometry);

                Path logFile = minDir.resolve("raw_combined.log");
                if (Files.isRegularFile(logFile) && Files.size(logFile) == 0) {
                    ctx.issues.add(IngestionIssue.note(
                            ctx.relativize(logFile),
                            "raw_combined.log is empty (disclosed logging defect);"
                                    + " convergence taken from result.json status"));
                }

                EvidenceAcceptanceState acceptance = defaultAcceptance(convergence);
                if (checksumMismatch(ctx, minDir, "input.json", "result.json", "final.xyz")) {
                    acceptance = EvidenceAcceptanceState.CHECKSUM_INVALID;
                }

                EvidenceIdentity identity = identity(
                        ctx, geometry, chargeOrZero(input), multiplicityOrOne(input),
                        CalculationType.OPTIMIZATION, protocol,
                        constraintsFrom(input), List.of("energy", "gradient"));
                QuantumEvidence evidence = new QuantumEvidence(
                        identity,
                        provenance(ctx, resultFile, List.of(), "minimum_id=" + minimumId),
                        convergence,
                        acceptance,
                        Optional.ofNullable(JsonArtifacts.asDoubleOrNull(result, "energy_hartree")),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        convergenceNote(result));
                if (ctx.bundle.add(evidence)) {
                    ctx.minimaEvidenceHashes.put(minimumId, identity.evidenceHash());
                    ctx.coveredGeometryShas.add(geometrySha);
                }
            } catch (IOException | RuntimeException e) {
                ctx.issues.add(IngestionIssue.error(
                        ctx.relativize(minDir), "failed to ingest minimum: " + e.getMessage()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Hessians (HESSIAN)
    // ------------------------------------------------------------------

    private void ingestHessians(Context ctx, Path hessiansDir) throws IOException {
        if (!requireDir(ctx, hessiansDir)) {
            return;
        }
        for (Path hessianDir : immediateSubdirs(hessiansDir)) {
            Path resultFile = hessianDir.resolve("result.json");
            if (!Files.isRegularFile(resultFile)) {
                continue;
            }
            try {
                JsonNode input = JsonArtifacts.readTree(hessianDir.resolve("input.json"));
                JsonNode result = JsonArtifacts.readTree(resultFile);

                String minimumId = firstText(input, "minimum_id")
                        .orElse(hessianDir.getFileName().toString());
                GeometryIdentity geometry = ctx.minimaGeometries.get(minimumId);
                if (geometry == null) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(hessianDir),
                            "no geometry for minimum " + minimumId + "; hessian skipped"));
                    continue;
                }
                String method = firstText(input, "method").orElse(null);
                if (method == null) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(hessianDir), "hessian input.json has no method; skipped"));
                    continue;
                }
                QmProtocol protocol = QmProtocolParser.fromMethodString(
                        method, "PySCF", softwareVersion(input, "pyscf"));
                registerProtocol(ctx, protocol, method);

                Optional<List<Double>> hessian = Optional.empty();
                Path flat = hessianDir.resolve("cartesian_hessian_flat_hartree_per_bohr2.txt");
                if (Files.isRegularFile(flat)) {
                    hessian = Optional.of(readFlatDoubles(flat));
                }

                String classification = firstText(result, "provisional_frequency_classification")
                        .orElse("");
                ConvergenceStatus convergence = convergenceFrom(result);
                EvidenceAcceptanceState acceptance = defaultAcceptance(convergence);
                if (checksumMismatch(ctx, hessianDir, "input.json", "result.json")) {
                    acceptance = EvidenceAcceptanceState.CHECKSUM_INVALID;
                }

                QuantumEvidence evidence = new QuantumEvidence(
                        identity(ctx, geometry, chargeOrZero(input), multiplicityOrOne(input),
                                CalculationType.HESSIAN, protocol,
                                List.of(), List.of("energy", "hessian", "frequencies")),
                        provenance(ctx, resultFile,
                                List.of(ctx.minimaEvidenceHashes.getOrDefault(minimumId, "")),
                                "minimum_id=" + minimumId
                                        + (classification.isEmpty() ? ""
                                        : "; provisional_frequency_classification=" + classification)),
                        convergence,
                        acceptance,
                        Optional.ofNullable(JsonArtifacts.asDoubleOrNull(result, "energy_hartree")),
                        Optional.empty(),
                        hessian,
                        Optional.empty(),
                        Optional.empty(),
                        convergenceNote(result));
                if (ctx.bundle.add(evidence)) {
                    ctx.coveredGeometryShas.add(geometry.sha256());
                }
            } catch (IOException | RuntimeException e) {
                ctx.issues.add(IngestionIssue.error(
                        ctx.relativize(hessianDir), "failed to ingest hessian: " + e.getMessage()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Electrostatic diagnostics (SINGLE_POINT carrying dipole)
    // ------------------------------------------------------------------

    private void ingestElectrostaticDiagnostics(Context ctx, Path diagnosticsDir) throws IOException {
        if (!requireDir(ctx, diagnosticsDir)) {
            return;
        }
        for (Path diagDir : immediateSubdirs(diagnosticsDir)) {
            Path resultFile = diagDir.resolve("result.json");
            if (!Files.isRegularFile(resultFile)) {
                continue;
            }
            try {
                JsonNode result = JsonArtifacts.readTree(resultFile);
                Path inputFile = diagDir.resolve("input.json");
                JsonNode input = Files.isRegularFile(inputFile) ? JsonArtifacts.readTree(inputFile) : null;

                String minimumId = diagDir.getFileName().toString();
                GeometryIdentity geometry = ctx.minimaGeometries.get(minimumId);
                if (geometry == null) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(diagDir),
                            "no geometry for minimum " + minimumId + "; electrostatics skipped"));
                    continue;
                }
                // The diagnostics were run at the minimum's own level of theory; when the
                // diagnostic input carries no method string, inherit the minimum protocol.
                QmProtocol protocol;
                String method = firstText(input, "method").orElse(null);
                if (method != null) {
                    protocol = QmProtocolParser.fromMethodString(
                            method, "PySCF", softwareVersion(input, "pyscf"));
                } else {
                    String note = "method string absent; protocol inherited from minimum " + minimumId;
                    ctx.issues.add(IngestionIssue.note(ctx.relativize(diagDir), note));
                    protocol = minimumProtocol(ctx, minimumId).orElse(null);
                    if (protocol == null) {
                        ctx.issues.add(IngestionIssue.warning(
                                ctx.relativize(diagDir), "no protocol resolvable; skipped"));
                        continue;
                    }
                }
                registerProtocol(ctx, protocol,
                        method != null ? method : "electrostatic diagnostics at minimum protocol");

                List<Double> dipole = JsonArtifacts.asDoubleListOrNull(result, "dipole_debye");
                if (dipole == null) {
                    dipole = readFlatDoubles(diagDir.resolve("dipole_debye.txt"));
                }

                ConvergenceStatus convergence = convergenceFrom(result);
                EvidenceAcceptanceState acceptance = defaultAcceptance(convergence);
                if (checksumMismatch(ctx, diagDir, "result.json")) {
                    acceptance = EvidenceAcceptanceState.CHECKSUM_INVALID;
                }

                QuantumEvidence evidence = new QuantumEvidence(
                        identity(ctx, geometry, chargeOrZero(input), multiplicityOrOne(input),
                                CalculationType.SINGLE_POINT, protocol,
                                List.of(),
                                List.of("energy", "dipole", "mulliken_charges", "lowdin_charges")),
                        provenance(ctx, resultFile,
                                List.of(ctx.minimaEvidenceHashes.getOrDefault(minimumId, "")),
                                "minimum_id=" + minimumId + "; electrostatic diagnostic"),
                        convergence,
                        acceptance,
                        Optional.ofNullable(JsonArtifacts.asDoubleOrNull(result, "energy_hartree")),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.ofNullable(dipole),
                        Optional.empty(),
                        convergenceNote(result));
                if (ctx.bundle.add(evidence)) {
                    ctx.coveredGeometryShas.add(geometry.sha256());
                }
            } catch (IOException | RuntimeException e) {
                ctx.issues.add(IngestionIssue.error(
                        ctx.relativize(diagDir), "failed to ingest electrostatic diagnostic: " + e.getMessage()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Native Amber RESP (accepted charge model + format-rejected negative)
    // ------------------------------------------------------------------

    private void ingestNativeAmberResp(Context ctx, Path unit05O) throws IOException {
        Path respDir = unit05O.resolve("native-amber-resp3min-hf631gd");
        Path chargesCsv = unit05O.resolve("TSL_RSH_NATIVE_AMBER_RESP_CHARGES.csv");
        if (!Files.isDirectory(respDir)) {
            ctx.issues.add(IngestionIssue.error(ctx.relativize(respDir),
                    "accepted RESP evidence directory is missing"));
            return;
        }
        if (!Files.isRegularFile(chargesCsv)) {
            ctx.issues.add(IngestionIssue.error(ctx.relativize(chargesCsv),
                    "accepted RESP charge CSV is missing"));
            return;
        }
        try {
            Path resultFile = respDir.resolve("result.json");
            if (!Files.isRegularFile(resultFile)) {
                throw new IOException("accepted RESP result.json is missing");
            }
            JsonNode result = JsonArtifacts.readTree(resultFile);
            ConvergenceStatus convergence = convergenceFrom(result);
            if (convergence != ConvergenceStatus.CONVERGED) {
                throw new IOException("accepted RESP result is not converged: " + convergence);
            }
            CsvTable charges = CsvTable.read(chargesCsv);
            Map<Integer, Double> chargeByAtomId = new LinkedHashMap<>();
            for (List<String> row : charges.rows()) {
                Optional<String> id = charges.cell(row, "atom_id");
                Optional<Double> charge = charges.cellAsDouble(row, "native_resp_charge_e");
                if (id.isPresent() && charge.isPresent()) {
                    chargeByAtomId.put(Integer.parseInt(id.get().trim()), charge.get());
                }
            }
            if (chargeByAtomId.size() != ctx.canonicalAtoms.size()) {
                throw new IOException("RESP charge count " + chargeByAtomId.size()
                        + " does not match canonical atom count " + ctx.canonicalAtoms.size());
            }
            validateRespRegenerations(result, chargeByAtomId);

            String classification = "";
            Path decisionReport = unit05O.resolve("NATIVE_AMBER_RESP_DECISION_REPORT.md");
            if (Files.isRegularFile(decisionReport)) {
                classification = BranchClassificationParser.fromMarkdown(decisionReport).orElse("");
            }

            QmProtocol protocol = new QmProtocol(
                    "HF", "6-31G(d)", "none", "gas", false,
                    "AmberTools", respVersion(respDir));
            registerProtocol(ctx, protocol,
                    "two-stage native Amber RESP on HF/6-31G(d) ESP of MIN01/MIN02/MIN04");

            // Multi-conformer fit: the identity is pinned to the MIN01 geometry and the
            // charge vector itself is recorded in the note (no geometry of its own).
            GeometryIdentity geometry = ctx.minimaGeometries.get("MIN01");
            String geometryNote = "multi-conformer RESP fit; identity pinned to MIN01 geometry";
            if (geometry == null) {
                geometry = new GeometryIdentity(
                        Sha256Index.hashFile(chargesCsv), chargeByAtomId.size());
                geometryNote = "no MIN01 geometry available; identity pinned to charges CSV checksum";
            }

            double s26 = chargeByAtomId.getOrDefault(26, Double.NaN);
            double h56 = chargeByAtomId.getOrDefault(56, Double.NaN);
            String note = geometryNote
                    + "; atoms=" + chargeByAtomId.size()
                    + "; S26(atom_id=26)=" + s26 + " e"
                    + "; H56(atom_id=56)=" + h56 + " e"
                    + (classification.isEmpty() ? "" : "; classification=" + classification);

            List<String> derivedFrom = new ArrayList<>(ctx.minimaEvidenceHashes.values());
            EvidenceAcceptanceState acceptance = checksumMismatch(ctx, unit05O,
                    "TSL_RSH_NATIVE_AMBER_RESP_CHARGES.csv")
                            ? EvidenceAcceptanceState.CHECKSUM_INVALID
                            : EvidenceAcceptanceState.ACCEPTED;
            QuantumEvidence evidence = new QuantumEvidence(
                    identity(ctx, geometry, 0, 1, CalculationType.RESP, protocol,
                            List.of(), List.of("resp_charges")),
                    provenance(ctx, chargesCsv, derivedFrom, note),
                    convergence,
                    acceptance,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    classification.isEmpty()
                            ? "accepted two-stage RESP fit"
                            : "accepted two-stage RESP fit; " + classification);
            ctx.bundle.add(evidence);
        } catch (IOException | RuntimeException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(respDir), "failed to ingest native Amber RESP: " + e.getMessage()));
        }
    }

    private static void validateRespRegenerations(JsonNode result, Map<Integer, Double> chargeByAtomId)
            throws IOException {
        JsonNode regenerations = result.path("regenerations");
        if (!regenerations.isObject() || regenerations.isEmpty()) {
            throw new IOException("RESP result has no regeneration charge vectors");
        }
        List<Double> csvCharges = new ArrayList<>(chargeByAtomId.values());
        for (var names = regenerations.fieldNames(); names.hasNext();) {
            String name = names.next();
            JsonNode charges = regenerations.path(name).path("charges");
            if (!charges.isArray() || charges.size() != csvCharges.size()) {
                throw new IOException("RESP " + name + " charge vector has the wrong size");
            }
            for (int index = 0; index < charges.size(); index++) {
                JsonNode value = charges.get(index);
                if (!value.isNumber() || !Double.isFinite(value.doubleValue())
                        || Double.compare(value.doubleValue(), csvCharges.get(index)) != 0) {
                    throw new IOException("RESP charge CSV disagrees with " + name
                            + " at zero-based atom index " + index);
                }
            }
        }
    }

    private void ingestFormatRejectedResp(Context ctx, Path rejectedDir) throws IOException {
        if (!Files.isDirectory(rejectedDir)) {
            return;
        }
        try {
            Path resultFile = rejectedDir.resolve("result.json");
            JsonNode result = Files.isRegularFile(resultFile)
                    ? JsonArtifacts.readTree(resultFile)
                    : null;
            String status = firstText(result, "status").orElse("unknown");

            QmProtocol protocol = new QmProtocol(
                    "HF", "6-31G(d)", "none", "gas", false,
                    "AmberTools", respVersion(rejectedDir));
            registerProtocol(ctx, protocol, "format-rejected first RESP attempt (invalid input format)");

            Path anchor = Files.isRegularFile(resultFile) ? resultFile : rejectedDir;
            QuantumEvidence evidence = new QuantumEvidence(
                    identity(ctx,
                            new GeometryIdentity(
                                    Files.isRegularFile(resultFile)
                                            ? Sha256Index.hashFile(resultFile)
                                            : "format-rejected-" + rejectedDir.getFileName(),
                                    1),
                            0, 1, CalculationType.RESP, protocol,
                            List.of(), List.of("resp_charges")),
                    provenance(ctx, anchor, List.of(),
                            "invalid input format (center records omitted RESP's leading blank field);"
                                    + " preserved as negative evidence only; status=" + status),
                    ConvergenceStatus.UNKNOWN,
                    EvidenceAcceptanceState.PROTOCOL_INCOMPLETE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "format-rejected input; never usable as charges");
            ctx.bundle.add(evidence);
            ctx.issues.add(IngestionIssue.warning(
                    ctx.relativize(rejectedDir),
                    "format-rejected RESP run kept as PROTOCOL_INCOMPLETE negative evidence"));
        } catch (IOException | RuntimeException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(rejectedDir), "failed to ingest format-rejected RESP: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // vdW probe validation (COUNTERPOISE_INTERACTION) + classical decomposition
    // ------------------------------------------------------------------

    private void ingestVdwProbes(Context ctx, Path unit05O) throws IOException {
        Path probesDir = unit05O.resolve("vdw-probe-validation-aws");
        Path diagnosticDir = unit05O.resolve("final-19-point-force-field-diagnostic");
        if (!Files.isDirectory(probesDir)) {
            return;
        }
        Map<String, AuditRow> audit = readGeometryAudit(ctx, diagnosticDir);
        try {
            for (Path pointDir : immediateSubdirs(probesDir)) {
                List<Path> labels = immediateSubdirs(pointDir);
                if (labels.isEmpty()) {
                    continue;
                }
                Path labelDir = labels.get(0);
                Path resultFile = labelDir.resolve("result.json");
                Path geometryXyz = labelDir.resolve("geometry.xyz");
                if (!Files.isRegularFile(resultFile) || !Files.isRegularFile(geometryXyz)) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(labelDir), "probe point lacks result.json or geometry.xyz; skipped"));
                    continue;
                }
                try {
                    JsonNode result = JsonArtifacts.readTree(resultFile);
                    String pointId = firstText(result, "point_id")
                            .orElse(labelDir.getFileName().toString());
                    QmProtocol protocol = counterpoiseProtocolFromResult(result);
                    registerProtocol(ctx, protocol, "counterpoise probe interaction (from result.json keys)");

                    int atomCount = XyzParser.declaredAtomCount(geometryXyz);
                    String geometrySha = Optional.ofNullable(
                                    JsonArtifacts.asTextOrNull(result, "geometry_sha256"))
                            .orElse(Sha256Index.hashFile(geometryXyz));
                    GeometryIdentity geometry = new GeometryIdentity(geometrySha, atomCount);

                    ConvergenceStatus convergence = convergenceFrom(result);
                    AuditRow auditRow = audit.get(pointId);
                    EvidenceAcceptanceState acceptance;
                    String auditNote = "";
                    if (auditRow != null && auditRow.excluded()) {
                        acceptance = EvidenceAcceptanceState.GEOMETRY_INVALID;
                        auditNote = "; geometry_classification=" + auditRow.geometryClassification()
                                + "; eligibility=" + auditRow.eligibility();
                    } else {
                        acceptance = defaultAcceptance(convergence);
                    }
                    if (checksumMismatch(ctx, labelDir, "result.json", "geometry.xyz")) {
                        acceptance = EvidenceAcceptanceState.CHECKSUM_INVALID;
                    }

                    String site = firstText(result, "site").orElse("unknown");
                    QuantumEvidence evidence = new QuantumEvidence(
                            identity(ctx, geometry, 0, 1,
                                    CalculationType.COUNTERPOISE_INTERACTION, protocol,
                                    List.of("RIGID_SINGLE_POINT"), List.of("interaction_energy")),
                            provenance(ctx, resultFile, List.of(),
                                    "point_id=" + pointId + "; site=" + site + auditNote),
                            convergence,
                            acceptance,
                            Optional.ofNullable(
                                    JsonArtifacts.asDoubleOrNull(result, "electronic_dimer_hartree")),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.ofNullable(interactionEnergy(result)),
                            convergenceNote(result));
                    if (ctx.bundle.add(evidence)) {
                        ctx.probeEvidenceHashByPointId.put(
                                pointId, evidence.identity().evidenceHash());
                        ctx.probeAtomCountByPointId.put(pointId, atomCount);
                        ctx.lastProbeAtomCount = atomCount;
                        ctx.coveredGeometryShas.add(geometrySha);
                    }
                } catch (IOException | RuntimeException e) {
                    ctx.issues.add(IngestionIssue.error(
                            ctx.relativize(labelDir), "failed to ingest probe point: " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(probesDir), "failed to scan probe points: " + e.getMessage()));
        }
        ingestClassicalDecomposition(ctx, diagnosticDir, audit);
    }

    /** One row of ALL_19_PROBE_GEOMETRY_AUDIT.csv, resolved by header name. */
    private record AuditRow(String geometryClassification, String eligibility) {
        boolean excluded() {
            return eligibility.toUpperCase(Locale.ROOT).startsWith("EXCLUDE");
        }
    }

    private Map<String, AuditRow> readGeometryAudit(Context ctx, Path diagnosticDir) throws IOException {
        Map<String, AuditRow> audit = new LinkedHashMap<>();
        Path auditCsv = diagnosticDir.resolve("ALL_19_PROBE_GEOMETRY_AUDIT.csv");
        if (!Files.isRegularFile(auditCsv)) {
            ctx.issues.add(IngestionIssue.warning(
                    ctx.relativize(auditCsv),
                    "probe geometry audit CSV not found; probe points ingested unaudited"));
            return audit;
        }
        CsvTable table = CsvTable.read(auditCsv);
        for (List<String> row : table.rows()) {
            Optional<String> pointId = table.cell(row, "point_id");
            if (pointId.isEmpty()) {
                continue;
            }
            audit.put(pointId.get(), new AuditRow(
                    table.cell(row, "geometry_classification").orElse(""),
                    table.cell(row, "force_field_validation_eligibility").orElse("")));
        }
        return audit;
    }

    private void ingestClassicalDecomposition(
            Context ctx, Path diagnosticDir, Map<String, AuditRow> audit) throws IOException {
        Path csv = diagnosticDir.resolve("CLASSICAL_ENERGY_DECOMPOSITION.csv");
        if (!Files.isRegularFile(csv)) {
            return;
        }
        QmProtocol gaff2 = gaff2Protocol(ctx);
        try {
            CsvTable table = CsvTable.read(csv);
            for (List<String> row : table.rows()) {
                Optional<String> pointId = table.cell(row, "point_id");
                if (pointId.isEmpty()) {
                    continue;
                }
                try {
                    // The decomposition shares its geometry with the QM probe of the same
                    // point; the audit row holds the geometry coordinates checksum.
                    String geometrySha = geometryShaForPoint(ctx, diagnosticDir, pointId.get());
                    if (geometrySha == null) {
                        ctx.issues.add(IngestionIssue.warning(
                                ctx.relativize(csv),
                                "no geometry resolvable for classical point " + pointId.get() + "; skipped"));
                        continue;
                    }
                    GeometryIdentity geometry = new GeometryIdentity(geometrySha, probeAtomCount(ctx, pointId.get()));
                    double total = table.cellAsDouble(row, "total").orElseThrow(
                            () -> new IOException("classical row " + pointId.get() + " without total"));
                    EnergyDecomposition decomposition = new EnergyDecomposition(
                            total,
                            table.cellAsDouble(row, "bond").orElse(null),
                            table.cellAsDouble(row, "angle").orElse(null),
                            table.cellAsDouble(row, "proper_torsion").orElse(null),
                            table.cellAsDouble(row, "improper_torsion").orElse(null),
                            table.cellAsDouble(row, "one_four_vdW").orElse(null),
                            table.cellAsDouble(row, "electrostatic").orElse(null),
                            table.cellAsDouble(row, "ordinary_LJ_vdW").orElse(null),
                            total);
                    AuditRow auditRow = audit.get(pointId.get());
                    EvidenceAcceptanceState acceptance =
                            auditRow != null && auditRow.excluded()
                                    ? EvidenceAcceptanceState.GEOMETRY_INVALID
                                    : EvidenceAcceptanceState.ACCEPTED;
                    List<String> derivedFrom = new ArrayList<>();
                    String qmHash = ctx.probeEvidenceHashByPointId.get(pointId.get());
                    if (qmHash != null) {
                        derivedFrom.add(qmHash);
                    }
                    ClassicalEvidence evidence = new ClassicalEvidence(
                            identity(ctx, geometry, 0, 1,
                                    CalculationType.ENERGY_DECOMPOSITION, gaff2,
                                    List.of("RIGID_SINGLE_POINT"), List.of("energy_decomposition")),
                            "GAFF2",
                            "execution-unit-05O/final-19-point-force-field-diagnostic",
                            decomposition,
                            provenance(ctx, csv, derivedFrom, "point_id=" + pointId.get()),
                            acceptance);
                    ctx.bundle.add(evidence);
                } catch (IOException | RuntimeException e) {
                    ctx.issues.add(IngestionIssue.error(
                            ctx.relativize(csv),
                            "failed to ingest classical point " + pointId.get() + ": " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(csv), "failed to read classical decomposition: " + e.getMessage()));
        }
    }

    private String geometryShaForPoint(Context ctx, Path diagnosticDir, String pointId)
            throws IOException {
        Path auditCsv = diagnosticDir.resolve("ALL_19_PROBE_GEOMETRY_AUDIT.csv");
        if (Files.isRegularFile(auditCsv)) {
            CsvTable table = CsvTable.read(auditCsv);
            for (List<String> row : table.rows()) {
                if (table.cell(row, "point_id").map(pointId::equals).orElse(false)) {
                    Optional<String> sha = table.cell(row, "geometry_sha256");
                    if (sha.isPresent()) {
                        return sha.get();
                    }
                }
            }
        }
        return null;
    }

    private int probeAtomCount(Context ctx, String pointId) {
        // Probe dimers are TSL-RSH plus a small probe molecule; the exact count was
        // established when the QM probe point was parsed. Classical points whose QM
        // sibling was not ingested fall back to the dimer size of any known probe.
        return ctx.probeAtomCountByPointId.getOrDefault(pointId, ctx.lastProbeAtomCount);
    }

    // ------------------------------------------------------------------
    // Replacement S-H donor QM + six-point classical table
    // ------------------------------------------------------------------

    private void ingestReplacementShDonor(Context ctx, Path unit05O) throws IOException {
        Path localDir = unit05O.resolve("replacement-sh-donor-qm-local");
        if (Files.isDirectory(localDir)) {
            Path runResults = localDir.resolve("RUN_RESULTS.json");
            if (Files.isRegularFile(runResults)) {
                try {
                    JsonNode runs = JsonArtifacts.readTree(runResults);
                    if (runs.isArray()) {
                        for (JsonNode run : runs) {
                            ingestReplacementRun(ctx, localDir, run);
                        }
                    }
                } catch (IOException e) {
                    ctx.issues.add(IngestionIssue.error(
                            ctx.relativize(runResults), "failed to read RUN_RESULTS.json: " + e.getMessage()));
                }
            }
        }
        ingestSixPointClassical(ctx, unit05O.resolve("replacement-sh-donor-final-evidence-package"));
    }

    private void ingestReplacementRun(Context ctx, Path localDir, JsonNode run) {
        String pointId = firstText(run, "point_id").orElse("unknown");
        Path runDir = localDir.resolve(pointId);
        Path geometryXyz = runDir.resolve("geometry.xyz");
        try {
            String method = firstText(run, "method").orElse(null);
            if (method == null) {
                ctx.issues.add(IngestionIssue.warning(
                        ctx.relativize(runDir), "replacement run " + pointId + " without method; skipped"));
                return;
            }
            QmProtocol protocol = QmProtocolParser.fromMethodString(method, "PySCF", "unknown");
            registerProtocol(ctx, protocol, method);

            int atomCount = Files.isRegularFile(geometryXyz)
                    ? XyzParser.declaredAtomCount(geometryXyz)
                    : 1;
            String geometrySha = Optional.ofNullable(
                            JsonArtifacts.asTextOrNull(run, "geometry_sha256"))
                    .orElse(Files.isRegularFile(geometryXyz)
                            ? Sha256Index.hashFile(geometryXyz)
                            : "unknown-geometry-" + pointId);
            GeometryIdentity geometry = new GeometryIdentity(geometrySha, atomCount);

            ConvergenceStatus convergence = convergenceFrom(run);
            EvidenceAcceptanceState acceptance = defaultAcceptance(convergence);
            String constraints = firstText(run, "constraints").orElse("");

            QuantumEvidence evidence = new QuantumEvidence(
                    identity(ctx, geometry,
                            intOrDefault(run, "charge", 0), intOrDefault(run, "multiplicity", 1),
                            CalculationType.COUNTERPOISE_INTERACTION, protocol,
                            constraints.isBlank() ? List.of() : List.of(constraints),
                            List.of("interaction_energy")),
                    provenance(ctx,
                            Files.isRegularFile(runDir.resolve("result.json"))
                                    ? runDir.resolve("result.json")
                                    : localDir.resolve("RUN_RESULTS.json"),
                            List.of(), "point_id=" + pointId),
                    convergence,
                    acceptance,
                    Optional.ofNullable(JsonArtifacts.asDoubleOrNull(run, "electronic_dimer_hartree")),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.ofNullable(
                            JsonArtifacts.asDoubleOrNull(run, "qm_cp_pbe0_d3bj_def2tzvp_kcal_mol")),
                    convergenceNote(run));
            if (ctx.bundle.add(evidence)) {
                ctx.probeAtomCountByPointId.putIfAbsent(pointId, atomCount);
                ctx.lastProbeAtomCount = atomCount;
                ctx.coveredGeometryShas.add(geometrySha);
            }
        } catch (IOException | RuntimeException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(runDir), "failed to ingest replacement run: " + e.getMessage()));
        }
    }

    private void ingestSixPointClassical(Context ctx, Path packageDir) throws IOException {
        Path csv = packageDir.resolve("SIX_POINT_SH_DONOR_MASTER_TABLE.csv");
        if (!Files.isRegularFile(csv)) {
            return;
        }
        QmProtocol gaff2 = gaff2Protocol(ctx);
        try {
            CsvTable table = CsvTable.read(csv);
            int rowNumber = 0;
            for (List<String> row : table.rows()) {
                rowNumber++;
                try {
                    Optional<String> sha = table.cell(row, "geometry_sha256");
                    Optional<Double> gaff2Energy = table.cellAsDouble(row, "gaff2_interaction_energy_kcal_mol");
                    if (sha.isEmpty() || gaff2Energy.isEmpty()) {
                        continue;
                    }
                    String parentMinimum = table.cell(row, "parent_minimum").orElse("unknown");
                    String distance = table.cell(row, "target_SH_O_distance_A").orElse("?");
                    String pointLabel = parentMinimum + "_SH_DONOR_" + distance + "A";
                    boolean valid = table.cell(row, "geometry_valid_status")
                            .map(s -> s.toUpperCase(Locale.ROOT).startsWith("VALID"))
                            .orElse(false);
                    EnergyDecomposition decomposition = new EnergyDecomposition(
                            gaff2Energy.get(),
                            null, null, null, null, null,
                            table.cellAsDouble(row, "electrostatic_interaction_kcal_mol").orElse(null),
                            table.cellAsDouble(row, "ordinary_LJ_interaction_kcal_mol").orElse(null),
                            gaff2Energy.get());
                    ClassicalEvidence evidence = new ClassicalEvidence(
                            identity(ctx, new GeometryIdentity(sha.get(), probeAtomCount(ctx, pointLabel)),
                                    0, 1, CalculationType.CLASSICAL_FIXED_GEOMETRY_ENERGY, gaff2,
                                    List.of("RIGID_SINGLE_POINT"), List.of("interaction_energy")),
                            "GAFF2",
                            "execution-unit-05O/replacement-sh-donor-final-evidence-package",
                            decomposition,
                            provenance(ctx, csv, List.of(),
                                    "six-point SH donor master table row " + rowNumber
                                            + "; parent_minimum=" + parentMinimum),
                            valid ? EvidenceAcceptanceState.ACCEPTED
                                    : EvidenceAcceptanceState.GEOMETRY_INVALID);
                    ctx.bundle.add(evidence);
                } catch (RuntimeException e) {
                    ctx.issues.add(IngestionIssue.error(
                            ctx.relativize(csv), "failed to ingest six-point row " + rowNumber
                            + ": " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(csv), "failed to read six-point master table: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Geometry inventory (dedup/cross-check backbone + recovery of 05B–05M)
    // ------------------------------------------------------------------

    private void ingestGeometryInventory(Context ctx, Path auditDir) throws IOException {
        Path csv = auditDir.resolve("TSL_RSH_QM_GEOMETRY_INVENTORY.csv");
        if (!Files.isRegularFile(csv)) {
            return;
        }
        try {
            CsvTable table = CsvTable.read(csv);
            for (List<String> row : table.rows()) {
                try {
                    Optional<String> sha = table.cell(row, "geometry_sha256");
                    if (sha.isEmpty()) {
                        continue;
                    }
                    if (ctx.coveredGeometryShas.contains(sha.get())) {
                        continue; // already recovered natively
                    }
                    Optional<String> geometryPath = table.cell(row, "geometry_path");
                    int atomCount = 1;
                    if (geometryPath.isPresent()) {
                        Path xyz = resolveArchivePath(ctx, geometryPath.get());
                        if (xyz != null && Files.isRegularFile(xyz)) {
                            atomCount = XyzParser.declaredAtomCount(xyz);
                        } else {
                            ctx.issues.add(IngestionIssue.note(
                                    geometryPath.get(),
                                    "inventory geometry file not found; atom count unavailable"));
                        }
                    }
                    String methodBasis = table.cell(row, "qm_method_basis").orElse(null);
                    if (methodBasis == null) {
                        continue;
                    }
                    QmProtocol protocol = QmProtocolParser.fromMethodString(
                            methodBasis, "unknown", "unknown");
                    registerProtocol(ctx, protocol, methodBasis + " (geometry inventory)");

                    String constraints = table.cell(row, "constraints").orElse("");
                    String probeClass = table.cell(row, "probe_class").orElse("");
                    CalculationType calculationType = !probeClass.isBlank()
                            ? CalculationType.COUNTERPOISE_INTERACTION
                            : constraints.toLowerCase(Locale.ROOT).contains("torsion")
                            ? CalculationType.CONSTRAINED_SCAN
                            : CalculationType.SINGLE_POINT;

                    ConvergenceStatus convergence = table.cell(row, "convergence_status")
                            .map(LegacyPhase2ArchiveIngester::convergenceFromText)
                            .orElse(ConvergenceStatus.UNKNOWN);
                    String inclusion = table.cell(row, "inclusion_exclusion_status").orElse("");
                    EvidenceAcceptanceState acceptance;
                    if (inclusion.toUpperCase(Locale.ROOT).startsWith("EXCLUDE")) {
                        acceptance = EvidenceAcceptanceState.EXCLUDED_BY_PROTOCOL;
                    } else if (inclusion.toUpperCase(Locale.ROOT).startsWith("INCLUDE")) {
                        acceptance = defaultAcceptance(convergence);
                    } else {
                        acceptance = EvidenceAcceptanceState.PENDING;
                    }

                    // Relative energies are not absolute hartree; they are carried in the note.
                    String energyKind = table.cell(row, "qm_energy_kind").orElse("");
                    Optional<String> energyValue = table.cell(row, "qm_energy_value");
                    Optional<Double> energyHartree = Optional.empty();
                    String energyNote = "";
                    if ("ABSOLUTE_HARTREE".equalsIgnoreCase(energyKind) && energyValue.isPresent()) {
                        energyHartree = Optional.of(Double.parseDouble(energyValue.get()));
                    } else if (energyValue.isPresent()) {
                        energyNote = "; qm_energy=" + energyValue.get() + " " + energyKind;
                    }
                    String artifactId = table.cell(row, "artifact_id").orElse("unknown");
                    String gaff2 = table.cell(row, "corresponding_GAFF2_energy").orElse("");
                    if (!gaff2.isBlank()) {
                        energyNote += "; GAFF2=" + gaff2 + " "
                                + table.cell(row, "GAFF2_energy_kind").orElse("");
                    }

                    Path source = geometryPath.map(p -> resolveArchivePath(ctx, p))
                            .filter(Objects::nonNull)
                            .orElse(csv);
                    QuantumEvidence evidence = new QuantumEvidence(
                            identity(ctx, new GeometryIdentity(sha.get(), atomCount),
                                    0, 1, calculationType, protocol,
                                    constraints.isBlank() ? List.of() : List.of(constraints),
                                    List.of("energy")),
                            provenance(ctx, source, List.of(),
                                    "recovered via geometry inventory; artifact_id=" + artifactId
                                            + energyNote),
                            convergence,
                            acceptance,
                            energyHartree,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            Optional.empty(),
                            "convergence_status=" + convergence);
                    if (ctx.bundle.add(evidence)) {
                        ctx.coveredGeometryShas.add(sha.get());
                    }
                } catch (RuntimeException | IOException e) {
                    ctx.issues.add(IngestionIssue.error(
                            ctx.relativize(csv), "failed to ingest inventory row: " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            ctx.issues.add(IngestionIssue.error(
                    ctx.relativize(csv), "failed to read geometry inventory: " + e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // Failed branches and validated diagnostics
    // ------------------------------------------------------------------

    private void ingestFailedBranches(Context ctx, Path unit05O) throws IOException {
        for (String branchDir : FAILED_BRANCH_DIRS) {
            Path dir = unit05O.resolve(branchDir);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try {
                Optional<BranchClassificationParser.RecoveredClassification> recovered =
                        BranchClassificationParser.find(dir);
                if (recovered.isEmpty()) {
                    ctx.issues.add(IngestionIssue.warning(
                            ctx.relativize(dir), "no classification recoverable from branch"));
                    continue;
                }
                BranchClassificationParser.RecoveredClassification found = recovered.get();
                ctx.branchOutcomes.add(new FailedCandidateRecord(
                        branchDir,
                        found.classification(),
                        FailedCandidateRecord.evidenceClassFor(found.classification()),
                        ctx.relativize(found.reportPath()),
                        List.of(),
                        found.summary()));
            } catch (IOException e) {
                ctx.issues.add(IngestionIssue.error(
                        ctx.relativize(dir), "failed to read branch reports: " + e.getMessage()));
            }
        }
    }

    private void ingestAngleCrossDiagnostic(Context ctx, Path unit05L) throws IOException {
        Path report = unit05L.resolve("ANGLE_CROSS_CAUSAL_REPORT.md");
        if (!Files.isRegularFile(report)) {
            return;
        }
        Optional<String> classification = BranchClassificationParser.fromMarkdown(report);
        if (classification.isEmpty()) {
            ctx.issues.add(IngestionIssue.warning(
                    ctx.relativize(report), "no classification recoverable from angle cross report"));
            return;
        }
        ctx.branchOutcomes.add(new FailedCandidateRecord(
                "execution-unit-05L-angle-cross",
                classification.get(),
                FailedCandidateRecord.evidenceClassFor(classification.get()),
                ctx.relativize(report),
                List.of(),
                ""));
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private EvidenceIdentity identity(
            Context ctx, GeometryIdentity geometry, int formalCharge, int multiplicity,
            CalculationType calculationType, QmProtocol protocol,
            List<String> constraints, List<String> requestedOutputs) {
        return new EvidenceIdentity(
                ctx.canonicalAtoms.molecule(),
                ctx.atomMapHash,
                geometry,
                formalCharge,
                multiplicity,
                calculationType,
                protocol,
                constraints,
                requestedOutputs);
    }

    private EvidenceProvenance provenance(
            Context ctx, Path source, List<String> derivedFrom, String note) throws IOException {
        List<String> derived = derivedFrom.stream().filter(s -> s != null && !s.isBlank()).toList();
        String sha = Files.isRegularFile(source) ? Sha256Index.hashFile(source) : "none";
        return new EvidenceProvenance(ctx.relativize(source), sha, ctx.ingestedAt, derived, note);
    }

    private void registerProtocol(Context ctx, QmProtocol protocol, String description) {
        ctx.protocolRegistry.putIfAbsent(protocol.protocolKey(), description);
    }

    private QmProtocol gaff2Protocol(Context ctx) {
        QmProtocol gaff2 = new QmProtocol(
                "GAFF2", "none", "none", "none", false, "AmberTools", "unknown");
        registerProtocol(ctx, gaff2, "GAFF2 fixed-geometry classical evaluation (AmberTools)");
        return gaff2;
    }

    private Optional<QmProtocol> minimumProtocol(Context ctx, String minimumId) {
        return ctx.bundle.quantum().stream()
                .filter(e -> e.identity().calculationType() == CalculationType.OPTIMIZATION)
                .filter(e -> e.provenance().note().contains("minimum_id=" + minimumId))
                .map(e -> e.identity().protocol())
                .findFirst();
    }

    /** Counterpoise protocol recovered from result.json key names (e.g. qm_cp_pbe0_d3bj_def2tzvp_kcal_mol). */
    private QmProtocol counterpoiseProtocolFromResult(JsonNode result) {
        String methodToken = null;
        for (var it = result.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (key.startsWith("qm_cp_") && key.endsWith("_kcal_mol")) {
                methodToken = key.substring("qm_cp_".length(), key.length() - "_kcal_mol".length());
                break;
            }
        }
        if (methodToken == null) {
            return new QmProtocol("unknown", "none", "none", "none", true, "unknown", "unknown");
        }
        // token like "pbe0_d3bj_def2tzvp"
        String[] parts = methodToken.split("_");
        String method = parts.length > 0 ? parts[0].toUpperCase(Locale.ROOT) : "unknown";
        String dispersion = "none";
        String basis = "none";
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("d3bj")) {
                dispersion = "D3(BJ)";
            } else if (parts[i].toLowerCase(Locale.ROOT).startsWith("def2")) {
                basis = "def2-" + parts[i].substring(4).toUpperCase(Locale.ROOT);
            }
        }
        return new QmProtocol(method, basis, dispersion, "none", true, "unknown", "unknown");
    }

    private Double interactionEnergy(JsonNode result) {
        for (var it = result.fieldNames(); it.hasNext(); ) {
            String key = it.next();
            if (key.startsWith("qm_cp_") && key.endsWith("_kcal_mol")) {
                JsonNode value = result.get(key);
                return value != null && value.isNumber() ? value.asDouble() : null;
            }
        }
        return null;
    }

    private static ConvergenceStatus convergenceFrom(JsonNode result) {
        if (result == null) {
            return ConvergenceStatus.UNKNOWN;
        }
        Boolean scf = JsonArtifacts.asBooleanOrNull(result, "scf_converged");
        if (scf != null) {
            return scf ? ConvergenceStatus.CONVERGED : ConvergenceStatus.NOT_CONVERGED;
        }
        String status = JsonArtifacts.asTextOrNull(result, "status");
        if (status == null) {
            return ConvergenceStatus.UNKNOWN;
        }
        return convergenceFromText(status);
    }

    private static ConvergenceStatus convergenceFromText(String status) {
        String s = status.strip().toUpperCase(Locale.ROOT);
        if (s.matches(".*(?:NOT[_ -]?CONVERGED|UNCONVERGED|INCOMPLETE).*")) {
            return ConvergenceStatus.NOT_CONVERGED;
        }
        if (s.contains("FAILED")) {
            return ConvergenceStatus.FAILED;
        }
        if (s.matches(".*(?:^|[^A-Z])(?:CONVERGED|COMPLETE)(?:[^A-Z]|$).*$")) {
            return ConvergenceStatus.CONVERGED;
        }
        return ConvergenceStatus.UNKNOWN;
    }

    private static EvidenceAcceptanceState defaultAcceptance(ConvergenceStatus convergence) {
        return switch (convergence) {
            case CONVERGED -> EvidenceAcceptanceState.ACCEPTED;
            case NOT_CONVERGED, FAILED, EMPTY_OUTPUT -> EvidenceAcceptanceState.FAILED_NUMERICALLY;
            case UNKNOWN -> EvidenceAcceptanceState.PENDING;
        };
    }

    private static String convergenceNote(JsonNode result) {
        String status = result != null ? JsonArtifacts.asTextOrNull(result, "status") : null;
        return status != null ? "status=" + status : "";
    }

    private static List<String> constraintsFrom(JsonNode input) {
        String constraints = firstText(input, "constraints").orElse("");
        return constraints.isBlank() || constraints.equalsIgnoreCase("NONE")
                ? List.of()
                : List.of(constraints);
    }

    private static int chargeOrZero(JsonNode input) {
        return intOrDefault(input, "charge", 0);
    }

    private static int multiplicityOrOne(JsonNode input) {
        return intOrDefault(input, "multiplicity", 1);
    }

    private static int intOrDefault(JsonNode node, String field, int fallback) {
        Integer value = JsonArtifacts.asIntOrNull(node, field);
        return value != null ? value : fallback;
    }

    private static Optional<String> firstText(JsonNode node, String field) {
        return Optional.ofNullable(JsonArtifacts.asTextOrNull(node, field));
    }

    private static String softwareVersion(JsonNode input, String key) {
        JsonNode software = input != null ? input.get("software") : null;
        String version = software != null ? JsonArtifacts.asTextOrNull(software, key) : null;
        return version != null ? version : "unknown";
    }

    private static String respVersion(Path respDir) {
        Path env = respDir.resolve("software_environment.json");
        if (!Files.isRegularFile(env)) {
            return "unknown";
        }
        try {
            JsonNode tree = JsonArtifacts.readTree(env);
            String record = JsonArtifacts.asTextOrNull(tree, "ambertools_conda_record");
            if (record != null) {
                String[] tokens = record.trim().split("\\s+");
                if (tokens.length >= 2) {
                    return tokens[1];
                }
            }
        } catch (IOException e) {
            // fall through
        }
        return "unknown";
    }

    /** Reads all whitespace-separated doubles of a flat numeric text file. */
    private static List<Double> readFlatDoubles(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        List<Double> values = new ArrayList<>();
        try (java.io.BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                for (String token : line.trim().split("\\s+")) {
                    values.add(Double.parseDouble(token));
                }
            }
        }
        return List.copyOf(values);
    }

    /**
     * Resolves an inventory-style geometry path (which may be anchored above the
     * archive root, e.g. {@code analysis/mettl7-phase2/execution-unit-05D/...})
     * against the archive root by taking the suffix from {@code execution-unit-}.
     */
    private static Path resolveArchivePath(Context ctx, String recordedPath) {
        String normalized = recordedPath.replace('\\', '/');
        int anchor = normalized.indexOf("execution-unit-");
        if (anchor >= 0) {
            return ctx.archiveRoot.resolve(normalized.substring(anchor));
        }
        return ctx.archiveRoot.resolve(normalized);
    }

    private boolean requireDir(Context ctx, Path dir) {
        if (!Files.isDirectory(dir)) {
            ctx.issues.add(IngestionIssue.note(
                    ctx.relativize(dir), "expected evidence directory not found; skipped"));
            return false;
        }
        return true;
    }

    private static List<Path> immediateSubdirs(Path dir) throws IOException {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory)
                    .filter(p -> !ArchiveExclusions.isExcluded(p))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }
}
