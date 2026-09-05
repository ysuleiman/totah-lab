package totah.lab.mettl7.triage;

import java.util.List;

public record Mettl7TriageResult(
        String identifier,
        String rulesetVersion,
        String chemistryClass,
        boolean plausibleMethylAcceptor,
        DimensionAssessment productiveStatePlausibility,
        DimensionAssessment mettl7aRecognitionCompatibility,
        DimensionAssessment mettl7bRecognitionCompatibility,
        DimensionAssessment aSelectivityPrior,
        DimensionAssessment bSelectivityPrior,
        DimensionAssessment sharedProductivePrior,
        CofactorStateAssessment cofactorStatePriority,
        LiabilityAssessment liabilityFlags,
        DimensionAssessment experimentalInformationValue,
        NextAction nextAction,
        List<EvidenceObservation> preservedEvidence) {
    public Mettl7TriageResult {
        preservedEvidence = List.copyOf(preservedEvidence);
    }
}
