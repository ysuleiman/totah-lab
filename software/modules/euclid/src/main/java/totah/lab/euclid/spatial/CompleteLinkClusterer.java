package totah.lab.euclid.spatial;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Single deterministic owner of complete-link clustering over a precomputed distance matrix. */
public final class CompleteLinkClusterer {
    public List<List<Integer>> cluster(List<List<Double>> distances, double inclusiveThreshold) {
        validate(distances, inclusiveThreshold);
        List<List<Integer>> clusters = new ArrayList<>();
        for (int index = 0; index < distances.size(); index++) clusters.add(new ArrayList<>(List.of(index)));
        while (true) {
            int bestA = -1, bestB = -1; double bestDistance = Double.MAX_VALUE;
            for (int a = 0; a < clusters.size(); a++) for (int b = a + 1; b < clusters.size(); b++) {
                double distance = completeLinkage(clusters.get(a), clusters.get(b), distances);
                if (distance < bestDistance) { bestDistance = distance; bestA = a; bestB = b; }
            }
            if (bestA < 0 || bestDistance > inclusiveThreshold) break;
            clusters.get(bestA).addAll(clusters.get(bestB)); clusters.remove(bestB);
        }
        clusters.sort(Comparator.<List<Integer>>comparingInt(List::size).reversed()
                .thenComparingInt(List::getFirst));
        return clusters.stream().map(List::copyOf).toList();
    }

    private static double completeLinkage(List<Integer> first, List<Integer> second,
                                          List<List<Double>> distances) {
        double maximum = 0;
        for (int a : first) for (int b : second) maximum = Math.max(maximum, distances.get(a).get(b));
        return maximum;
    }

    private static void validate(List<List<Double>> distances, double threshold) {
        Objects.requireNonNull(distances, "distances");
        if (!Double.isFinite(threshold) || threshold < 0) throw new IllegalArgumentException("threshold must be finite and non-negative");
        for (List<Double> row : distances) {
            if (row == null || row.size() != distances.size()) throw new IllegalArgumentException("distance matrix must be square");
            if (row.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0))
                throw new IllegalArgumentException("distances must be finite and non-negative");
        }
    }
}
