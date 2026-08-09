package totah.lab.athena.pocket.component;

import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Groups raw cavities without selecting by fpocket rank. */
public final class ExperimentalBindingSiteGrouper {
    private final ExperimentalBindingSiteGroupingRule rule;

    public ExperimentalBindingSiteGrouper(
            ExperimentalBindingSiteGroupingRule rule) {
        this.rule = rule;
    }

    public ExperimentalBindingSiteGrouping group(
            List<ExperimentalSitePocket> candidates) {
        if (candidates.isEmpty()) {
            return new ExperimentalBindingSiteGrouping(List.of(), List.of(),
                    List.of());
        }
        List<ExperimentalSitePocket> ordered = candidates.stream()
                .sorted(Comparator.comparingLong(ExperimentalSitePocket::pocketId))
                .toList();
        List<PocketPairComparison> comparisons = new ArrayList<>();
        UnionFind groups = new UnionFind(ordered.size());
        for (int first = 0; first < ordered.size(); first++) {
            for (int second = first + 1; second < ordered.size(); second++) {
                PocketPairComparison comparison = compare(ordered.get(first),
                        ordered.get(second));
                comparisons.add(comparison);
                if (comparison.samePhysicalSite()
                        && ordered.get(first).strong()
                        && ordered.get(second).strong()) {
                    groups.union(first, second);
                }
            }
        }

        List<Integer> strong = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).strong()) strong.add(index);
        }
        if (strong.isEmpty()) {
            // Near-only evidence localizes one weak site but does not assert that
            // separated nearby cavities are equivalent.
            int best = java.util.stream.IntStream.range(0, ordered.size())
                    .boxed().min(Comparator.comparingDouble(index ->
                            ordered.get(index).minimumProteinDistance()))
                    .orElseThrow();
            return new ExperimentalBindingSiteGrouping(
                    List.of(toGroup(1, true, List.of(ordered.get(best)))),
                    comparisons, ordered.stream().map(ExperimentalSitePocket::pocketId)
                    .filter(id -> id != ordered.get(best).pocketId()).toList());
        }

        // Weak candidates may contribute only when they independently agree with
        // exactly one already localized strong group.
        for (int weak = 0; weak < ordered.size(); weak++) {
            if (ordered.get(weak).strong()) continue;
            Set<Integer> matchingRoots = new LinkedHashSet<>();
            for (int strongIndex : strong) {
                if (compare(ordered.get(weak), ordered.get(strongIndex))
                        .samePhysicalSite()) {
                    matchingRoots.add(groups.find(strongIndex));
                }
            }
            if (matchingRoots.size() == 1) {
                groups.union(weak, matchingRoots.iterator().next());
            }
        }

        Map<Integer, List<ExperimentalSitePocket>> grouped = new HashMap<>();
        List<Long> incidental = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            int root = groups.find(index);
            boolean rootHasStrong = strong.stream().anyMatch(value ->
                    groups.find(value) == root);
            if (!rootHasStrong) {
                incidental.add(ordered.get(index).pocketId());
            } else {
                grouped.computeIfAbsent(root, ignored -> new ArrayList<>())
                        .add(ordered.get(index));
            }
        }
        List<List<ExperimentalSitePocket>> values = grouped.values().stream()
                .sorted(Comparator.comparingLong(value -> value.stream()
                        .mapToLong(ExperimentalSitePocket::pocketId).min()
                        .orElseThrow())).toList();
        List<ExperimentalBindingSiteGroup> sites = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            sites.add(toGroup(index + 1, false, values.get(index)));
        }
        return new ExperimentalBindingSiteGrouping(sites, comparisons, incidental);
    }

    public PocketPairComparison compare(ExperimentalSitePocket first,
            ExperimentalSitePocket second) {
        int sharedResidues = intersection(first.residues(), second.residues());
        double residueJaccard = jaccard(first.residues(), second.residues());
        int sharedCovered = intersection(first.coveredLigandAtoms(),
                second.coveredLigandAtoms());
        double coveredJaccard = jaccard(first.coveredLigandAtoms(),
                second.coveredLigandAtoms());
        int sharedContacted = intersection(first.contactingLigandAtoms(),
                second.contactingLigandAtoms());
        double contactedJaccard = jaccard(first.contactingLigandAtoms(),
                second.contactingLigandAtoms());
        Set<String> firstEngaged = new LinkedHashSet<>(first.nearLigandAtoms());
        firstEngaged.addAll(first.contactingLigandAtoms());
        firstEngaged.addAll(first.coveredLigandAtoms());
        Set<String> secondEngaged = new LinkedHashSet<>(second.nearLigandAtoms());
        secondEngaged.addAll(second.contactingLigandAtoms());
        secondEngaged.addAll(second.coveredLigandAtoms());
        double engagedDistance = ligandAtomDistance(firstEngaged, secondEngaged,
                first.ligandAtomPositions());
        double sphereGap = sphereGap(first.spheres(), second.spheres());
        double centroidDistance = distance(first.centroid(), second.centroid());
        boolean sameChains = intersects(first.chains(), second.chains());
        boolean sameTargets = intersects(first.humanTargets(),
                second.humanTargets());
        boolean sameContext = sameTargets || (first.humanTargets().isEmpty()
                && second.humanTargets().isEmpty() && sameChains);
        boolean ligandAgreement = sharedCovered > 0 || sharedContacted > 0
                || coveredJaccard >= rule.ligandAtomJaccardThreshold()
                || contactedJaccard >= rule.ligandAtomJaccardThreshold()
                || engagedDistance
                <= rule.maximumEngagedLigandAtomDistanceAngstrom();
        boolean residueAgreement = residueJaccard
                >= rule.residueJaccardThreshold();
        boolean adjacentFragments = sameContext
                && sphereGap <= rule.maximumSphereSurfaceGapAngstrom()
                && centroidDistance
                <= rule.maximumPocketCentroidDistanceAngstrom();
        // A single experimental ligand occurrence is itself a physical anchor:
        // direct/occupying cavities on the same target context describe parts
        // of that one binding interface even when an elongated ligand makes
        // their sphere clouds disjoint. Weak NEAR evidence cannot use this rule.
        boolean sameLigandAnchoredInterface = first.strong() && second.strong();
        boolean sameSite = sameContext && (sameLigandAnchoredInterface
                || ligandAgreement || residueAgreement || adjacentFragments);
        return new PocketPairComparison(first.pocketId(), second.pocketId(),
                sharedResidues, residueJaccard, sphereGap, centroidDistance,
                sharedCovered, coveredJaccard, sharedContacted,
                contactedJaccard, engagedDistance, sameChains, sameTargets,
                sameSite);
    }

    private static ExperimentalBindingSiteGroup toGroup(int number,
            boolean weak, List<ExperimentalSitePocket> pockets) {
        return new ExperimentalBindingSiteGroup(number, weak,
                pockets.stream().map(ExperimentalSitePocket::pocketId).toList(),
                union(pockets, ExperimentalSitePocket::directContactResidues),
                union(pockets, ExperimentalSitePocket::nearShellResidues),
                union(pockets, ExperimentalSitePocket::chains),
                union(pockets, ExperimentalSitePocket::humanTargets),
                union(pockets, ExperimentalSitePocket::coveredLigandAtoms),
                union(pockets, ExperimentalSitePocket::contactingLigandAtoms));
    }

    private static Set<String> union(List<ExperimentalSitePocket> pockets,
            java.util.function.Function<ExperimentalSitePocket, Set<String>> f) {
        Set<String> result = new LinkedHashSet<>();
        pockets.forEach(pocket -> result.addAll(f.apply(pocket)));
        return result;
    }

    private static int intersection(Set<String> first, Set<String> second) {
        return (int) first.stream().filter(second::contains).count();
    }

    private static boolean intersects(Set<String> first, Set<String> second) {
        return first.stream().anyMatch(second::contains);
    }

    private static double jaccard(Set<String> first, Set<String> second) {
        if (first.isEmpty() && second.isEmpty()) return 0;
        int intersection = intersection(first, second);
        return (double) intersection /
                (first.size() + second.size() - intersection);
    }

    private static double sphereGap(List<PocketSphere> first,
            List<PocketSphere> second) {
        double minimum = Double.POSITIVE_INFINITY;
        for (PocketSphere a : first) for (PocketSphere b : second) {
            minimum = Math.min(minimum, Math.max(0,
                    distance(a.center(), b.center()) - a.radius() - b.radius()));
        }
        return minimum;
    }

    private static double ligandAtomDistance(Set<String> first,
            Set<String> second, Map<String, Point3D> positions) {
        double minimum = Double.POSITIVE_INFINITY;
        for (String a : first) for (String b : second) {
            Point3D firstPoint = positions.get(a), secondPoint = positions.get(b);
            if (firstPoint != null && secondPoint != null) {
                minimum = Math.min(minimum, distance(firstPoint, secondPoint));
            }
        }
        return minimum;
    }

    private static double distance(Point3D first, Point3D second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static final class UnionFind {
        private final int[] parent;
        private UnionFind(int size) {
            parent = java.util.stream.IntStream.range(0, size).toArray();
        }
        private int find(int value) {
            if (parent[value] != value) parent[value] = find(parent[value]);
            return parent[value];
        }
        private void union(int first, int second) {
            int a = find(first), b = find(second);
            if (a != b) parent[Math.max(a, b)] = Math.min(a, b);
        }
    }
}
