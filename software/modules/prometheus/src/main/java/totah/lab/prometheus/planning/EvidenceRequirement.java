package totah.lab.prometheus.planning;

import java.util.Objects;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * A stated need for one piece of evidence: which calculation, under which
 * protocol, on which geometry of which molecule, for which purpose.
 *
 * <p>{@code geometry} is nullable ONLY when {@code required == false}: a missing
 * geometry on a required calculation cannot be planned, and the planner will
 * mark such a requirement {@link PlanDecision#BLOCKED}.
 *
 * <p>The stable legacy planner entry point treats this record as neutral
 * singlet, unconstrained, energy-only evidence. Wrap it in
 * {@link ScientificEvidenceRequirement} for an explicit electronic state and
 * calculation contract without changing this public record's API.
 */
public record EvidenceRequirement(
        CalculationType calculationType,
        QmProtocol protocol,
        EnergyTarget target,
        String purpose,
        DatasetRole role,
        boolean required,
        MoleculeIdentity molecule,
        GeometryIdentity geometry) {

    public EvidenceRequirement {
        Objects.requireNonNull(calculationType, "calculationType");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(purpose, "purpose");
        if (purpose.isBlank()) {
            throw new IllegalArgumentException("purpose must be non-blank");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(molecule, "molecule");
        // geometry may be null ONLY when required == false; a required
        // requirement with null geometry is constructed intact so the planner
        // can mark it BLOCKED with an explicit reason.
    }
}
