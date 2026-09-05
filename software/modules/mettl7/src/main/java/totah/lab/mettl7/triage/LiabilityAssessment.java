package totah.lab.mettl7.triage;

import totah.lab.athena.ligand.screening.ChemicalLiabilityGate;

import java.util.List;

public record LiabilityAssessment(boolean clear, List<ChemicalLiabilityGate.Finding> findings) {
    public LiabilityAssessment {
        findings = List.copyOf(findings);
        if (clear == !findings.isEmpty()) {
            throw new IllegalArgumentException("clear must agree with findings");
        }
    }
}
