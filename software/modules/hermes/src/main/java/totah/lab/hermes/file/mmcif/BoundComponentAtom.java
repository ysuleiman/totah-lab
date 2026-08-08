package totah.lab.hermes.file.mmcif;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/** One experimentally observed non-polymer atom, in source-file order. */
public record BoundComponentAtom(
        String name,
        String authName,
        String element,
        Point3D position,
        Double occupancy,
        Double bFactor,
        Integer formalCharge,
        String alternateLocation,
        String sourceAtomId
) {
    public BoundComponentAtom {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(position, "position");
    }
}
