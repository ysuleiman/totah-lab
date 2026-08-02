package totah.lab.pocket.visualization.analysis;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Estimates solvent-facing pocket openings from directional heavy-atom
 * clearance. Results are derived annotations, not fpocket-provided features.
 */
public final class PocketOpeningDetector {
    private static final int DIRECTION_SAMPLES = 256;
    private static final double GOLDEN_ANGLE =
            Math.PI * (3.0 - Math.sqrt(5.0));
    private static final double MIN_SEPARATION_COS = 0.55;

    private PocketOpeningDetector() {
    }

    public static List<PocketOpening> detect(
            Pocket pocket,
            Structure structure,
            int maximumOpenings) {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(structure, "structure");
        if (maximumOpenings < 1) {
            throw new IllegalArgumentException(
                    "maximumOpenings must be positive");
        }
        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(alphaSphereSet -> alphaSphereSet.spheres())
                .orElse(List.of());
        if (spheres.isEmpty()) {
            return List.of();
        }
        Point3D origin = pocket.center();
        List<Point3D> heavyAtoms = structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();

        List<Candidate> candidates = new ArrayList<>(DIRECTION_SAMPLES);
        for (int index = 0; index < DIRECTION_SAMPLES; index++) {
            Point3D direction = fibonacciDirection(index);
            double extent = pocketExtent(origin, direction, spheres);
            double clearance = directionalClearance(
                    origin, direction, extent, heavyAtoms);
            candidates.add(new Candidate(direction, extent, clearance));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::clearance)
                .reversed());

        List<PocketOpening> openings = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (openings.stream().anyMatch(opening ->
                    dot(opening.direction(), candidate.direction())
                            > MIN_SEPARATION_COS)) {
                continue;
            }
            Point3D center = add(
                    origin, scale(candidate.direction(), candidate.extent()));
            double radius = Math.max(
                    0.8, Math.min(4.0, candidate.clearance()));
            openings.add(new PocketOpening(
                    openings.isEmpty()
                            ? PocketOpening.Kind.MOUTH
                            : PocketOpening.Kind.SECONDARY_OPENING,
                    center,
                    candidate.direction(),
                    radius,
                    candidate.clearance()));
            if (openings.size() == maximumOpenings) {
                break;
            }
        }
        return List.copyOf(openings);
    }

    private static Point3D fibonacciDirection(int index) {
        double y = 1.0 - 2.0 * (index + 0.5) / DIRECTION_SAMPLES;
        double radial = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double angle = index * GOLDEN_ANGLE;
        return new Point3D(
                Math.cos(angle) * radial,
                y,
                Math.sin(angle) * radial);
    }

    private static double pocketExtent(
            Point3D origin,
            Point3D direction,
            List<AlphaSphere> spheres) {
        double extent = 0.0;
        for (AlphaSphere sphere : spheres) {
            Point3D relative = subtract(sphere.center(), origin);
            extent = Math.max(
                    extent, dot(relative, direction) + sphere.radius());
        }
        return extent;
    }

    private static double directionalClearance(
            Point3D origin,
            Point3D direction,
            double extent,
            List<Point3D> atoms) {
        double clearance = Double.POSITIVE_INFINITY;
        double end = extent + 5.0;
        for (Point3D atom : atoms) {
            Point3D relative = subtract(atom, origin);
            double projection = dot(relative, direction);
            if (projection < extent * 0.35 || projection > end) {
                continue;
            }
            Point3D perpendicular = subtract(
                    relative, scale(direction, projection));
            clearance = Math.min(clearance, norm(perpendicular) - 1.7);
        }
        return Double.isFinite(clearance)
                ? Math.max(0.25, clearance)
                : 4.0;
    }

    private static double dot(Point3D left, Point3D right) {
        return left.x() * right.x()
                + left.y() * right.y()
                + left.z() * right.z();
    }

    private static double norm(Point3D point) {
        return Math.sqrt(dot(point, point));
    }

    private static Point3D add(Point3D left, Point3D right) {
        return new Point3D(
                left.x() + right.x(),
                left.y() + right.y(),
                left.z() + right.z());
    }

    private static Point3D subtract(Point3D left, Point3D right) {
        return new Point3D(
                left.x() - right.x(),
                left.y() - right.y(),
                left.z() - right.z());
    }

    private static Point3D scale(Point3D point, double factor) {
        return new Point3D(
                point.x() * factor,
                point.y() * factor,
                point.z() * factor);
    }

    private record Candidate(
            Point3D direction,
            double extent,
            double clearance) {
    }
}
