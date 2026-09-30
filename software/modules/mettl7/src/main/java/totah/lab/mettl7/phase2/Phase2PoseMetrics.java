package totah.lab.mettl7.phase2;

import totah.lab.athena.interaction.InteractionFingerprint;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.euclid.spatial.RmsdClusterer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Campaign-specific metrics composed from canonical Athena evidence. */
public final class Phase2PoseMetrics {
    private Phase2PoseMetrics() { }

    public static FrozenFingerprintRetention frozenFingerprintRetention(
            InteractionFingerprint observed, Phase2MatchedSarPolicy policy) {
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(policy, "policy");
        Set<Phase2MatchedSarPolicy.TypedFeature> retained = policy.parentFingerprint().stream()
                .filter(feature -> observed.byResidue().entrySet().stream().anyMatch(entry ->
                        entry.getKey().residueNumber() == feature.residueNumber()
                                && entry.getValue().contains(feature.type())))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new FrozenFingerprintRetention(Phase2MatchedSarPolicy.FINGERPRINT_ID, retained,
                (double) retained.size() / policy.parentFingerprint().size());
    }

    public static AtomDistance atomSpecificDistance(List<Atom> ligandHeavyAtoms,
                                                     Atom receptorAtom,
                                                     int residueNumber) {
        Objects.requireNonNull(ligandHeavyAtoms, "ligandHeavyAtoms");
        Atom target = Objects.requireNonNull(receptorAtom, "receptorAtom");
        Atom closest = ligandHeavyAtoms.stream().filter(Atom::isHeavyAtom)
                .min(java.util.Comparator.comparingDouble(atom -> atom.getPosition().distance(target.getPosition())))
                .orElseThrow(() -> new IllegalArgumentException("ligand has no heavy atoms"));
        return new AtomDistance(residueNumber, target.getName(), closest.getName(),
                target.getPosition().distance(closest.getPosition()));
    }

    /** Fixed-frame comparison: no fitting, rotation, translation, or superposition is performed. */
    public static FixedFrameComparison fixedFrameCommonCore(
            List<Point3D> parent, List<Point3D> analogue, List<int[]> symmetryMappings) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(analogue, "analogue");
        if (parent.isEmpty() || symmetryMappings == null || symmetryMappings.isEmpty()) {
            throw new IllegalArgumentException("common core and symmetry mappings are required");
        }
        FixedFrameComparison best = null;
        for (int[] mapping : symmetryMappings) {
            if (mapping.length != parent.size()) throw new IllegalArgumentException("mapping length differs from parent core");
            List<Point3D> mapped = new ArrayList<>(mapping.length);
            for (int i = 0; i < mapping.length; i++) {
                if (mapping[i] < 0 || mapping[i] >= analogue.size()) throw new IllegalArgumentException("mapping index out of range");
                mapped.add(analogue.get(mapping[i]));
            }
            double rmsd = RmsdClusterer.rmsd(coordinates(parent), coordinates(mapped));
            double centroid = centroid(parent).distance(centroid(mapped));
            var candidate = new FixedFrameComparison(rmsd, centroid, mapping.clone());
            if (best == null || candidate.rmsdAngstroms() < best.rmsdAngstroms()
                    || candidate.rmsdAngstroms() == best.rmsdAngstroms()
                    && candidate.centroidShiftAngstroms() < best.centroidShiftAngstroms()) best = candidate;
        }
        return best;
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0, y = 0, z = 0;
        for (Point3D point : points) { x += point.x(); y += point.y(); z += point.z(); }
        return new Point3D(x / points.size(), y / points.size(), z / points.size());
    }

    private static List<double[]> coordinates(List<Point3D> points) {
        return points.stream().map(point -> new double[]{point.x(), point.y(), point.z()}).toList();
    }

    public record FrozenFingerprintRetention(String definitionId,
                                              Set<Phase2MatchedSarPolicy.TypedFeature> retained,
                                              double fraction) { }
    public record AtomDistance(int residueNumber, String receptorAtom, String ligandAtom,
                               double distanceAngstroms) { }
    public record FixedFrameComparison(double rmsdAngstroms, double centroidShiftAngstroms,
                                       int[] symmetryMapping) {
        public FixedFrameComparison { symmetryMapping = symmetryMapping.clone(); }
        @Override public int[] symmetryMapping() { return symmetryMapping.clone(); }
    }
}
