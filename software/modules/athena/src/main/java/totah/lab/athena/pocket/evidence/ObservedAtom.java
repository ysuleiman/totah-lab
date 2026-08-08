package totah.lab.athena.pocket.evidence;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/** Source-observed atom and experimental coordinates. */
public record ObservedAtom(
        EvidenceAtomId id,
        String element,
        Point3D position,
        Double occupancy,
        Double bFactor,
        Integer formalCharge
) {
    public ObservedAtom {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(position, "position");
        if (element.isBlank()) {
            throw new IllegalArgumentException("element must not be blank");
        }
        element = element.trim();
        if (occupancy != null && (!Double.isFinite(occupancy)
                || occupancy < 0.0 || occupancy > 1.0)) {
            throw new IllegalArgumentException("occupancy must be between 0 and 1");
        }
        if (bFactor != null && !Double.isFinite(bFactor)) {
            throw new IllegalArgumentException("bFactor must be finite");
        }
    }
}
