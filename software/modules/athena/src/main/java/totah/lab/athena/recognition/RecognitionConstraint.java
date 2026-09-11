package totah.lab.athena.recognition;

import totah.lab.athena.interaction.InteractionType;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Typed constraint evidence; evaluation and thresholds remain owned by their source detector/gate. */
public record RecognitionConstraint(
        String id,
        Type type,
        String ligandFeatureId,
        Optional<ResidueId> proteinFeature,
        List<String> geometricConditions,
        List<String> chemicalConditions,
        Evaluation evaluation,
        Optional<InteractionType> observedInteractionType,
        Optional<String> substituteEdgeId,
        EvidenceQuality quality,
        String provenance) {

    public enum Type {
        DONOR_ACCEPTOR_COMPATIBILITY,
        CATION_PI_GEOMETRY,
        PI_STACKING_GEOMETRY,
        HYDROPHOBIC_PROXIMITY,
        SALT_BRIDGE_GEOMETRY,
        STERIC_EXCLUSION,
        SAM_CLEARANCE,
        AROMATIC_FACE_AVAILABILITY,
        CHARGE_CENTER_AVAILABILITY,
        OTHER_EXISTING_EVIDENCE
    }

    public enum Evaluation { SATISFIED, TOLERATED, SUBSTITUTED, VIOLATED, UNAVAILABLE }

    public RecognitionConstraint {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        Objects.requireNonNull(type, "type");
        if (ligandFeatureId == null || ligandFeatureId.isBlank()) {
            throw new IllegalArgumentException("ligandFeatureId required");
        }
        proteinFeature = Objects.requireNonNull(proteinFeature, "proteinFeature");
        geometricConditions = List.copyOf(Objects.requireNonNull(geometricConditions,
                "geometricConditions"));
        chemicalConditions = List.copyOf(Objects.requireNonNull(chemicalConditions,
                "chemicalConditions"));
        Objects.requireNonNull(evaluation, "evaluation");
        observedInteractionType = Objects.requireNonNull(observedInteractionType,
                "observedInteractionType");
        substituteEdgeId = Objects.requireNonNull(substituteEdgeId, "substituteEdgeId");
        Objects.requireNonNull(quality, "quality");
        if (provenance == null || provenance.isBlank()) {
            throw new IllegalArgumentException("provenance required");
        }
        if (evaluation == Evaluation.SUBSTITUTED && substituteEdgeId.isEmpty()) {
            throw new IllegalArgumentException("substituted constraint requires substitute edge");
        }
        if (evaluation == Evaluation.UNAVAILABLE && quality != EvidenceQuality.UNAVAILABLE) {
            throw new IllegalArgumentException("unavailable evaluation requires unavailable quality");
        }
        if (quality == EvidenceQuality.UNAVAILABLE && evaluation != Evaluation.UNAVAILABLE) {
            throw new IllegalArgumentException("unavailable quality requires unavailable evaluation");
        }
    }
}
