package totah.lab.athena.tmt;

import java.util.Objects;
import java.util.OptionalDouble;

/** One independently sampled ensemble frame with separate observables. */
public record EnsembleFrame(
        String stateId,
        int replica,
        long frameIndex,
        double timePicoseconds,
        NearAttackAssessment nearAttackAssessment,
        OptionalDouble backboneRmsdAngstrom,
        OptionalDouble activeSiteRmsdAngstrom,
        OptionalDouble substrateRmsdAngstrom,
        OptionalDouble samRmsdAngstrom,
        OptionalDouble pocketMouthAreaAngstromSquared,
        OptionalDouble catalyticWaterOccupancy,
        boolean substrateRetained,
        boolean samRetained) {

    public EnsembleFrame {
        stateId = requireText(stateId, "stateId");
        if (replica < 1) {
            throw new IllegalArgumentException("replica must be positive");
        }
        if (frameIndex < 0 || !Double.isFinite(timePicoseconds) || timePicoseconds < 0.0) {
            throw new IllegalArgumentException("frame index and time must be non-negative");
        }
        nearAttackAssessment = Objects.requireNonNull(nearAttackAssessment, "nearAttackAssessment");
        backboneRmsdAngstrom = requireOptional(backboneRmsdAngstrom, "backboneRmsdAngstrom");
        activeSiteRmsdAngstrom = requireOptional(activeSiteRmsdAngstrom, "activeSiteRmsdAngstrom");
        substrateRmsdAngstrom = requireOptional(substrateRmsdAngstrom, "substrateRmsdAngstrom");
        samRmsdAngstrom = requireOptional(samRmsdAngstrom, "samRmsdAngstrom");
        pocketMouthAreaAngstromSquared = requireOptional(
                pocketMouthAreaAngstromSquared, "pocketMouthAreaAngstromSquared");
        catalyticWaterOccupancy = requireOptional(catalyticWaterOccupancy, "catalyticWaterOccupancy");
    }

    public boolean candidateNac() {
        return nearAttackAssessment.classification()
                == NearAttackClassification.GEOMETRICALLY_NEAR_PRODUCTIVE
                || nearAttackAssessment.classification()
                == NearAttackClassification.CHEMICALLY_COMPATIBLE_CANDIDATE;
    }

    private static OptionalDouble requireOptional(OptionalDouble value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0.0)) {
            throw new IllegalArgumentException(name + " must be finite and non-negative when evaluated");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
