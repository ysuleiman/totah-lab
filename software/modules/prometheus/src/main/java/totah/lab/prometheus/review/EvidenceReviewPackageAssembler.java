package totah.lab.prometheus.review;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.diagnosis.DiagnosisReport;
import totah.lab.prometheus.diagnosis.FunctionalFormClassification;
import totah.lab.prometheus.diagnosis.FunctionalFormDiagnostic;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;
import totah.lab.prometheus.ingest.FailedCandidateRecord;
import totah.lab.prometheus.ingest.LegacyPhase2ArchiveIngester;
import totah.lab.prometheus.recovery.authoritative.AuthoritativeEvidenceEnricher;
import totah.lab.prometheus.inventory.EvidenceInventoryService;
import totah.lab.prometheus.inventory.EvidenceInventorySnapshot;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.planning.EvidenceGenerationPlan;
import totah.lab.prometheus.planning.EvidenceRequirement;
import totah.lab.prometheus.planning.PlanDecision;
import totah.lab.prometheus.planning.RequirementResolution;
import totah.lab.prometheus.reporting.ExecutionDecisionRecord;
import totah.lab.prometheus.reporting.ProtocolGroupRow;
import totah.lab.prometheus.reporting.StrategyComparisonRow;
import totah.lab.prometheus.reporting.ReviewDeliverableInput;
import totah.lab.prometheus.reporting.ReviewDeliverableRenderer;
import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.EvidenceMemoryIndex;

/**
 * TSL acceptance-profile assembler. It derives the review package from the
 * canonical evidence generation and parsed archived decisions; scientific
 * engines are never invoked.
 */
public final class EvidenceReviewPackageAssembler {

    private static final QmProtocol QUBEKIT_PROTOCOL = new QmProtocol(
            "wB97X-D", "6-311++G(d,p)", "included", "none", false, "Gaussian", "16");

    public void write(Path archiveRoot, Path canonicalStoreRoot, Path outputDirectory)
            throws IOException {
        CanonicalEvidenceStore.LoadedEvidence canonical =
                new CanonicalEvidenceStore().loadCurrent(canonicalStoreRoot);
        LegacyPhase2ArchiveIngester.IngestionResult archive = new LegacyPhase2ArchiveIngester().ingest(archiveRoot);
        EvidenceMemoryIndex authoritativeArchive = new EvidenceMemoryIndex(
                new AuthoritativeEvidenceEnricher().enrich(archiveRoot, archive.bundle()));
        verifyCanonicalMatchesArchive(canonical.index(), authoritativeArchive);

        EvidenceMemoryIndex evidence = canonical.index();
        MoleculeIdentity molecule = evidence.quantum().stream().findFirst().orElseThrow().identity().molecule();
        GeometryIdentity min02 = findMin02(evidence);
        EvidenceInventorySnapshot inventory = new EvidenceInventoryService(evidence).snapshot();

        ReviewDeliverableInput input = new ReviewDeliverableInput(
                evidence,
                inventory,
                protocolGroups(evidence),
                diagnosis(molecule, archive.branchOutcomes(), canonical.manifest().compiledAt()),
                strategyRows(evidence),
                blockedQubeKitPlan(molecule, min02),
                costNotes(),
                executionDecision(archive.branchOutcomes(), inventory.provenanceGaps().size()));
        new ReviewDeliverableRenderer().write(outputDirectory, input);
    }

    private static void verifyCanonicalMatchesArchive(
            EvidenceMemoryIndex canonical,
            EvidenceMemoryIndex archive) throws IOException {
        Set<String> canonicalHashes = new LinkedHashSet<>();
        canonical.quantum().forEach(item -> canonicalHashes.add("Q:" + item.identity().evidenceHash()));
        canonical.classical().forEach(item -> canonicalHashes.add("C:" + item.identity().evidenceHash()));
        Set<String> archiveHashes = new LinkedHashSet<>();
        archive.quantum().forEach(item -> archiveHashes.add("Q:" + item.identity().evidenceHash()));
        archive.classical().forEach(item -> archiveHashes.add("C:" + item.identity().evidenceHash()));
        if (!canonicalHashes.equals(archiveHashes)) {
            throw new IOException("canonical evidence generation does not match the current archive import");
        }
    }

    private static GeometryIdentity findMin02(EvidenceMemoryIndex evidence) throws IOException {
        return evidence.quantum().stream()
                .filter(item -> item.identity().calculationType() == CalculationType.OPTIMIZATION)
                .filter(item -> item.acceptance() == EvidenceAcceptanceState.ACCEPTED)
                .filter(item -> item.provenance().sourcePath().toUpperCase().contains("MIN02"))
                .map(item -> item.identity().geometry())
                .findFirst()
                .orElseThrow(() -> new IOException("accepted MIN02 optimization is absent"));
    }

    private static List<ProtocolGroupRow> protocolGroups(EvidenceMemoryIndex evidence) {
        Map<String, ProtocolAccumulator> groups = new LinkedHashMap<>();
        evidence.quantum().forEach(item -> addProtocol(groups, item.identity().protocol().protocolKey(),
                item.identity().calculationType(), item.identity().evidenceHash()));
        evidence.classical().forEach(item -> addProtocol(groups, item.identity().protocol().protocolKey(),
                item.identity().calculationType(), item.identity().evidenceHash()));
        List<ProtocolGroupRow> rows = new ArrayList<>();
        int group = 0;
        for (var entry : groups.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            group++;
            rows.add(new ProtocolGroupRow(
                    "protocol-" + group,
                    entry.getKey(),
                    entry.getValue().calculationTypes.stream().sorted().toList(),
                    entry.getValue().hashes.stream().sorted().toList(),
                    "Exact protocol group only; evidence in another protocol group is not silently interchangeable."));
        }
        return rows;
    }

    private static void addProtocol(
            Map<String, ProtocolAccumulator> groups,
            String key,
            CalculationType type,
            String hash) {
        ProtocolAccumulator accumulator = groups.computeIfAbsent(key,
                unused -> new ProtocolAccumulator());
        accumulator.calculationTypes.add(type.name());
        accumulator.hashes.add(hash);
    }

    private static final class ProtocolAccumulator {
        private final Set<String> calculationTypes = new LinkedHashSet<>();
        private final List<String> hashes = new ArrayList<>();
    }

    private static DiagnosisReport diagnosis(
            MoleculeIdentity molecule,
            List<FailedCandidateRecord> outcomes,
            java.time.Instant createdAt) {
        List<FunctionalFormDiagnostic> diagnostics = new ArrayList<>();
        for (FailedCandidateRecord outcome : outcomes) {
            FunctionalFormClassification classification = mapClassification(outcome.classification());
            if (classification != null) {
                diagnostics.add(new FunctionalFormDiagnostic(
                        classification,
                        List.of(outcome.classification() + ": " + outcome.summary()
                                + " [source: " + outcome.reportPath() + "]"),
                        outcome.relatedEvidenceHashes(),
                        "archive-derived-1"));
            }
        }
        diagnostics.sort(Comparator.comparing(item -> item.classification().name()));
        return new DiagnosisReport(molecule, diagnostics, createdAt);
    }

    private static FunctionalFormClassification mapClassification(String classification) {
        if (classification.equals("ANGLE_LJ_COUPLED_DEFECT_SUPPORTED")) {
            return FunctionalFormClassification.COUPLED_COORDINATE_BEHAVIOR;
        }
        if (classification.contains("HARMONIC_BONDED_FORM_INSUFFICIENT")) {
            return FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT;
        }
        if (classification.contains("OFFCENTER_FIXED_CHARGE_MODEL_INSUFFICIENT")
                || classification.contains("ONE_PARAMETER_LOCAL_POLARIZATION_INSUFFICIENT")) {
            return FunctionalFormClassification.NONBONDED_FORM_INSUFFICIENT;
        }
        if (classification.contains("RESP_CONSISTENT_FIXED_CHARGE_CORRECTION_NOT_FOUND")
                || classification.contains("NO_PUBLISHED_LJ_COMPARATOR_RESOLVES")) {
            return FunctionalFormClassification.PARAMETER_TRANSFERABILITY_FAILURE;
        }
        return null;
    }

    private static List<StrategyComparisonRow> strategyRows(EvidenceMemoryIndex evidence) {
        List<String> classical = classicalHashes(evidence, ignored -> true);
        List<String> esp = quantumHashes(evidence, item -> EnumSet.of(
                CalculationType.ESP, CalculationType.RESP).contains(item.identity().calculationType()));
        List<String> hessians = quantumHashes(evidence,
                item -> item.identity().calculationType() == CalculationType.HESSIAN);
        List<String> scans = quantumHashes(evidence, item -> EnumSet.of(
                CalculationType.TORSION_SCAN, CalculationType.CONSTRAINED_SCAN)
                .contains(item.identity().calculationType()));
        List<String> energiesAndForces = quantumHashes(evidence, item -> EnumSet.of(
                CalculationType.SINGLE_POINT, CalculationType.FORCE_EVALUATION,
                CalculationType.OPTIMIZATION).contains(item.identity().calculationType()));

        return List.of(
                new StrategyComparisonRow("gaff2-baseline", "AmberTools/GAFF2",
                        "BASELINE_REJECTED_FOR_PRODUCTION", classical, List.of(), List.of(
                        "Existing baseline is fully reusable as a comparator.",
                        "Archived higher-level diagnostics support coupled angle/LJ failure; it is not a production solution.")),
                new StrategyComparisonRow("resp", "RESP", "COMPONENT_ACCEPTED_NOT_WHOLE_MODEL",
                        esp, List.of(), List.of(
                        "Accepted three-conformer RESP remains the supported permanent-charge component.",
                        "RESP does not provide a coherent bonded/torsion/LJ model.")),
                new StrategyComparisonRow("modified-seminario", "modified Seminario",
                        "EVALUATED_FAILED_EXISTING_EVIDENCE", hessians, List.of(), List.of(
                        "Three verified analytic Hessians are reusable.",
                        "Archived bonded-only V3 concludes HARMONIC_BONDED_FORM_INSUFFICIENT.")),
                new StrategyComparisonRow("qforce-style", "Q-Force",
                        "NOT_SELECTED_FUNCTIONAL_FORM_AND_INTEGRATION_UNRESOLVED", hessians,
                        List.of("coherent nonbonded derivation", "strategy-specific torsion evidence"), List.of(
                        "Hessian evidence is reusable only after method-level compatibility is demonstrated.",
                        "A standard single-harmonic junction remains contradicted by cross-minimum evidence.")),
                new StrategyComparisonRow("forcebalance", "ForceBalance",
                        "NOT_STANDALONE_MISSING_PROSPECTIVE_TRAINING", energiesAndForces,
                        List.of("prospectively split QM-native energy-and-force snapshot cloud",
                                "independently justified nonbonded model"), List.of(
                        "ForceBalance is an optimizer, not an independent force-field definition.",
                        "Existing stress-test grids cannot be reused as both training and validation.")),
                new StrategyComparisonRow("torsion-fit", "standard periodic torsion fit",
                        "LOCAL_REPAIR_REJECTED", scans, List.of(), List.of(
                        "Archived representability tests found standard local Amber angle/torsion/LJ repair inadequate.",
                        "A torsion-only fit is not a coherent whole-molecule model.")),
                new StrategyComparisonRow("qubekit", "QUBEKit/QUBE",
                        "BLOCKED_INFRASTRUCTURE_AND_PRODUCTION_COMPATIBILITY", List.of(), List.of(
                        "wB97X-D/6-311++G(d,p) optimization", "same-level analytic Hessian",
                        "Gaussian density plus DDEC6/Chargemol", "one to two TorsionDrive scans"), List.of(
                        "Protocol 5b is the coherent sulfur-capable whole-molecule workflow identified by the archived feasibility audit.",
                        "Gaussian 16 and Chargemol are unavailable; the local QUBEKit environment is not executable.",
                        "Compatibility with the required Amber protein stack is not demonstrated.")));
    }

    private static List<String> quantumHashes(
            EvidenceMemoryIndex evidence, Predicate<QuantumEvidence> predicate) {
        return evidence.quantum().stream().filter(predicate)
                .map(item -> item.identity().evidenceHash()).sorted().toList();
    }

    private static List<String> classicalHashes(
            EvidenceMemoryIndex evidence,
            Predicate<totah.lab.prometheus.evidence.ClassicalEvidence> predicate) {
        return evidence.classical().stream().filter(predicate)
                .map(item -> item.identity().evidenceHash()).sorted().toList();
    }

    private static EvidenceGenerationPlan blockedQubeKitPlan(
            MoleculeIdentity molecule, GeometryIdentity geometry) {
        List<EvidenceRequirement> requirements = List.of(
                requirement(molecule, geometry, CalculationType.OPTIMIZATION,
                        "QUBEKit protocol 5b whole-molecule optimization"),
                requirement(molecule, geometry, CalculationType.HESSIAN,
                        "QUBEKit protocol 5b analytic Hessian"),
                requirement(molecule, geometry, CalculationType.ESP,
                        "QUBEKit protocol 5b Gaussian density and DDEC6 partition"),
                requirement(molecule, geometry, CalculationType.TORSION_SCAN,
                        "QUBEKit protocol 5b TorsionDrive reference scans"));
        List<RequirementResolution> resolutions = requirements.stream()
                .map(requirement -> new RequirementResolution(
                        requirement,
                        PlanDecision.BLOCKED,
                        List.of(),
                        "QUBEKit protocol 5b requires a version-pinned Linux x86_64 environment,"
                                + " licensed Gaussian 16, and Chargemol/DDEC; Amber protein-stack"
                                + " compatibility must be resolved before calculation authorization."))
                .toList();
        return EvidenceGenerationPlan.of(resolutions, List.of());
    }

    private static EvidenceRequirement requirement(
            MoleculeIdentity molecule,
            GeometryIdentity geometry,
            CalculationType type,
            String purpose) {
        return new EvidenceRequirement(type, QUBEKIT_PROTOCOL, EnergyTarget.of(type), purpose,
                DatasetRole.DEVELOPMENT, true, molecule, geometry);
    }

    private static List<String> costNotes() {
        return List.of(
                "No calculation is authorized; plan cost is therefore zero.",
                "Archived QUBEKit feasibility estimate: up to 40 xTB seed preoptimizations; at least one full optimization; one analytic Hessian; one density/DDEC job; approximately one to two torsion scans.",
                "Archived practical workload estimate: approximately 3 major whole-molecule jobs plus 24–96 constrained optimization jobs.",
                "Archived local estimate: 2–7 days for one scan or 4–12 days for two scans.",
                "Archived 32-vCPU Linux estimate: 15–60 hours for one scan or 1.5–5 days for two scans.",
                "Archived illustrative on-demand envelope: USD 15–360, excluding Gaussian licensing and storage; exact licensed-instance benchmarking remains required.");
    }

    private static ExecutionDecisionRecord executionDecision(
            List<FailedCandidateRecord> outcomes,
            int provenanceGapCount) {
        List<String> references = outcomes.stream().map(FailedCandidateRecord::reportPath)
                .filter(path -> path.contains("05L") || path.contains("hessian-bonded-v3"))
                .sorted().toList();
        List<String> allReferences = new ArrayList<>(references);
        allReferences.add("execution-unit-05P-qubekit-feasibility/QUBEKIT_EXECUTION_DECISION.json");
        allReferences.add("execution-unit-05P-qubekit-feasibility/QUBEKIT_COST_ESTIMATE.md");
        return new ExecutionDecisionRecord(
                "tsl-rsh-prometheus-review-1",
                "NO_COHERENT_CLASSICAL_WORKFLOW_READY_FOR_EXECUTION",
                false,
                List.of(
                        "The accepted GAFF2/RESP baseline remains a comparator, not a validated production model, because the intramolecular coupled defect is supported.",
                        "Existing Hessian-derived harmonic replacement failed transferability and benchmark gates.",
                        "The coherent sulfur-capable QUBEKit protocol is unavailable on current infrastructure and its Amber protein-stack compatibility is unresolved.",
                        "Authoritative recovery reduced the canonical provenance gaps to "
                                + provenanceGapCount
                                + "; these are retained as genuinely unrecoverable legacy metadata rather than guessed values.",
                        "No new QM should launch until the execution backend and production force-field compatibility decision are approved.",
                        "The protein pilot remains locked.",
                        "Reject the fixed-charge classical route if one independently derived, frozen candidate fails the preregistered intramolecular, minimum-stability, and geometry-valid intermolecular holdouts without post-validation tuning."),
                allReferences);
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "usage: EvidenceReviewPackageAssembler <archive-root> <canonical-store-root> <output-directory>");
        }
        new EvidenceReviewPackageAssembler().write(
                Path.of(arguments[0]), Path.of(arguments[1]), Path.of(arguments[2]));
    }
}
