package totah.lab.pocket;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class PocketSimilarity {

    private PocketSimilarity() {
    }

    public static PocketComparison compare(Pocket a, Pocket b) {
        double[] ca =
                PocketGeometryUtil.calculateCenter(
                        a.getAlphaSpheres());
        double[] cb =
                PocketGeometryUtil.calculateCenter(
                        b.getAlphaSpheres());

        double centerDistance = distance(ca, cb);
        double volumeDifference = Math.abs(a.getVolume() - b.getVolume());
        Set<Residue> residuesA = new HashSet<>(a.getResidues());
        Set<Residue> residuesB = new HashSet<>(b.getResidues());

        Set<Residue> intersection = new HashSet<>(residuesA);
        intersection.retainAll(residuesB);

        Set<Residue> union = new HashSet<>(residuesA);
        union.addAll(residuesB);

        double jaccard = union.isEmpty()
                ? 0.0
                : (double) intersection.size() / union.size();

        return new PocketComparison(
                centerDistance,
                volumeDifference,
                intersection.size(),
                intersection.size(),
                jaccard
        );
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0]-b[0];
        double dy = a[1]-b[1];
        double dz = a[2]-b[2];
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
