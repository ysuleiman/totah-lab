package totah.lab.mettl7.triage;

import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic evidence-preserving METTL7 ligand triage; not an affinity predictor. */
public final class Mettl7LigandTriageService {
    private final Mettl7TriageRuleset rules;
    private final ChemicalLiabilityGate liabilityGate;

    public Mettl7LigandTriageService() {
        this(Mettl7TriageRuleset.version1(), new ChemicalLiabilityGate());
    }

    public Mettl7LigandTriageService(Mettl7TriageRuleset rules,
                                    ChemicalLiabilityGate liabilityGate) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.liabilityGate = Objects.requireNonNull(liabilityGate, "liabilityGate");
    }

    public Mettl7TriageResult assess(Mettl7TriageInput input) {
        Objects.requireNonNull(input, "input");
        DimensionAssessment productive = productive(input);
        DimensionAssessment aRecognition = recognition(input.recognition().mettl7aContacts(),
                rules.mettl7aCore(), rules.mettl7aExtensions(), "METTL7A");
        DimensionAssessment bRecognition = bRecognition(input);
        DimensionAssessment aPrior = aPrior(input, aRecognition);
        DimensionAssessment bPrior = bPrior(input, bRecognition);
        DimensionAssessment shared = sharedProductive(input, productive);
        ChemicalLiabilityGate.Result liabilityResult = liabilityGate.evaluate(input.liabilities());
        LiabilityAssessment liabilities = new LiabilityAssessment(
                liabilityResult.accepted(), liabilityResult.findings());
        CofactorStateAssessment cofactor = cofactor(input);
        DimensionAssessment information = informationValue(input, productive, aPrior, bPrior);
        NextAction action = nextAction(input, productive, aPrior, bPrior, liabilities);
        return new Mettl7TriageResult(input.identifier(), rules.version(),
                input.chemistry().chemistryClass(), input.chemistry().plausibleMethylAcceptor(),
                productive, aRecognition, bRecognition, aPrior, bPrior, shared,
                cofactor, liabilities, information, action, input.evidence());
    }

    private DimensionAssessment productive(Mettl7TriageInput input) {
        ChemistryFeatures c = input.chemistry();
        if (input.experimental().productiveTurnoverEstablished()) {
            return dimension(AssessmentLevel.HIGH, "productive turnover is directly established", input);
        }
        if (c.arylamine() && !c.accessibleReactiveSulfur()) {
            return dimension(AssessmentLevel.LOW,
                    "arylamine chemistry is not a supported methyl acceptor in the METTL7 reference panel", input);
        }
        if (c.accessibleReactiveSulfur() && c.competentSamApproach() && c.toleratedTopology()) {
            return dimension(AssessmentLevel.HIGH,
                    "accessible reactive sulfur, competent SAM approach, and tolerated topology coexist", input);
        }
        if (c.accessibleReactiveSulfur()) {
            return dimension(AssessmentLevel.LOW,
                    "reactive sulfur alone is insufficient without SAM approach and tolerated topology", input);
        }
        if (c.plausibleMethylAcceptor()) {
            return dimension(AssessmentLevel.MODERATE,
                    "a methyl acceptor is plausible but productive reaction geometry is not established", input);
        }
        return dimension(AssessmentLevel.LOW, "no plausible productive methyl-acceptor state is supported", input);
    }

    private DimensionAssessment recognition(Set<String> observed, Set<String> core,
                                             Set<String> extensions, String paralog) {
        int coreCount = overlap(observed, core);
        int extensionCount = overlap(observed, extensions);
        AssessmentLevel level = coreCount >= rules.minimumCorroboratingContacts() ? AssessmentLevel.HIGH
                : coreCount + extensionCount >= rules.minimumCorroboratingContacts() ? AssessmentLevel.MODERATE
                : observed.isEmpty() ? AssessmentLevel.INDETERMINATE : AssessmentLevel.LOW;
        return new DimensionAssessment(level,
                List.of(paralog + " evidence preserves " + coreCount + " core and "
                        + extensionCount + " extension contacts; no single contact is decisive"), List.of());
    }

    private DimensionAssessment bRecognition(Mettl7TriageInput input) {
        RecognitionFeatures r = input.recognition();
        DimensionAssessment contacts = recognition(r.mettl7bContacts(), rules.mettl7bCore(),
                rules.mettl7bExtensions(), "METTL7B");
        int routeDimensions = (r.broadHydrophobicCanonicalRoute() ? 1 : 0)
                + (r.rearRoute() ? 1 : 0) + (r.context195To203() ? 1 : 0)
                + (r.context228To237() ? 1 : 0);
        if (contacts.level() == AssessmentLevel.MODERATE
                && routeDimensions >= rules.minimumRouteDimensions()) {
            return new DimensionAssessment(AssessmentLevel.HIGH,
                    List.of(contacts.reasons().getFirst() + "; multiple B-side route/context dimensions agree"),
                    contacts.evidence());
        }
        return contacts;
    }

    private DimensionAssessment aPrior(Mettl7TriageInput input, DimensionAssessment recognition) {
        if (input.experimental().mettl7aSelectiveEstablished()) {
            return dimension(AssessmentLevel.HIGH, "matched experimental evidence establishes A selectivity", input);
        }
        int dcmbWall = overlap(input.recognition().mettl7aContacts(), rules.dcmbSpecificWall());
        if (dcmbWall >= rules.minimumCorroboratingContacts()
                && recognition.level() != AssessmentLevel.LOW) {
            return dimension(AssessmentLevel.MODERATE,
                    "DCMB-wall and broader A recognition evidence support an A-selective prospect; the wall is not universal", input);
        }
        return dimension(recognition.level() == AssessmentLevel.HIGH ? AssessmentLevel.MODERATE : AssessmentLevel.LOW,
                "A recognition compatibility is a prior, not experimental selectivity", input);
    }

    private DimensionAssessment bPrior(Mettl7TriageInput input, DimensionAssessment recognition) {
        if (input.experimental().mettl7bSelectiveEstablished()) {
            return dimension(AssessmentLevel.HIGH, "matched experimental evidence establishes B selectivity", input);
        }
        if (input.experimental().mettl7bCompatibleOnly()) {
            return dimension(AssessmentLevel.MODERATE,
                    "existing evidence supports B compatibility only, not B selectivity", input);
        }
        return dimension(recognition.level() == AssessmentLevel.HIGH ? AssessmentLevel.MODERATE : AssessmentLevel.LOW,
                "B corridor compatibility remains prospective until matched A/B testing", input);
    }

    private DimensionAssessment sharedProductive(Mettl7TriageInput input, DimensionAssessment productive) {
        AssessmentLevel level = productive.level() == AssessmentLevel.HIGH
                ? AssessmentLevel.HIGH : AssessmentLevel.LOW;
        return dimension(level, level == AssessmentLevel.HIGH
                ? "productive chemistry supports a shared-substrate prior pending matched kinetics"
                : "shared productivity is not supported by the available reaction-state evidence", input);
    }

    private CofactorStateAssessment cofactor(Mettl7TriageInput input) {
        CofactorEvidence c = input.cofactorEvidence();
        List<CofactorState> missing = new ArrayList<>();
        if (!c.apoEvaluated()) missing.add(CofactorState.APO);
        if (!c.samEvaluated()) missing.add(CofactorState.SAM);
        if (!c.sahEvaluated()) missing.add(CofactorState.SAH);
        if (missing.isEmpty()) missing = List.of(CofactorState.APO, CofactorState.SAM, CofactorState.SAH);
        return new CofactorStateAssessment(missing,
                c.stateDependentRecognitionObserved()
                        ? "observed cofactor-state dependence requires explicit state comparison"
                        : "cofactor state can reweight recognition, but generalization beyond DCMB is untested",
                true);
    }

    private DimensionAssessment informationValue(Mettl7TriageInput input, DimensionAssessment productive,
                                                  DimensionAssessment a, DimensionAssessment b) {
        if (input.experimental().mettl7aSelectiveEstablished()
                || input.experimental().mettl7bSelectiveEstablished()) {
            return dimension(AssessmentLevel.HIGH, "an experimental directional anchor strongly informs the model", input);
        }
        if (a.level() == AssessmentLevel.MODERATE || b.level() == AssessmentLevel.MODERATE
                || productive.level() == AssessmentLevel.HIGH) {
            return dimension(AssessmentLevel.HIGH,
                    "a matched A/B experiment can distinguish productive or directional hypotheses", input);
        }
        if (input.chemistry().nonproductiveControl()) {
            return dimension(AssessmentLevel.MODERATE, "a defined negative control tests false-positive suppression", input);
        }
        return dimension(AssessmentLevel.LOW, "available evidence does not currently distinguish a major hypothesis", input);
    }

    private NextAction nextAction(Mettl7TriageInput input, DimensionAssessment productive,
                                  DimensionAssessment a, DimensionAssessment b,
                                  LiabilityAssessment liabilities) {
        boolean covalencyRisk = input.liabilities().stream().anyMatch(f -> switch (f.code()) {
            case NONSPECIFIC_ALKYLATOR, UNSTABLE_ELECTROPHILE, REACTIVE_MICHAEL_ACCEPTOR,
                    ACID_CHLORIDE, SULFONYL_CHLORIDE,
                    NONSPECIFIC_ISOCYANATE_OR_ISOTHIOCYANATE -> true;
            default -> false;
        });
        if (covalencyRisk) return NextAction.TEST_COVALENCY_FIRST;
        if (!liabilities.clear()) return NextAction.REJECT_LOW_INFORMATION;
        if (input.chemistry().nonproductiveControl()) return NextAction.KEEP_AS_NEGATIVE_CONTROL;
        if (productive.level() == AssessmentLevel.HIGH) return NextAction.TEST_PRODUCTIVE_TURNOVER_A_B;
        if (input.experimental().mettl7aSelectiveEstablished() || a.level() == AssessmentLevel.MODERATE)
            return NextAction.TEST_AS_A_SELECTIVE_PROSPECT;
        if (input.experimental().mettl7bCompatibleOnly() || b.level() == AssessmentLevel.MODERATE)
            return NextAction.TEST_AS_B_SELECTIVE_PROSPECT;
        if (input.cofactorEvidence().stateDependentRecognitionObserved())
            return NextAction.TEST_APO_SAM_SAH_BINDING;
        if (input.experimental().directBindingEstablished()) return NextAction.TEST_DIRECT_BINDING_A_B;
        return NextAction.REJECT_LOW_INFORMATION;
    }

    private static DimensionAssessment dimension(AssessmentLevel level, String reason, Mettl7TriageInput input) {
        return new DimensionAssessment(level, List.of(reason), input.evidence());
    }

    private static int overlap(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }
}
