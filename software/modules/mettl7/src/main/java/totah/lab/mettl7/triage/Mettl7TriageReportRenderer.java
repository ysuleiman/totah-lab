package totah.lab.mettl7.triage;

/** Stable human-readable rendering; JSON remains the authoritative exchange form. */
public final class Mettl7TriageReportRenderer {
    public String render(Mettl7TriageResult result) {
        return """
                METTL7 LIGAND TRIAGE
                IDENTIFIER = %s
                RULESET = %s
                CHEMISTRY_CLASS = %s
                PLAUSIBLE_METHYL_ACCEPTOR = %s
                PRODUCTIVE_STATE_PLAUSIBILITY = %s
                7A_RECOGNITION_COMPATIBILITY = %s
                7B_RECOGNITION_COMPATIBILITY = %s
                A_SELECTIVITY_PRIOR = %s
                B_SELECTIVITY_PRIOR = %s
                SHARED_PRODUCTIVE_PRIOR = %s
                COFACTOR_STATE_PRIORITY = %s
                LIABILITY_FLAGS = %s
                EXPERIMENTAL_INFORMATION_VALUE = %s
                NEXT_ACTION = %s
                """.formatted(result.identifier(), result.rulesetVersion(), result.chemistryClass(),
                result.plausibleMethylAcceptor(), result.productiveStatePlausibility().level(),
                result.mettl7aRecognitionCompatibility().level(),
                result.mettl7bRecognitionCompatibility().level(), result.aSelectivityPrior().level(),
                result.bSelectivityPrior().level(), result.sharedProductivePrior().level(),
                result.cofactorStatePriority().priority(), result.liabilityFlags().findings(),
                result.experimentalInformationValue().level(), result.nextAction());
    }
}
