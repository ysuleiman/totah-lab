package totah.lab.mettl7.triage;

import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;

import java.util.List;

public record Mettl7TriageInput(
        String identifier,
        String smiles,
        ChemistryFeatures chemistry,
        RecognitionFeatures recognition,
        ExperimentalFeatures experimental,
        CofactorEvidence cofactorEvidence,
        List<ChemicalLiabilityGate.Finding> liabilities,
        List<EvidenceObservation> evidence) {
    public Mettl7TriageInput {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        if (smiles == null || smiles.isBlank()) {
            throw new IllegalArgumentException("smiles must not be blank");
        }
        chemistry = java.util.Objects.requireNonNull(chemistry, "chemistry");
        recognition = recognition == null
                ? new RecognitionFeatures(java.util.Set.of(), java.util.Set.of(), false, false, false, false)
                : recognition;
        experimental = experimental == null ? ExperimentalFeatures.none() : experimental;
        cofactorEvidence = cofactorEvidence == null ? CofactorEvidence.none() : cofactorEvidence;
        liabilities = List.copyOf(liabilities == null ? List.of() : liabilities);
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
