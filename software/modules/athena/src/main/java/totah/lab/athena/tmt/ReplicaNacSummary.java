package totah.lab.athena.tmt;

public record ReplicaNacSummary(
        String stateId,
        int replica,
        int frameCount,
        double nacFraction,
        int transitionsIntoNac,
        int transitionsOutOfNac,
        int recurrenceCount,
        double longestContinuousResidencePicoseconds) {
}
