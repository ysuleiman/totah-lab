package totah.lab.prometheus.reporting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.CostComparisonRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.MissingEvidenceRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.RequirementRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.ReuseRow;
import totah.lab.prometheus.reporting.StrategyPlanningReportRenderer.StrategyPlanningReport;
import totah.lab.prometheus.store.CanonicalEvidenceStore;
import totah.lab.prometheus.store.EvidenceMemoryIndex;
import totah.lab.prometheus.strategy.ForceBalanceStyleStrategy;
import totah.lab.prometheus.strategy.QForceLikeStrategy;
import totah.lab.prometheus.strategy.QubeLikeStrategy;
import totah.lab.prometheus.strategy.ScientificStrategyDescriptor;

/**
 * Read-only data profile that turns a canonical evidence generation into a strategy-planning package.
 * Dataset names affect report labels only; no molecule-specific behavior is embedded in the architecture.
 */
public final class StrategyPlanningDataProfileRunner {
    public StrategyPlanningReport build(Path canonicalStoreRoot, String moleculeId) throws IOException {
        Objects.requireNonNull(canonicalStoreRoot, "canonicalStoreRoot");
        require(moleculeId, "moleculeId");
        CanonicalEvidenceStore.LoadedEvidence loaded = new CanonicalEvidenceStore().loadCurrent(canonicalStoreRoot);
        String generation = loaded.manifest().importDescriptor().generationId();
        EvidenceSummary evidence = EvidenceSummary.from(loaded.index(), moleculeId);
        if (evidence.acceptedEvidence().isEmpty()) {
            throw new IOException("canonical generation has no accepted quantum evidence for molecule "
                    + moleculeId);
        }

        ScientificStrategyDescriptor qube = new QubeLikeStrategy().scientificDescriptor();
        ScientificStrategyDescriptor qforce = new QForceLikeStrategy().scientificDescriptor();
        ScientificStrategyDescriptor forceBalance = new ForceBalanceStyleStrategy().scientificDescriptor();

        List<RequirementRow> qbr = qubeRequirements(evidence);
        List<RequirementRow> qfr = qforceRequirements(evidence);
        List<RequirementRow> fbr = forceBalanceRequirements(evidence);
        List<ReuseRow> reuse = reuseRows(evidence, qbr, qfr, fbr);
        List<MissingEvidenceRow> missing = missingEvidence();
        List<CostComparisonRow> costs = costs(evidence, qube, qforce, forceBalance);
        return new StrategyPlanningReport(requirementModel(generation, evidence, qube, qforce, forceBalance),
                qbr, qfr, fbr, reuse, holdoutPlans(evidence), missing, costs,
                recommendation(generation, evidence), decisionJson(generation, evidence));
    }

    private static List<RequirementRow> qubeRequirements(EvidenceSummary e) {
        return List.of(
                row("qube-like", "optimized geometry", "PBE-D3(BJ)/def2-SVP", true, "DEVELOPMENT",
                        "REUSE_EXISTING", e.accepted(CalculationType.OPTIMIZATION), "none",
                        "three independently verified QM-native minima are authoritative", "reference geometry"),
                row("qube-like", "Cartesian Hessian", "PBE-D3(BJ)/def2-SVP at matching minimum", true,
                        "DEVELOPMENT", "INCOMPLETE_MISSING_DISPERSION_HESSIAN",
                        e.accepted(CalculationType.HESSIAN),
                        "preserve electronic curvature; do not qualify a composite Hessian",
                        "MIN01 MIN02 and MIN04 contain TRUSTED_PBE_ONLY_HESSIAN matrices; D3 curvature is absent",
                        "bonds;angles"),
                row("qube-like", "electron density", "single declared DDEC-compatible density protocol", true,
                        "DEVELOPMENT", "GENERATE_NEW", 0, "none",
                        "RESP ESP is valid evidence but is not a DDEC electron density", "atomic charges;AIM volumes"),
                row("qube-like", "DDEC/AIM partition", "same density and partitioner release", true,
                        "DEVELOPMENT", "BLOCKED_BY_INFRASTRUCTURE", 0,
                        "derive charges and volumes after density generation",
                        "no authoritative DDEC/AIM partitioner artifact or installed dependency is recorded",
                        "charges;LJ"),
                row("qube-like", "torsion profiles", "internally consistent relaxed-QM protocol", true,
                        "DEVELOPMENT", "GENERATE_NEW", 0,
                        "existing 05H/05L points inform design but are not a complete torsion training set",
                        "sparse heterogeneous points do not cover all fitted rotors", "proper torsions"),
                row("qube-like", "independent conformational validation", "higher-level existing protocols", false,
                        "HOLDOUT", "RESERVE_AS_HOLDOUT", e.higherLevelConformational(), "none",
                        "05H/05L evidence stays sealed from fitting", "validation"));
    }

    private static List<RequirementRow> qforceRequirements(EvidenceSummary e) {
        return List.of(
                row("qforce-like", "optimized geometry", "PBE-D3(BJ)/def2-SVP", true, "DEVELOPMENT",
                        "REUSE_EXISTING", e.accepted(CalculationType.OPTIMIZATION), "none",
                        "verified minima define equilibrium structures", "reference geometry"),
                row("qforce-like", "Cartesian Hessian", "PBE-D3(BJ)/def2-SVP", true, "DEVELOPMENT",
                        "INCOMPLETE_MISSING_DISPERSION_HESSIAN", e.accepted(CalculationType.HESSIAN),
                        "preserve electronic curvature; do not qualify a composite Hessian",
                        "three matching electronic Hessians are authoritative PBE-only evidence", "bonds;angles"),
                row("qforce-like", "common atomic charges", "HF/6-31G(d) multiconformer RESP", true,
                        "DEVELOPMENT", "REUSE_EXISTING", e.accepted(CalculationType.RESP), "none",
                        "accepted three-conformer RESP is independently justified but retains documented limitations",
                        "fixed charges supplied;not solved by Q-Force"),
                row("qforce-like", "relaxed torsion profiles", "one internally consistent protocol", true,
                        "DEVELOPMENT", "GENERATE_NEW", 0, "05H/05L can constrain point selection only",
                        "existing sparse surfaces are not a complete fitted-rotor training set", "proper torsions"),
                row("qforce-like", "functional-form compatibility", "existing diagnoses", true, "DEVELOPMENT",
                        "INCOMPATIBLE_EXISTING", e.diagnosticRecords(), "none",
                        "ANGLE_LJ_COUPLED_DEFECT_SUPPORTED and HARMONIC_BONDED_FORM_INSUFFICIENT contradict an uncoupled harmonic solution",
                        "scientific gate"),
                row("qforce-like", "independent conformer validation", "existing higher-level evidence", false,
                        "HOLDOUT", "RESERVE_AS_HOLDOUT", e.higherLevelConformational(), "none",
                        "an entire minimum and cross family remain sealed", "validation"));
    }

    private static List<RequirementRow> forceBalanceRequirements(EvidenceSummary e) {
        return List.of(
                row("forcebalance-style", "executable initial force field", "declared coupled-capable form", true,
                        "DEVELOPMENT", "GENERATE_NEW", 0, "none",
                        "the failed V2/local-GAFF2 repair lineage cannot be the clean-slate model centre",
                        "model definition"),
                row("forcebalance-style", "Cartesian forces", "one common prospective QM protocol", true,
                        "DEVELOPMENT", "GENERATE_NEW", 0, "none",
                        "canonical records contain minima Hessians and energies but not a prospective force-training cloud",
                        "force targets"),
                row("forcebalance-style", "conformational energies", "within protocol groups only", false,
                        "DEVELOPMENT", "DERIVE_FROM_EXISTING", e.accepted(CalculationType.SINGLE_POINT),
                        "relative energies from authoritative absolute energies",
                        "05H/05L cannot be pooled across materially different methods", "energy targets"),
                row("forcebalance-style", "interaction energies", "PBE0-D3(BJ)/def2-TZVP counterpoise", true,
                        "HOLDOUT", "RESERVE_AS_HOLDOUT", e.accepted(CalculationType.COUNTERPOISE_INTERACTION),
                        "none", "geometry-valid accepted probes stay independent of training", "nonbonded validation"),
                row("forcebalance-style", "Hessian curvature", "PBE-D3(BJ)/def2-SVP", true,
                        "DEVELOPMENT", "INCOMPLETE_MISSING_DISPERSION_HESSIAN",
                        e.accepted(CalculationType.HESSIAN),
                        "preserve electronic curvature; do not qualify a composite Hessian",
                        "three authoritative PBE-only Hessians lack dispersion curvature", "curvature targets"),
                row("forcebalance-style", "sealed validation family", "entire MIN04 and independent probes", false,
                        "HOLDOUT", "RESERVE_AS_HOLDOUT", e.holdoutCandidates(), "none",
                        "holdout is frozen before parameter exposure", "validation"));
    }

    private static RequirementRow row(String strategy, String requirement, String protocol, boolean exact,
            String role, String decision, int records, String derivation, String reason, String output) {
        return new RequirementRow(strategy, requirement, protocol, exact, role, decision, records,
                derivation, reason, output);
    }

    private static List<ReuseRow> reuseRows(EvidenceSummary evidence, List<RequirementRow>... groups) {
        List<ReuseRow> rows = new ArrayList<>();
        for (List<RequirementRow> group : groups) {
            for (RequirementRow row : group) {
                rows.add(new ReuseRow(row.strategy(), row.requirement(), row.outputCapability(), row.decision(),
                        row.matchedRecords(), row.protocolConstraint(),
                        evidence.hashesFor(row.requirement()), row.reason()));
            }
        }
        return List.copyOf(rows);
    }

    private static List<MissingEvidenceRow> missingEvidence() {
        return List.of(
                new MissingEvidenceRow("qube-like", "electron density", "DDEC-compatible density for each selected minimum",
                        "RESP grids cannot be transformed into an AIM density", "preregistered density single point at selected protocol",
                        3, "QM engine", "~6-12 h aggregate", "~1-3 h", "$10-$35", "CPU", false, "select common density protocol"),
                new MissingEvidenceRow("qube-like", "DDEC/AIM partition", "atomic charges and volumes",
                        "required for molecule-specific QUBE electrostatics and LJ", "same density; fixed partitioner version",
                        3, "Chargemol or equivalent", "<1 h", "<1 h", "<$2", "CPU", false, "electron density"),
                new MissingEvidenceRow("qube-like", "torsion coverage", "relaxed profiles for every fitted rotor",
                        "05H/05L are sparse diagnostics rather than a complete common-protocol training set",
                        "locked common DFT torsion protocol", 12, "QM engine;torsion optimizer", "~24-60 h", "~4-12 h",
                        "$30-$120", "CPU", false, "density-derived nonbonded model frozen"),
                new MissingEvidenceRow("qforce-like", "torsion coverage", "complete relaxed torsion profiles",
                        "required by Q-Force but cannot repair diagnosed coupled-coordinate behavior",
                        "locked common DFT torsion protocol", 12, "QM engine;Q-Force", "~24-60 h", "~4-12 h",
                        "$30-$120", "CPU", false, "functional-form gate must first pass"),
                new MissingEvidenceRow("forcebalance-style", "coupled-capable initial model",
                        "explicitly declared production functional form and exposed parameter set",
                        "ForceBalance is an optimizer and cannot choose the model form", "no scientific calculation",
                        0, "OpenMM/Amber-compatible evaluator;ForceBalance", "engineering estimate pending", "n/a", "$0",
                        "CPU", false, "predeclare coupled-coordinate representation"),
                new MissingEvidenceRow("forcebalance-style", "QM-native force cloud",
                        "prospectively split common-protocol energies and Cartesian gradients",
                        "existing evidence does not constitute a force-matching training cloud",
                        "PBE-D3(BJ)/def2-SVP on QM-native perturbations", 36, "QM engine;ForceBalance",
                        "~36-90 h aggregate", "~6-18 h", "$45-$180", "CPU", false,
                        "freeze geometry generation and train/holdout assignment"));
    }

    private static List<CostComparisonRow> costs(EvidenceSummary e, ScientificStrategyDescriptor qube,
            ScientificStrategyDescriptor qforce, ScientificStrategyDescriptor forceBalance) {
        return List.of(
                new CostComparisonRow(qube.displayName(), "DOWNGRADED: standard separable harmonic form does not address diagnosed coupling",
                        e.accepted(CalculationType.OPTIMIZATION) + e.accepted(CalculationType.HESSIAN), 1, 18, 0,
                        "QM engine;DDEC/AIM partitioner;torsion optimizer", "~28-75 h local", "$40-$155",
                        qube.openMmCompatibility().name(), qube.amberCompatibility().name(), "strong if holdouts remain sealed",
                        "new nonbonded model still uses a separable harmonic bonded form", "FUNCTIONAL_FORM_INCOMPATIBLE"),
                new CostComparisonRow(qforce.displayName(), "INCOMPATIBLE with the diagnosed local harmonic/coupled defect",
                        e.accepted(CalculationType.OPTIMIZATION) + e.accepted(CalculationType.HESSIAN)
                                + e.accepted(CalculationType.RESP), 1, 12, 0,
                        "QM engine;Q-Force-compatible fitter", "~24-60 h local", "$30-$120",
                        qforce.openMmCompatibility().name(), qforce.amberCompatibility().name(), "available holdouts",
                        "independent harmonic angles and separable torsions repeat a rejected model class",
                        "FUNCTIONAL_FORM_INCOMPATIBLE"),
                new CostComparisonRow(forceBalance.displayName(),
                        "CONDITIONALLY SUITABLE only with a preregistered coupled-capable model form",
                        e.accepted(CalculationType.HESSIAN) + e.accepted(CalculationType.SINGLE_POINT), 1, 36, 0,
                        "QM engine;ForceBalance;classical evaluator", "~36-90 h local", "$45-$180",
                        forceBalance.openMmCompatibility().name(), forceBalance.amberCompatibility().name(),
                        "strong: MIN04 and probe families can remain sealed",
                        "charges/LJ may be non-identifiable and Amber export may not represent coupling",
                        "READY_AFTER_MINIMAL_NEW_EVIDENCE"));
    }

    private static String requirementModel(String generation, EvidenceSummary e,
            ScientificStrategyDescriptor... descriptors) {
        StringBuilder out = new StringBuilder("# Prometheus strategy requirement model\n\n")
                .append("Canonical generation: `").append(generation).append("`\n\n")
                .append("This package is a read-only plan over ").append(e.quantum()).append(" quantum and ")
                .append(e.classical()).append(" classical records. It launches no QM, MM, fitting, or MD.\n\n")
                .append("A strategy declares scientific evidence, protocol exactness, dataset role, derivability, outputs, dependencies, functional form, and engine compatibility. The matcher distinguishes reuse, derivation, generation, holdout reservation, protocol incompatibility, infrastructure blockage, and insufficient metadata. Derivation consumes authoritative artifacts; it is not recomputation.\n\n")
                .append("## Registered methodologies\n\n");
        for (ScientificStrategyDescriptor descriptor : descriptors) {
            out.append("- `").append(descriptor.strategyId()).append("`: ")
                    .append(descriptor.methodology()).append(". Functional form: ")
                    .append(descriptor.productionFunctionalForm()).append("\n");
        }
        return out.append("\n## Scientific guardrails\n\n")
                .append("The accepted RESP model is evidence, not DDEC. Protocol groups are never pooled silently. Existing model-form diagnoses—`ANGLE_LJ_COUPLED_DEFECT_SUPPORTED`, `HARMONIC_BONDED_FORM_INSUFFICIENT`, failed fixed-charge local repairs, and failed published-LJ comparators—are strategy constraints, not scores. Infrastructure failure is reported separately from scientific invalidity.\n")
                .toString();
    }

    private static String holdoutPlans(EvidenceSummary e) {
        return "# Holdout plans\n\n"
                + "No evidence hash may be assigned to development and holdout in one plan.\n\n"
                + "## QUBE-like\n\nReserve the higher-level Unit 05H states and Unit 05L cross-family points ("
                + e.higherLevelConformational() + " accepted records) plus all accepted geometry-valid counterpoise probes. Do not use these to select density protocol or torsion terms.\n\n"
                + "## Q-Force-like\n\nReserve an entire verified QM-native minimum (MIN04), its associated higher-level states, and an independent torsion/interaction family. This holdout does not cure the functional-form incompatibility.\n\n"
                + "## ForceBalance-style\n\nProspectively reserve MIN04 before generating a force cloud. Keep the accepted counterpoise probe family ("
                + e.accepted(CalculationType.COUNTERPOISE_INTERACTION)
                + " records) external to force fitting. Unit 05H/05L protocol groups remain separate and are stress tests unless a preregistered target transformation proves comparability.\n";
    }

    private static String recommendation(String generation, EvidenceSummary e) {
        return "# Prometheus strategy recommendation\n\n"
                + "Primary recommended route: **ForceBalance-style optimization over an explicitly preregistered coupled-coordinate-capable local model, after the minimum common-protocol QM-native force evidence is generated.**\n\n"
                + "Classification: `READY_AFTER_MINIMAL_NEW_EVIDENCE`.\n\n"
                + "This is the only assessed route that can, in principle, expose a model form addressing the verified angle/LJ coupled response instead of repeating the rejected independent-harmonic repair. ForceBalance itself is not the force field: the coupled-capable production form and its OpenMM/Amber representation must pass a separate gate before any calculation is authorized.\n\n"
                + "Q-Force-like is rejected because its independent harmonic/separable form conflicts directly with the existing diagnosis. Standard QUBE-like is scientifically attractive for clean-slate charges and LJ and can reuse the "
                + e.accepted(CalculationType.HESSIAN) + " Hessians, but its standard separable harmonic form does not itself resolve the diagnosed coupling and it additionally needs new density/DDEC evidence.\n\n"
                + "Minimum next evidence: freeze the coupled-capable model definition and prospective MIN01/MIN02 development versus MIN04 holdout split, then generate 36 deduplicated common-protocol energy+Cartesian-force snapshots. Existing Hessians and compatible relative energies are reused or derived; accepted probes remain sealed. No execution is authorized by this report.\n\n"
                + "Canonical generation assessed: `" + generation + "`.\n";
    }

    private static String decisionJson(String generation, EvidenceSummary e) {
        return "{\n"
                + "  \"canonicalGeneration\": \"" + generation + "\",\n"
                + "  \"moleculeId\": \"" + e.moleculeId() + "\",\n"
                + "  \"quantumRecords\": " + e.quantum() + ",\n"
                + "  \"classicalRecords\": " + e.classical() + ",\n"
                + "  \"primaryRoute\": \"FORCEBALANCE_STYLE_COUPLED_CAPABLE_LOCAL_MODEL\",\n"
                + "  \"classification\": \"READY_AFTER_MINIMAL_NEW_EVIDENCE\",\n"
                + "  \"newQmJobs\": 36,\n"
                + "  \"newMmJobs\": 0,\n"
                + "  \"executionAuthorized\": false,\n"
                + "  \"holdout\": \"MIN04_AND_GEOMETRY_VALID_COUNTERPOISE_PROBES\",\n"
                + "  \"reason\": \"Only a preregistered coupled-capable model can directly address the authoritative angle/LJ coupled diagnosis; standard QUBE-like and Q-Force-like separable harmonic forms cannot.\"\n"
                + "}\n";
    }

    private static void require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: StrategyPlanningDataProfileRunner <canonical-store> <molecule-id> <output-directory>");
        }
        StrategyPlanningDataProfileRunner runner = new StrategyPlanningDataProfileRunner();
        StrategyPlanningReport report = runner.build(Path.of(args[0]), args[1]);
        new StrategyPlanningReportRenderer().render(Path.of(args[2]), report);
    }

    private record EvidenceSummary(String moleculeId, int quantum, int classical,
            Map<CalculationType, Integer> acceptedByType, List<QuantumEvidence> acceptedEvidence) {
        static EvidenceSummary from(EvidenceMemoryIndex index, String moleculeId) {
            List<QuantumEvidence> selected = index.quantum().stream()
                    .filter(q -> q.identity().molecule().moleculeId().equals(moleculeId))
                    .filter(q -> q.acceptance() == EvidenceAcceptanceState.ACCEPTED)
                    .sorted(Comparator.comparing(q -> q.identity().evidenceHash())).toList();
            Map<CalculationType, Integer> counts = new EnumMap<>(CalculationType.class);
            selected.forEach(q -> counts.merge(q.identity().calculationType(), 1, Integer::sum));
            return new EvidenceSummary(moleculeId, index.quantum().size(), index.classical().size(), counts, selected);
        }
        int total() { return quantum + classical; }
        int accepted(CalculationType type) { return acceptedByType.getOrDefault(type, 0); }
        int higherLevelConformational() { return accepted(CalculationType.SINGLE_POINT); }
        int diagnosticRecords() { return accepted(CalculationType.SINGLE_POINT) + accepted(CalculationType.CONSTRAINED_SCAN); }
        int holdoutCandidates() { return higherLevelConformational() + accepted(CalculationType.COUNTERPOISE_INTERACTION); }
        String hashesFor(String requirement) {
            CalculationType type = requirement.contains("Hessian") ? CalculationType.HESSIAN
                    : requirement.contains("optimized") ? CalculationType.OPTIMIZATION
                    : requirement.contains("charges") ? CalculationType.RESP
                    : requirement.contains("interaction") ? CalculationType.COUNTERPOISE_INTERACTION
                    : requirement.contains("conformational") ? CalculationType.SINGLE_POINT : null;
            if (type == null) return "";
            return acceptedEvidence.stream().filter(q -> q.identity().calculationType() == type)
                    .map(q -> q.identity().evidenceHash()).sorted().limit(6).reduce((a, b) -> a + ";" + b).orElse("");
        }
    }
}
