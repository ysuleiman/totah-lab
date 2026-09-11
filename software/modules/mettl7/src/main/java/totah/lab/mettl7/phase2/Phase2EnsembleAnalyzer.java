package totah.lab.mettl7.phase2;

import totah.lab.euclid.spatial.CompleteLinkClusterer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Complete-link basins and seed-balanced recurrence for frozen Phase-2 distance matrices. */
public final class Phase2EnsembleAnalyzer {
    private static final double DISTANCE_MATRIX_TOLERANCE = 1.0e-9;
    private Phase2EnsembleAnalyzer() { }

    public static List<Basin> completeLink(List<PoseIdentity> poses, List<List<Double>> distances,
                                           double inclusiveThreshold,
                                           Set<Integer> newSeeds,
                                           Phase2MatchedSarPolicy policy) {
        validate(poses, distances, inclusiveThreshold);
        List<List<Integer>> groups = new CompleteLinkClusterer().cluster(distances, inclusiveThreshold);
        int totalSeeds = (int) poses.stream().map(PoseIdentity::seed).distinct().count();
        return java.util.stream.IntStream.range(0, groups.size()).mapToObj(index -> {
            List<Integer> members = List.copyOf(groups.get(index));
            Set<Integer> seeds = members.stream().map(i -> poses.get(i).seed())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Integer> added = seeds.stream().filter(newSeeds::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            double occupancy = totalSeeds == 0 ? 0 : (double) seeds.size() / totalSeeds;
            return new Basin(index + 1, members, Set.copyOf(seeds), Set.copyOf(added), occupancy,
                    Phase2DecisionRules.recurrent(added, seeds, policy));
        }).toList();
    }

    public static double seedBalancedOccupancy(List<PoseIdentity> poses, Set<Integer> expectedSeeds,
                                               Predicate<PoseIdentity> predicate) {
        Objects.requireNonNull(expectedSeeds, "expectedSeeds"); Objects.requireNonNull(predicate, "predicate");
        if (expectedSeeds.isEmpty()) throw new IllegalArgumentException("expected seeds must not be empty");
        Map<Integer, List<PoseIdentity>> bySeed = poses.stream().collect(Collectors.groupingBy(PoseIdentity::seed));
        long occupied = expectedSeeds.stream().filter(seed -> bySeed.getOrDefault(seed, List.of()).stream().anyMatch(predicate)).count();
        return (double) occupied / expectedSeeds.size();
    }

    private static void validate(List<PoseIdentity> poses, List<List<Double>> distances, double threshold) {
        Objects.requireNonNull(poses, "poses"); Objects.requireNonNull(distances, "distances");
        if (poses.isEmpty() || distances.size() != poses.size() || !Double.isFinite(threshold) || threshold < 0)
            throw new IllegalArgumentException("invalid poses, matrix, or threshold");
        if (poses.stream().anyMatch(Objects::isNull)
                || poses.stream().map(PoseIdentity::id).distinct().count() != poses.size())
            throw new IllegalArgumentException("pose identities must be non-blank and unique");
        for (int i = 0; i < distances.size(); i++) {
            List<Double> row = Objects.requireNonNull(distances.get(i), "distance row");
            if (row.size() != poses.size()
                    || row.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0))
                throw new IllegalArgumentException("distance matrix must be square, finite, and non-negative");
            if (Math.abs(row.get(i)) > DISTANCE_MATRIX_TOLERANCE)
                throw new IllegalArgumentException("distance matrix diagonal must be zero");
            for (int j = 0; j < i; j++) {
                if (Math.abs(row.get(j) - distances.get(j).get(i)) > DISTANCE_MATRIX_TOLERANCE)
                    throw new IllegalArgumentException("distance matrix must be symmetric");
            }
        }
    }

    public record PoseIdentity(String id, int seed, boolean bLike) {
        public PoseIdentity { if (id == null || id.isBlank()) throw new IllegalArgumentException("id required"); }
    }
    public record Basin(int number, List<Integer> memberIndices, Set<Integer> seeds,
                        Set<Integer> newSeeds, double seedBalancedOccupancy, boolean recurrent) { }
}
