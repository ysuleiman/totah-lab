package totah.lab.prometheus.identity;

import java.util.List;
import java.util.Objects;

import totah.lab.gaia.geometry.Point3D;

/**
 * Hash identity of a geometry: SHA-256 over one line per atom in canonical order
 * ({@code "label x y z"} with coordinates at %.8f) plus the atom count.
 */
public record GeometryIdentity(
        String sha256,
        int atomCount) {

    public GeometryIdentity {
        Objects.requireNonNull(sha256, "sha256");
        if (sha256.isBlank()) {
            throw new IllegalArgumentException("sha256 must be non-blank");
        }
        if (atomCount < 1) {
            throw new IllegalArgumentException("atomCount must be >= 1, got " + atomCount);
        }
    }

    /**
     * Builds the geometry identity of {@code coordinatesInCanonicalOrder} against
     * {@code map}. The coordinate list must contain exactly one point per canonical
     * atom, ordered by ascending canonical index.
     */
    public static GeometryIdentity of(
            CanonicalAtomMap map,
            List<Point3D> coordinatesInCanonicalOrder) {

        Objects.requireNonNull(map, "map");
        Objects.requireNonNull(coordinatesInCanonicalOrder, "coordinatesInCanonicalOrder");
        if (coordinatesInCanonicalOrder.size() != map.size()) {
            throw new IllegalArgumentException(
                    "coordinate count " + coordinatesInCanonicalOrder.size()
                            + " does not match atom count " + map.size());
        }
        StringBuilder sb = new StringBuilder();
        List<CanonicalAtomId> atoms = map.atoms();
        for (int i = 0; i < atoms.size(); i++) {
            Point3D point = Objects.requireNonNull(
                    coordinatesInCanonicalOrder.get(i), "coordinates must not contain null");
            sb.append(atoms.get(i).label())
                    .append(' ')
                    .append(CanonicalHashing.format(point.x()))
                    .append(' ')
                    .append(CanonicalHashing.format(point.y()))
                    .append(' ')
                    .append(CanonicalHashing.format(point.z()));
            if (i + 1 < atoms.size()) {
                sb.append('\n');
            }
        }
        return new GeometryIdentity(CanonicalHashing.sha256Hex(sb.toString()), map.size());
    }
}
