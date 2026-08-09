package totah.lab.athena.pocket.component;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

public record GeometryAtom(String element, Point3D position,
        String residueIdentity) {
    public GeometryAtom {
        Objects.requireNonNull(element);
        Objects.requireNonNull(position);
    }

    public boolean heavy() {
        return !element.equalsIgnoreCase("H")
                && !element.equalsIgnoreCase("D")
                && !element.equalsIgnoreCase("T");
    }
}
