package totah.lab.athena.recognition;

/** Explicit adequacy and topology gate for cross-ensemble basin classification. */
public record RecognitionBasinComparisonPolicy(double maximumEquivalentTopologyDistance,
        boolean targetSamplingAdequateForLoss, String provenance) {
    public RecognitionBasinComparisonPolicy {
        if (!Double.isFinite(maximumEquivalentTopologyDistance) || maximumEquivalentTopologyDistance < 0)
            throw new IllegalArgumentException("topology threshold invalid");
        if (provenance == null || provenance.isBlank()) throw new IllegalArgumentException("provenance required");
    }
}
