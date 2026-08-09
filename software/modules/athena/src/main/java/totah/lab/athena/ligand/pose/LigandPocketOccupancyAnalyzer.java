package totah.lab.athena.ligand.pose;

import totah.lab.gaia.pocket.Pocket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregates per-pose {@link PosePocketAssignment}s into a
 * {@link LigandPocketOccupancy} report: how often predicted poses
 * occupy each pocket, with their Vina affinities and assignment scores
 * kept as separate, never-merged values.
 *
 * <p>Poses with {@link AssignmentStatus#NOT_ASSIGNED} are counted
 * separately; poses with {@link AssignmentStatus#AMBIGUOUS} count
 * toward their reported best pocket (the assignment itself carries the
 * ambiguity flag).
 */
public final class LigandPocketOccupancyAnalyzer {

    /**
     * Summarizes parallel lists of assignments and affinities: entry
     * {@code i} of {@code affinities} describes the pose of entry
     * {@code i} of {@code assignments}.
     *
     * @throws IllegalArgumentException if the lists differ in size
     */
    public LigandPocketOccupancy summarize(
            List<PosePocketAssignment> assignments,
            List<PoseAffinity> affinities
    ) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(affinities, "affinities");

        if (assignments.size() != affinities.size()) {
            throw new IllegalArgumentException(
                    "assignments and affinities must have the same "
                            + "size: " + assignments.size() + " vs "
                            + affinities.size()
            );
        }

        int total = assignments.size();
        int notAssignedCount = 0;
        Map<Pocket, List<Integer>> poseIndexesByPocket =
                new LinkedHashMap<>();

        for (int i = 0; i < total; i++) {
            PosePocketAssignment assignment = assignments.get(i);

            if (assignment.status() == AssignmentStatus.NOT_ASSIGNED) {
                notAssignedCount++;
                continue;
            }

            poseIndexesByPocket
                    .computeIfAbsent(
                            assignment.pocket(),
                            pocket -> new ArrayList<>()
                    )
                    .add(i);
        }

        List<PocketOccupancyEntry> entries = poseIndexesByPocket
                .entrySet()
                .stream()
                .map(entry -> toEntry(
                        entry.getKey(),
                        entry.getValue(),
                        assignments,
                        affinities,
                        total
                ))
                .sorted(Comparator
                        .comparingInt(PocketOccupancyEntry::poseCount)
                        .reversed()
                        .thenComparing(entry -> entry.pocket()
                                .id()
                                .value()))
                .toList();

        return new LigandPocketOccupancy(entries, notAssignedCount);
    }

    private static PocketOccupancyEntry toEntry(
            Pocket pocket,
            List<Integer> poseIndexes,
            List<PosePocketAssignment> assignments,
            List<PoseAffinity> affinities,
            int total
    ) {
        int poseCount = poseIndexes.size();

        List<Double> sortedAffinities = poseIndexes.stream()
                .map(index -> affinities.get(index).affinityKcalPerMol())
                .sorted()
                .toList();

        List<Double> scores = poseIndexes.stream()
                .map(index -> assignments.get(index).assignmentScore())
                .toList();

        double meanScore = scores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double bestScore = scores.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

        List<String> poseLabels = poseIndexes.stream()
                .map(index -> affinities.get(index).poseLabel())
                .toList();

        return new PocketOccupancyEntry(
                pocket,
                poseCount,
                poseCount / (double) total,
                sortedAffinities.get(0),
                median(sortedAffinities),
                meanScore,
                bestScore,
                poseLabels
        );
    }

    private static double median(List<Double> sortedValues) {
        int size = sortedValues.size();
        int middle = size / 2;

        if (size % 2 == 1) {
            return sortedValues.get(middle);
        }

        return (sortedValues.get(middle - 1)
                + sortedValues.get(middle)) / 2.0;
    }
}
