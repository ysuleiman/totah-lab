package totah.lab.athena.recognition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Conservative comparison of a recurrent source basin with observed target basins. */
public final class RecognitionBasinComparator {
    public Result compare(RecognitionBasin source, RecognitionBasinBuildResult target,
            RecognitionTopologyDistanceProvider topology,
            RecognitionTopologyDistance.ScalarPolicy scalarPolicy,
            RecognitionBasinComparisonPolicy policy) {
        if (!source.recurrent() || source.evidenceQuality() != EvidenceQuality.ADEQUATE) {
            return result(RecognitionBasinState.INSUFFICIENT_EVIDENCE, "source basin is not adequate and recurrent");
        }
        if (!target.evidenceInsufficiencies().isEmpty()) {
            return result(RecognitionBasinState.INSUFFICIENT_EVIDENCE, "target pairwise evidence is incomplete");
        }
        if (target.recurrentBasins().isEmpty()) {
            if (!target.nonRecurrentBasins().isEmpty()) {
                return result(RecognitionBasinState.UNOBSERVED_BASIN,
                        "target has observations but no recurrent recognition basin");
            }
            return policy.targetSamplingAdequateForLoss()
                    ? result(RecognitionBasinState.LOST_BASIN, "adequately sampled target has no observed basin")
                    : result(RecognitionBasinState.INSUFFICIENT_EVIDENCE, "absence is not loss without adequate sampling");
        }
        var sourceMedoid = source.members().stream().filter(m -> m.poseId().equals(source.topologyMedoidPoseId())).findFirst().orElseThrow();
        List<Candidate> candidates = new ArrayList<>();
        for (RecognitionBasin basin : target.recurrentBasins()) {
            var targetMedoid = basin.members().stream().filter(m -> m.poseId().equals(basin.topologyMedoidPoseId())).findFirst().orElseThrow();
            topology.distance(sourceMedoid, targetMedoid).ifPresent(distance -> candidates.add(
                    new Candidate(basin.id(), distance, distance.scalarDistance(scalarPolicy))));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::scalarDistance).thenComparing(Candidate::basinId));
        List<Candidate> within = candidates.stream()
                .filter(c -> c.scalarDistance() <= policy.maximumEquivalentTopologyDistance()).toList();
        if (within.size() > 1) return new Result(RecognitionBasinState.MULTIPLE_ALTERNATIVE_BASINS, within,
                "multiple recurrent target basins pass the explicit topology gate");
        if (within.isEmpty()) return new Result(RecognitionBasinState.FRAGMENTED, candidates,
                "target recurrent basin(s) do not preserve the source topology as a whole");
        Candidate best = within.getFirst(); RecognitionTopologyDistance d = best.distance();
        RecognitionBasinState state = d.reroutedEdges() > 0 ? RecognitionBasinState.REROUTED_BASIN
                : d.substitutedEdges() > 0 ? RecognitionBasinState.SUBSTITUTED_BASIN
                : d.lostEdges() + d.unobservedEdges() > 0 ? RecognitionBasinState.FRAGMENTED
                : RecognitionBasinState.PRESERVED_BASIN;
        return new Result(state, within, "classification follows explicit edge-change components");
    }
    private static Result result(RecognitionBasinState state, String reason) { return new Result(state, List.of(), reason); }
    public record Candidate(String basinId, RecognitionTopologyDistance distance, double scalarDistance) { }
    public record Result(RecognitionBasinState state, List<Candidate> candidates, String reason) {
        public Result { candidates = List.copyOf(candidates); }
    }
}
