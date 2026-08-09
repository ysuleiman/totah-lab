package totah.lab.athena.pocket.evidence.grammar;

import totah.lab.athena.pocket.evidence.EvaluationStatus;
import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;

/** CA-superposition-derived positional variability across experimental chains. */
public final class ExperimentalStructuralVariabilityCalculator {
    public static final String METHOD = "EXPERIMENTAL_CA_SUPERPOSITION_RMSF";
    public static final String VERSION = "1";
    private static final int MINIMUM_ALIGNMENT_POSITIONS = 3;

    public Map<Integer, StructuralVariabilityEvidence> calculate(
            List<ExperimentalCoordinateObservation> input) {
        if (input.isEmpty()) return Map.of();
        List<ExperimentalCoordinateObservation> observations = input.stream()
                .sorted(Comparator.comparingInt(
                                (ExperimentalCoordinateObservation value) ->
                                        value.residues().size()).reversed()
                        .thenComparing(ExperimentalCoordinateObservation::observationId))
                .toList();
        ExperimentalCoordinateObservation reference = observations.getFirst();
        Map<Integer, List<Point3D>> ca = new LinkedHashMap<>();
        Map<Integer, List<Point3D>> side = new LinkedHashMap<>();
        add(reference.residues(), ca, side, null);
        for (int index = 1; index < observations.size(); index++) {
            ExperimentalCoordinateObservation mobile = observations.get(index);
            List<Integer> common = reference.residues().keySet().stream()
                    .filter(mobile.residues()::containsKey).sorted().toList();
            if (common.size() < MINIMUM_ALIGNMENT_POSITIONS) continue;
            var fit = new KabschRigidPointAligner().align(
                    common.stream().map(position -> mobile.residues()
                            .get(position).ca()).toList(),
                    common.stream().map(position -> reference.residues()
                            .get(position).ca()).toList());
            add(mobile.residues(), ca, side, fit);
        }
        Set<Integer> positions = new LinkedHashSet<>();
        observations.forEach(value -> positions.addAll(value.residues().keySet()));
        Map<Integer, StructuralVariabilityEvidence> result = new LinkedHashMap<>();
        positions.stream().sorted().forEach(position -> {
            List<Point3D> caPoints = ca.getOrDefault(position, List.of());
            List<Point3D> sidePoints = side.getOrDefault(position, List.of());
            if (caPoints.size() < 2) {
                result.put(position, unavailable(caPoints.size()));
            } else {
                result.put(position, new StructuralVariabilityEvidence(
                        EvaluationStatus.PRESENT, caPoints.size(),
                        OptionalDouble.of(rmsf(caPoints)),
                        sidePoints.size() < 2 ? OptionalDouble.empty()
                                : OptionalDouble.of(rmsf(sidePoints)),
                        METHOD, VERSION, "EVALUATED"));
            }
        });
        return Map.copyOf(result);
    }

    private static void add(Map<Integer, ExperimentalResidueCoordinate> residues,
            Map<Integer, List<Point3D>> ca, Map<Integer, List<Point3D>> side,
            RigidTransform fit) {
        residues.forEach((position, coordinate) -> {
            ca.computeIfAbsent(position, ignored -> new ArrayList<>()).add(
                    transform(coordinate.ca(), fit));
            coordinate.sideChainCentroid().ifPresent(point -> side
                    .computeIfAbsent(position, ignored -> new ArrayList<>())
                    .add(transform(point, fit)));
        });
    }

    private static Point3D transform(Point3D point, RigidTransform fit) {
        if (fit == null) return point;
        return fit.apply(point);
    }

    private static double rmsf(List<Point3D> points) {
        double x = points.stream().mapToDouble(Point3D::x).average().orElseThrow();
        double y = points.stream().mapToDouble(Point3D::y).average().orElseThrow();
        double z = points.stream().mapToDouble(Point3D::z).average().orElseThrow();
        return Math.sqrt(points.stream().mapToDouble(point ->
                square(point.x() - x) + square(point.y() - y)
                        + square(point.z() - z)).average().orElseThrow());
    }

    private static double square(double value) { return value * value; }

    private static StructuralVariabilityEvidence unavailable(int count) {
        return new StructuralVariabilityEvidence(EvaluationStatus.EMPTY, count,
                OptionalDouble.empty(), OptionalDouble.empty(), METHOD, VERSION,
                "INSUFFICIENT_ALIGNED_COORDINATE_OBSERVATIONS");
    }
}
