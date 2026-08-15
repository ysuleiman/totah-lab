package totah.lab.prometheus.evidence;

import java.util.Objects;

/**
 * A single piece of classical (force-field) evidence: an energy evaluation or
 * decomposition at a fixed geometry.
 *
 * <p>Classical evidence still uses {@link EvidenceIdentity}; its
 * {@link QmProtocol} slot carries force-field identification instead of a QM
 * level of theory: {@code protocol.method} = force-field id (e.g. "GAFF2"),
 * {@code protocol.basis} = "none", {@code protocol.software} e.g. "AmberTools".
 */
public record ClassicalEvidence(
        EvidenceIdentity identity,
        String forceFieldId,
        String topologyReference,
        EnergyDecomposition decomposition,
        EvidenceProvenance provenance,
        EvidenceAcceptanceState acceptance) {

    public ClassicalEvidence {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(forceFieldId, "forceFieldId");
        if (forceFieldId.isBlank()) {
            throw new IllegalArgumentException("forceFieldId must be non-blank");
        }
        Objects.requireNonNull(topologyReference, "topologyReference");
        Objects.requireNonNull(decomposition, "decomposition");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(acceptance, "acceptance");
    }
}
