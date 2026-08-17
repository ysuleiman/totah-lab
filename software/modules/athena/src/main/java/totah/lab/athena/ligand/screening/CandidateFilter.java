package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reusable evidence-preserving ligand filter configured with a screening policy. */
public final class CandidateFilter {

    /** Outcome of the inexpensive intake screen that selects compounds to dock. */
    public enum IntakeStatus {
        REJECTED_BEFORE_DOCKING,
        ELIGIBLE_FOR_DOCKING,
        DCMB_CONTROL_ELIGIBLE_FOR_DOCKING
    }

    public record IncomingCandidate(
            String candidateId,
            String canonicalSmiles,
            LibraryEvidence library,
            String dockingState,
            DrugLikenessAssessment.Pool pool,
            PhysicochemicalGate.Descriptors descriptors,
            List<ChemicalLiabilityGate.Finding> liabilities,
            double dcmbTanimoto) {
        public IncomingCandidate {
            requireText(candidateId, "candidateId");
            requireText(canonicalSmiles, "canonicalSmiles");
            Objects.requireNonNull(library, "library");
            requireText(dockingState, "dockingState");
            Objects.requireNonNull(pool, "pool");
            Objects.requireNonNull(descriptors, "descriptors");
            liabilities = List.copyOf(Objects.requireNonNull(liabilities, "liabilities"));
            if (!Double.isFinite(dcmbTanimoto)
                    || dcmbTanimoto < 0.0 || dcmbTanimoto > 1.0) {
                throw new IllegalArgumentException("dcmbTanimoto must be between 0 and 1");
            }
        }
    }

    public record IntakeDisposition(
            IncomingCandidate candidate,
            IntakeStatus status,
            String reason,
            List<String> advisoryNotes,
            PhysicochemicalGate.Result physicochemical,
            DrugLikenessAssessment.Result drugLikeness,
            ChemicalLiabilityGate.Result liabilities) {
        public IntakeDisposition {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(status, "status");
            requireText(reason, "reason");
            advisoryNotes = List.copyOf(advisoryNotes);
            Objects.requireNonNull(physicochemical, "physicochemical");
            Objects.requireNonNull(drugLikeness, "drugLikeness");
            Objects.requireNonNull(liabilities, "liabilities");
        }
    }

    public record Policy(double primaryDcmbSimilarityMaximum,
                         double controlDcmbSimilarityMaximum) {
        public Policy {
            if (!Double.isFinite(primaryDcmbSimilarityMaximum)
                    || !Double.isFinite(controlDcmbSimilarityMaximum)
                    || primaryDcmbSimilarityMaximum < 0.0
                    || controlDcmbSimilarityMaximum
                    < primaryDcmbSimilarityMaximum
                    || controlDcmbSimilarityMaximum > 1.0) {
                throw new IllegalArgumentException("invalid similarity policy");
            }
        }

        public static Policy mettl7Discovery() {
            return new Policy(0.45, 0.75);
        }
    }

    public record LibraryEvidence(boolean purchasableOrMakeOnDemand,
                                  boolean unambiguousConnectivity,
                                  boolean pocketRelevantStereochemistryDefined,
                                  boolean dockingStateDefined,
                                  List<String> vendorIds,
                                  String availability) {
        public LibraryEvidence {
            vendorIds = List.copyOf(Objects.requireNonNull(vendorIds, "vendorIds"));
            if (vendorIds.isEmpty() || vendorIds.stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("vendorIds must not be empty or blank");
            }
            requireText(availability, "availability");
        }
    }

    /** Opaque upstream docking evidence retained without inventing a score. */
    public record DockingEvidence(String receptor,
                                  String settingsIdentity,
                                  List<String> poseFamilies,
                                  List<String> contactFingerprint,
                                  List<String> clashAssessment) {
        public DockingEvidence {
            requireText(receptor, "receptor");
            requireText(settingsIdentity, "settingsIdentity");
            poseFamilies = List.copyOf(poseFamilies);
            contactFingerprint = List.copyOf(contactFingerprint);
            clashAssessment = List.copyOf(clashAssessment);
        }
    }

    public record Candidate(
            String candidateId,
            String canonicalSmiles,
            LibraryEvidence library,
            String dockingState,
            DrugLikenessAssessment.Pool pool,
            PhysicochemicalGate.Descriptors descriptors,
            List<ChemicalLiabilityGate.Finding> liabilities,
            double dcmbTanimoto,
            DockingEvidence tmt1bDocking,
            DockingEvidence tmt1aDocking,
            CanonicalPocketGate.Evidence canonicalPocket,
            PoseReproducibilityGate.Evidence poseReproducibility,
            SamCompatibilityGate.Evidence samCompatibility,
            IsoformSelectivityComparator.Evidence isoformSelectivity,
            TslInterferenceClassifier.Evidence tmt1bTslInterference,
            TslInterferenceClassifier.Evidence tmt1aTslInterference) {

        public Candidate {
            requireText(candidateId, "candidateId");
            requireText(canonicalSmiles, "canonicalSmiles");
            Objects.requireNonNull(library, "library");
            requireText(dockingState, "dockingState");
            Objects.requireNonNull(pool, "pool");
            Objects.requireNonNull(descriptors, "descriptors");
            liabilities = List.copyOf(Objects.requireNonNull(liabilities, "liabilities"));
            if (!Double.isFinite(dcmbTanimoto)
                    || dcmbTanimoto < 0.0 || dcmbTanimoto > 1.0) {
                throw new IllegalArgumentException("dcmbTanimoto must be between 0 and 1");
            }
            Objects.requireNonNull(tmt1bDocking, "tmt1bDocking");
            Objects.requireNonNull(tmt1aDocking, "tmt1aDocking");
            Objects.requireNonNull(canonicalPocket, "canonicalPocket");
            Objects.requireNonNull(poseReproducibility, "poseReproducibility");
            Objects.requireNonNull(samCompatibility, "samCompatibility");
            Objects.requireNonNull(isoformSelectivity, "isoformSelectivity");
            Objects.requireNonNull(tmt1bTslInterference, "tmt1bTslInterference");
            Objects.requireNonNull(tmt1aTslInterference, "tmt1aTslInterference");
        }
    }

    private final Policy policy;
    private final PhysicochemicalGate physicochemicalGate;
    private final DrugLikenessAssessment drugLikenessAssessment;
    private final ChemicalLiabilityGate liabilityGate;
    private final CanonicalPocketGate canonicalPocketGate;
    private final PoseReproducibilityGate reproducibilityGate;
    private final SamCompatibilityGate samCompatibilityGate;
    private final IsoformSelectivityComparator selectivityComparator;
    private final TslInterferenceClassifier tslClassifier;

    public CandidateFilter() {
        this(Policy.mettl7Discovery(), new PhysicochemicalGate(),
                new DrugLikenessAssessment(), new ChemicalLiabilityGate(),
                new CanonicalPocketGate(), new PoseReproducibilityGate(),
                new SamCompatibilityGate(), new IsoformSelectivityComparator(),
                new TslInterferenceClassifier());
    }

    public CandidateFilter(Policy policy,
                           PhysicochemicalGate physicochemicalGate,
                           DrugLikenessAssessment drugLikenessAssessment,
                           ChemicalLiabilityGate liabilityGate,
                           CanonicalPocketGate canonicalPocketGate,
                           PoseReproducibilityGate reproducibilityGate,
                           SamCompatibilityGate samCompatibilityGate,
                           IsoformSelectivityComparator selectivityComparator,
                           TslInterferenceClassifier tslClassifier) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.physicochemicalGate = Objects.requireNonNull(physicochemicalGate);
        this.drugLikenessAssessment = Objects.requireNonNull(drugLikenessAssessment);
        this.liabilityGate = Objects.requireNonNull(liabilityGate);
        this.canonicalPocketGate = Objects.requireNonNull(canonicalPocketGate);
        this.reproducibilityGate = Objects.requireNonNull(reproducibilityGate);
        this.samCompatibilityGate = Objects.requireNonNull(samCompatibilityGate);
        this.selectivityComparator = Objects.requireNonNull(selectivityComparator);
        this.tslClassifier = Objects.requireNonNull(tslClassifier);
    }

    /**
     * Selects an incoming compound for docking without requiring any pose,
     * receptor, SAM, isoform, or TSL evidence.
     */
    public IntakeDisposition filterIncoming(IncomingCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        PhysicochemicalGate.Result physical =
                physicochemicalGate.evaluate(candidate.descriptors());
        DrugLikenessAssessment.Result drugLikeness =
                drugLikenessAssessment.assess(candidate.descriptors(), candidate.pool());
        ChemicalLiabilityGate.Result liabilities =
                liabilityGate.evaluate(candidate.liabilities());
        List<String> notes = advisoryNotes(physical, drugLikeness);

        String libraryReason = libraryRejection(candidate.library());
        if (libraryReason != null) {
            return intake(candidate, IntakeStatus.REJECTED_BEFORE_DOCKING,
                    libraryReason, notes, physical, drugLikeness, liabilities);
        }
        if (!physical.accepted()) {
            return intake(candidate, IntakeStatus.REJECTED_BEFORE_DOCKING,
                    String.join("; ", physical.reasons()), notes,
                    physical, drugLikeness, liabilities);
        }
        if (!liabilities.accepted()) {
            String reason = liabilities.findings().stream()
                    .map(ChemicalLiabilityGate.Finding::reason)
                    .reduce((left, right) -> left + "; " + right).orElseThrow();
            return intake(candidate, IntakeStatus.REJECTED_BEFORE_DOCKING,
                    reason, notes, physical, drugLikeness, liabilities);
        }
        if (candidate.dcmbTanimoto() > policy.controlDcmbSimilarityMaximum()) {
            return intake(candidate, IntakeStatus.REJECTED_BEFORE_DOCKING,
                    "DCMB similarity exceeds the configured control-neighborhood limit",
                    notes, physical, drugLikeness, liabilities);
        }
        if (candidate.dcmbTanimoto() > policy.primaryDcmbSimilarityMaximum()) {
            notes.add("DCMB-neighborhood SAR control; excluded from primary discovery");
            return intake(candidate, IntakeStatus.DCMB_CONTROL_ELIGIBLE_FOR_DOCKING,
                    "chemically eligible DCMB-neighborhood control", notes,
                    physical, drugLikeness, liabilities);
        }
        return intake(candidate, IntakeStatus.ELIGIBLE_FOR_DOCKING,
                "passes pre-docking intake filters", notes,
                physical, drugLikeness, liabilities);
    }

    public CandidateDisposition evaluate(Candidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        List<String> notes = new ArrayList<>();
        // Cheap evidence is intentionally computed eagerly so even an early
        // library rejection retains useful annotations. Structural gates below
        // continue to short-circuit because they are materially more expensive.
        PhysicochemicalGate.Result physical =
                physicochemicalGate.evaluate(candidate.descriptors());
        DrugLikenessAssessment.Result drugLikeness =
                drugLikenessAssessment.assess(candidate.descriptors(), candidate.pool());
        ChemicalLiabilityGate.Result liabilities =
                liabilityGate.evaluate(candidate.liabilities());
        notes.addAll(physical.preferences());
        if (!drugLikeness.lipinski().preferred()) {
            notes.add("Lipinski Rule-of-5 violations exceed the preferred maximum of one");
        }
        if (drugLikeness.fragmentRuleOf3().applicable()
                && !drugLikeness.fragmentRuleOf3().passes()) {
            notes.add("explicit compact-fragment pool candidate does not pass Rule-of-3");
        }

        String libraryReason = libraryRejection(candidate.library());
        if (libraryReason != null) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.LIBRARY_REQUIREMENT, libraryReason,
                    notes, physical, drugLikeness, liabilities,
                    null, null, null, null, null);
        }
        if (!physical.accepted()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.PHYSICOCHEMICAL,
                    String.join("; ", physical.reasons()), notes, physical,
                    drugLikeness, null, null, null, null, null, null);
        }

        if (!liabilities.accepted()) {
            String reason = liabilities.findings().stream()
                    .map(ChemicalLiabilityGate.Finding::reason)
                    .reduce((left, right) -> left + "; " + right).orElseThrow();
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.CHEMICAL_LIABILITY, reason, notes,
                    physical, drugLikeness, liabilities, null, null, null, null, null);
        }

        boolean dcmbControl = candidate.dcmbTanimoto()
                > policy.primaryDcmbSimilarityMaximum()
                && candidate.dcmbTanimoto() <= policy.controlDcmbSimilarityMaximum();
        if (candidate.dcmbTanimoto() > policy.controlDcmbSimilarityMaximum()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.DCMB_SIMILARITY,
                    "DCMB similarity exceeds the configured control-neighborhood limit",
                    notes, physical, drugLikeness, liabilities,
                    null, null, null, null, null);
        }
        if (dcmbControl) {
            notes.add("DCMB-neighborhood SAR control; excluded from primary discovery");
        }

        CanonicalPocketGate.Result pocket =
                canonicalPocketGate.evaluate(candidate.canonicalPocket());
        if (!pocket.accepted()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.CANONICAL_POCKET,
                    String.join("; ", pocket.reasons()), notes, physical,
                    drugLikeness, liabilities, pocket, null, null, null, null);
        }

        PoseReproducibilityGate.Result reproducibility =
                reproducibilityGate.evaluate(candidate.poseReproducibility());
        if (!reproducibility.accepted()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.POSE_REPRODUCIBILITY,
                    String.join("; ", reproducibility.reasons()), notes, physical,
                    drugLikeness, liabilities, pocket, reproducibility,
                    null, null, null);
        }

        SamCompatibilityGate.Result sam =
                samCompatibilityGate.evaluate(candidate.samCompatibility());
        if (!sam.accepted()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.SAM_COMPATIBILITY,
                    String.join("; ", sam.reasons()), notes, physical,
                    drugLikeness, liabilities, pocket, reproducibility,
                    sam, null, null);
        }

        IsoformSelectivityComparator.Result selectivity =
                selectivityComparator.compare(candidate.isoformSelectivity());
        if (!selectivity.selectiveEvidencePresent()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.ISOFORM_SELECTIVITY,
                    "no orthogonal TMT1B-over-TMT1A structural advantage; docking score alone is insufficient",
                    notes, physical, drugLikeness, liabilities, pocket,
                    reproducibility, sam, selectivity, null);
        }

        TslInterferenceClassifier.Comparison tsl = tslClassifier.compare(
                candidate.tmt1bTslInterference(), candidate.tmt1aTslInterference());
        if (tsl.provisionallySupportsTmt1bSelectivity()) {
            return disposition(candidate,
                    CandidateDisposition.Status.SELECTIVITY_REQUIRES_7A_TSL_RESOLUTION,
                    CandidateDisposition.Stage.ADVANCED,
                    "TMT1B productive-state interference is present, but TMT1A TSL interference must be resolved before a selectivity claim",
                    notes, physical, drugLikeness, liabilities, pocket,
                    reproducibility, sam, selectivity, tsl);
        }
        if (!tsl.stronglySupportsTmt1bSelectivity()) {
            return disposition(candidate, CandidateDisposition.Status.REJECTED,
                    CandidateDisposition.Stage.TSL_INTERFERENCE,
                    "resolved TSL comparison does not show TMT1B interference with TMT1A escape",
                    notes,
                    physical, drugLikeness, liabilities, pocket,
                    reproducibility, sam, selectivity, tsl);
        }

        CandidateDisposition.Status status = dcmbControl
                ? CandidateDisposition.Status.DCMB_NEIGHBORHOOD_CONTROL
                : CandidateDisposition.Status.PREDICTED_TMT1B_SELECTIVE_CANDIDATE;
        String reason = dcmbControl
                ? "qualifies only as a DCMB-neighborhood control"
                : "complete chemical, canonical-pose, SAM, isoform, and resolved TSL evidence";
        return disposition(candidate, status, CandidateDisposition.Stage.ADVANCED,
                reason, notes, physical, drugLikeness, liabilities, pocket,
                reproducibility, sam, selectivity, tsl);
    }

    private static String libraryRejection(LibraryEvidence evidence) {
        if (!evidence.purchasableOrMakeOnDemand()) return "not purchasable or make-on-demand";
        if (!evidence.unambiguousConnectivity()) return "ambiguous connectivity";
        if (!evidence.pocketRelevantStereochemistryDefined()) return "undefined pocket-relevant stereochemistry";
        if (!evidence.dockingStateDefined()) return "undefined protonation/tautomer state";
        return null;
    }

    private static List<String> advisoryNotes(
            PhysicochemicalGate.Result physical,
            DrugLikenessAssessment.Result drugLikeness) {
        List<String> notes = new ArrayList<>(physical.preferences());
        if (!drugLikeness.lipinski().preferred()) {
            notes.add("Lipinski Rule-of-5 violations exceed the preferred maximum of one");
        }
        if (drugLikeness.fragmentRuleOf3().applicable()
                && !drugLikeness.fragmentRuleOf3().passes()) {
            notes.add("explicit compact-fragment pool candidate does not pass Rule-of-3");
        }
        return notes;
    }

    private static IntakeDisposition intake(
            IncomingCandidate candidate, IntakeStatus status, String reason,
            List<String> notes, PhysicochemicalGate.Result physical,
            DrugLikenessAssessment.Result drugLikeness,
            ChemicalLiabilityGate.Result liabilities) {
        return new IntakeDisposition(candidate, status, reason, notes,
                physical, drugLikeness, liabilities);
    }

    private static CandidateDisposition disposition(
            Candidate candidate, CandidateDisposition.Status status,
            CandidateDisposition.Stage stage, String reason, List<String> notes,
            PhysicochemicalGate.Result physical,
            DrugLikenessAssessment.Result drugLikeness,
            ChemicalLiabilityGate.Result liabilities,
            CanonicalPocketGate.Result pocket,
            PoseReproducibilityGate.Result reproducibility,
            SamCompatibilityGate.Result sam,
            IsoformSelectivityComparator.Result selectivity,
            TslInterferenceClassifier.Comparison tsl) {
        return new CandidateDisposition(candidate, status, stage, reason, notes,
                physical, drugLikeness, liabilities, pocket, reproducibility,
                sam, selectivity, tsl);
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
