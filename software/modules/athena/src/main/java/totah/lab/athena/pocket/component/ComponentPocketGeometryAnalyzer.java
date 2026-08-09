package totah.lab.athena.pocket.component;

import totah.lab.gaia.geometry.Point3D;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic geometry only; it assigns no biological significance. */
public final class ComponentPocketGeometryAnalyzer {
    private final ComponentPocketGeometryThresholds thresholds;

    public ComponentPocketGeometryAnalyzer(
            ComponentPocketGeometryThresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds);
    }

    public ComponentPocketGeometry analyze(List<GeometryAtom> componentAtoms,
            List<GeometryAtom> pocketAtoms, List<PocketSphere> spheres) {
        List<GeometryAtom> heavy = componentAtoms.stream()
                .filter(GeometryAtom::heavy).toList();
        if (heavy.isEmpty()) {
            throw new IllegalArgumentException("component requires a heavy atom");
        }
        if (pocketAtoms.isEmpty()) {
            throw new IllegalArgumentException("pocket requires atoms");
        }
        double minimumAtom = Double.POSITIVE_INFINITY;
        Set<String> contactingResidues = new HashSet<>();
        for (GeometryAtom component : heavy) {
            for (GeometryAtom pocket : pocketAtoms) {
                double distance = distance(component.position(), pocket.position());
                minimumAtom = Math.min(minimumAtom, distance);
                if (distance <= thresholds.contactDistanceAngstrom()
                        && pocket.residueIdentity() != null) {
                    contactingResidues.add(pocket.residueIdentity());
                }
            }
        }

        double minimumSphereCenter = Double.POSITIVE_INFINITY;
        double minimumSphereSurface = Double.POSITIVE_INFINITY;
        int inside = 0;
        int near = 0;
        for (GeometryAtom atom : heavy) {
            boolean atomInside = false;
            boolean atomNear = false;
            for (PocketSphere sphere : spheres) {
                double centerDistance = distance(atom.position(), sphere.center());
                minimumSphereCenter = Math.min(minimumSphereCenter, centerDistance);
                double surfaceDistance = Math.max(0.0,
                        centerDistance - sphere.radius());
                minimumSphereSurface = Math.min(minimumSphereSurface,
                        surfaceDistance);
                atomInside |= centerDistance <= sphere.radius();
                atomNear |= centerDistance <= sphere.radius()
                        + thresholds.sphereNearShellAngstrom();
            }
            if (atomInside) inside++;
            if (atomNear) near++;
        }
        double insideFraction = (double) inside / heavy.size();
        double nearFraction = (double) near / heavy.size();
        ComponentPocketRelationshipClass relationship;
        if (insideFraction >= thresholds.occupiedHeavyAtomFraction()) {
            relationship = ComponentPocketRelationshipClass.OCCUPIES_POCKET;
        } else if (minimumAtom <= thresholds.contactDistanceAngstrom()) {
            relationship = ComponentPocketRelationshipClass.CONTACTS_POCKET;
        } else if (minimumAtom <= thresholds.nearDistanceAngstrom()
                || minimumSphereSurface <= thresholds.sphereNearShellAngstrom()) {
            relationship = ComponentPocketRelationshipClass.NEAR_POCKET;
        } else {
            relationship = ComponentPocketRelationshipClass.NOT_ASSOCIATED;
        }
        Point3D componentCentroid = centroid(heavy.stream()
                .map(GeometryAtom::position).toList());
        Point3D pocketCentroid = spheres.isEmpty()
                ? centroid(pocketAtoms.stream().map(GeometryAtom::position).toList())
                : centroid(spheres.stream().map(PocketSphere::center).toList());
        return new ComponentPocketGeometry(relationship, minimumAtom,
                finiteOrNaN(minimumSphereCenter), finiteOrNaN(minimumSphereSurface),
                distance(componentCentroid, pocketCentroid), inside, near,
                insideFraction, nearFraction, contactingResidues.size());
    }

    private static Point3D centroid(List<Point3D> points) {
        double x = 0, y = 0, z = 0;
        for (Point3D point : points) {
            x += point.x(); y += point.y(); z += point.z();
        }
        return new Point3D(x / points.size(), y / points.size(), z / points.size());
    }

    private static double distance(Point3D first, Point3D second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double finiteOrNaN(double value) {
        return Double.isFinite(value) ? value : Double.NaN;
    }
}
