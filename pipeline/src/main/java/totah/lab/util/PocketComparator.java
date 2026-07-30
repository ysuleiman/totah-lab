package totah.lab.util;


import totah.lab.pocket.Dimensions;
import totah.lab.pocket.Pocket;
import totah.lab.protein.Point3D;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class PocketComparator {

    private PocketComparator() {}

    /**
     * Calculates the direct Euclidean distance between the 3D center points of two pockets.
     * Essential for determining if two prediction tools identified the exact same cavity site.
     */
    public static double calculateCenterDistance(Pocket first, Pocket second) {
        Objects.requireNonNull(first, "first pocket cannot be null");
        Objects.requireNonNull(second, "second pocket cannot be null");

        Point3D posA = first.getCenter();
        Point3D posB = second.getCenter();

        if (posA == null || posB == null) {
            throw new IllegalArgumentException("Cannot calculate distance; one or both pocket centers are missing.");
        }

        double dx = posA.x() - posB.x();
        double dy = posA.y() - posB.y();
        double dz = posA.z() - posB.z();

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Computes the Tanimoto Coefficient (Jaccard Index) of the lining amino acid names.
     * Measures how closely aligned the biochemical environments are on a scale of 0.0 to 1.0.
     */
    public static double calculateResidueOverlap(Pocket first, Pocket second) {
        Objects.requireNonNull(first, "first pocket cannot be null");
        Objects.requireNonNull(second, "second pocket cannot be null");

        // Use standard Java Streams to map to simple 'Chain_Number' identifiers (e.g., "A_103")
        Set<String> setA = first.getResidueRefs().stream()
                .map(ref -> ref.chain() + "_" + ref.number())
                .collect(Collectors.toSet());

        Set<String> setB = second.getResidueRefs().stream()
                .map(ref -> ref.chain() + "_" + ref.number())
                .collect(Collectors.toSet());

        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;

        // Intersection (Common residues)
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        // Union (Total unique residues)
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    /**
     * Generates a structural footprint comparison report between two pockets.
     */
    public static ComparisonReport compare(Pocket first, Pocket second) {
        double centerDistance = calculateCenterDistance(first, second);
        double sequenceOverlap = calculateResidueOverlap(first, second);

        // Grab uniform dimensions via your Geometry utility
        Dimensions dimA = PocketGeometry.calculatePocketDimensions(first);
        Dimensions dimB = PocketGeometry.calculatePocketDimensions(second);

        double scoreDelta = Math.abs(
                (first.getScore() != null ? first.getScore() : 0.0) -
                        (second.getScore() != null ? second.getScore() : 0.0)
        );

        return new ComparisonReport(centerDistance, sequenceOverlap, scoreDelta, dimA, dimB);
    }

    /**
     * Immutable value container capturing pocket differences.
     */
    public record ComparisonReport(
            double centerDistanceAngstroms,
            double sequenceJaccardIndex, // 1.0 = identical lining residues, 0.0 = completely disjoint
            double scoreDifference,
            Dimensions firstDimensions,
            Dimensions secondDimensions
    ) {
        /**
         * Helper check to determine if two pockets from different tools (like P2Rank vs fpocket)
         * point to the identical physical binding cavity based on spatial thresholds.
         */
        public boolean isSameCavitySite() {
            // Standard structural benchmark: centers within 4.0 Å usually signify the same cleft
            return centerDistanceAngstroms <= 4.0;
        }
    }

}
