package totah.lab.prometheus.evidence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single piece of quantum-chemical evidence: its identity, provenance,
 * convergence/acceptance state, and whichever numeric results were extracted.
 *
 * <p>Consistency rule: if convergence is {@code FAILED}, {@code NOT_CONVERGED} or
 * {@code EMPTY_OUTPUT}, the evidence must not be {@code ACCEPTED} — the compact
 * constructor throws {@link IllegalArgumentException} in that case.
 */
public record QuantumEvidence(
        EvidenceIdentity identity,
        EvidenceProvenance provenance,
        ConvergenceStatus convergence,
        EvidenceAcceptanceState acceptance,
        Optional<Double> energyHartree,
        Optional<List<Double>> gradientHartreePerBohr,
        Optional<List<Double>> hessianHartreePerBohr2,
        Optional<List<Double>> dipoleDebye,
        Optional<Double> interactionEnergyKcalMol,
        String convergenceNote) {

    public QuantumEvidence {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(convergence, "convergence");
        Objects.requireNonNull(acceptance, "acceptance");
        if (convergence != ConvergenceStatus.CONVERGED
                && acceptance == EvidenceAcceptanceState.ACCEPTED) {
            throw new IllegalArgumentException(
                    "evidence with convergence " + convergence + " cannot be ACCEPTED");
        }
        energyHartree = Objects.requireNonNull(energyHartree, "energyHartree");
        gradientHartreePerBohr = Objects.requireNonNull(gradientHartreePerBohr, "gradientHartreePerBohr")
                .map(List::copyOf);
        hessianHartreePerBohr2 = Objects.requireNonNull(hessianHartreePerBohr2, "hessianHartreePerBohr2")
                .map(List::copyOf);
        dipoleDebye = Objects.requireNonNull(dipoleDebye, "dipoleDebye")
                .map(List::copyOf);
        interactionEnergyKcalMol = Objects.requireNonNull(
                interactionEnergyKcalMol, "interactionEnergyKcalMol");
        Objects.requireNonNull(convergenceNote, "convergenceNote");
    }
}
