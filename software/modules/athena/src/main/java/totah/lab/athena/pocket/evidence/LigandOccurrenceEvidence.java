package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Objects;

/** Bound occurrence source truth plus independently derived pocket relationships. */
public record LigandOccurrenceEvidence(
        LigandOccurrenceId id,
        List<ObservedAtom> experimentalAtoms,
        EvidenceChannel<LigandPocketRelationship> pocketRelationship,
        EvidenceChannel<List<EvidenceResidueId>> contactingResidues,
        EvidenceChannel<List<InteractionObservation>> interactions,
        EvidenceChannel<String> ccdChemistryReference
) {
    public LigandOccurrenceEvidence {
        Objects.requireNonNull(id, "id");
        experimentalAtoms = List.copyOf(Objects.requireNonNull(
                experimentalAtoms, "experimentalAtoms"));
        Objects.requireNonNull(pocketRelationship, "pocketRelationship");
        Objects.requireNonNull(contactingResidues, "contactingResidues");
        Objects.requireNonNull(interactions, "interactions");
        Objects.requireNonNull(ccdChemistryReference, "ccdChemistryReference");
        EvidenceChannel.requireOrigin(pocketRelationship,
                EvidenceOrigin.DERIVED, "pocketRelationship");
        EvidenceChannel.requireOrigin(contactingResidues,
                EvidenceOrigin.DERIVED, "contactingResidues");
        EvidenceChannel.requireOrigin(interactions,
                EvidenceOrigin.DERIVED, "interactions");
        EvidenceChannel.requireOrigin(ccdChemistryReference,
                EvidenceOrigin.DERIVED, "ccdChemistryReference");
    }
}
