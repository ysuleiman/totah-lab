package totah.lab.athena.recognition;

import java.util.List;

public record RecognitionBasinBuildResult(List<RecognitionBasin> recurrentBasins,
        List<RecognitionBasin> nonRecurrentBasins,
        List<RecognitionStateObservation> ambiguousMemberships,
        List<EvidenceInsufficiency> evidenceInsufficiencies) {
    public RecognitionBasinBuildResult {
        recurrentBasins = List.copyOf(recurrentBasins); nonRecurrentBasins = List.copyOf(nonRecurrentBasins);
        ambiguousMemberships = List.copyOf(ambiguousMemberships);
        evidenceInsufficiencies = List.copyOf(evidenceInsufficiencies);
    }
    public record EvidenceInsufficiency(String firstPoseId, String secondPoseId, String reason) { }

    /** States affected by unavailable pair evidence; distinct from ambiguous cluster membership. */
    public List<RecognitionStateObservation> incompletePairEvidenceStates() {
        java.util.Set<String> ids = evidenceInsufficiencies.stream()
                .flatMap(value -> java.util.stream.Stream.of(value.firstPoseId(), value.secondPoseId()))
                .collect(java.util.stream.Collectors.toSet());
        return java.util.stream.Stream.concat(recurrentBasins.stream(), nonRecurrentBasins.stream())
                .flatMap(basin -> basin.members().stream()).filter(state -> ids.contains(state.poseId()))
                .distinct().toList();
    }
}
