package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/** Provider facts and explicitly evaluated metadata for one structure model. */
public record StructureEvidence(
        String accession,
        String provider,
        String chainId,
        int modelNumber,
        String assemblyId,
        StructureKind kind,
        String modelVersion,
        EvidenceChannel<String> experimentalMethod,
        EvidenceChannel<Double> resolutionAngstrom,
        EvidenceChannel<PredictedModelConfidence> predictedConfidence
) {
    public StructureEvidence {
        accession = requireText(accession, "accession");
        provider = requireText(provider, "provider");
        chainId = requireText(chainId, "chainId");
        if (modelNumber < 1) {
            throw new IllegalArgumentException("modelNumber must be positive");
        }
        assemblyId = optionalText(assemblyId);
        Objects.requireNonNull(kind, "kind");
        modelVersion = optionalText(modelVersion);
        Objects.requireNonNull(experimentalMethod, "experimentalMethod");
        Objects.requireNonNull(resolutionAngstrom, "resolutionAngstrom");
        Objects.requireNonNull(predictedConfidence, "predictedConfidence");
        EvidenceChannel.requireOrigin(experimentalMethod,
                EvidenceOrigin.SOURCE_REPORTED, "experimentalMethod");
        EvidenceChannel.requireOrigin(resolutionAngstrom,
                EvidenceOrigin.SOURCE_REPORTED, "resolutionAngstrom");
        EvidenceChannel.requireOrigin(predictedConfidence,
                EvidenceOrigin.SOURCE_REPORTED, "predictedConfidence");
        validateKind(kind, experimentalMethod, resolutionAngstrom,
                predictedConfidence);
    }

    private static void validateKind(StructureKind kind,
            EvidenceChannel<String> method,
            EvidenceChannel<Double> resolution,
            EvidenceChannel<PredictedModelConfidence> confidence) {
        if (kind == StructureKind.PREDICTED
                && (isEvaluated(method) || isEvaluated(resolution))) {
            throw new IllegalArgumentException(
                    "Predicted structures cannot report experimental method or resolution");
        }
        if (kind == StructureKind.EXPERIMENTAL && isEvaluated(confidence)) {
            throw new IllegalArgumentException(
                    "Experimental structures cannot report predicted-model confidence");
        }
    }

    private static boolean isEvaluated(EvidenceChannel<?> channel) {
        return channel.status() == EvaluationStatus.PRESENT
                || channel.status() == EvaluationStatus.EMPTY;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum StructureKind {
        EXPERIMENTAL,
        PREDICTED
    }
}
