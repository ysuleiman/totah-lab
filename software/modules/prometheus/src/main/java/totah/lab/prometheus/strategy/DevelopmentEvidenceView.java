package totah.lab.prometheus.strategy;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.evidence.ClassicalEvidence;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.MoleculeIdentity;
import totah.lab.prometheus.validation.DevelopmentDataset;

/**
 * Immutable, development-only evidence exposed to a strategy.
 *
 * <p>Quantum and classical evidence remain separate dimensions. The factory
 * admits only hashes preregistered in the development dataset and belonging to
 * the requested molecule; it has no holdout argument or holdout accessor.
 */
public record DevelopmentEvidenceView(
        List<QuantumEvidence> quantumEvidence,
        List<ClassicalEvidence> classicalEvidence) {

    public DevelopmentEvidenceView {
        quantumEvidence = List.copyOf(Objects.requireNonNull(quantumEvidence, "quantumEvidence"));
        classicalEvidence = List.copyOf(Objects.requireNonNull(classicalEvidence, "classicalEvidence"));
    }

    public static DevelopmentEvidenceView from(
            MoleculeIdentity molecule,
            DevelopmentDataset development,
            EvidenceBundle evidence) {

        Objects.requireNonNull(molecule, "molecule");
        Objects.requireNonNull(development, "development");
        Objects.requireNonNull(evidence, "evidence");
        Set<String> hashes = development.hashes();
        List<QuantumEvidence> quantum = evidence.quantum().stream()
                .filter(item -> item.identity().molecule().equals(molecule))
                .filter(item -> hashes.contains(item.identity().evidenceHash()))
                .toList();
        List<ClassicalEvidence> classical = evidence.classical().stream()
                .filter(item -> item.identity().molecule().equals(molecule))
                .filter(item -> hashes.contains(item.identity().evidenceHash()))
                .toList();
        return new DevelopmentEvidenceView(quantum, classical);
    }

    public List<String> quantumEvidenceHashes() {
        return quantumEvidence.stream().map(item -> item.identity().evidenceHash()).toList();
    }

    public List<String> classicalEvidenceHashes() {
        return classicalEvidence.stream().map(item -> item.identity().evidenceHash()).toList();
    }
}
