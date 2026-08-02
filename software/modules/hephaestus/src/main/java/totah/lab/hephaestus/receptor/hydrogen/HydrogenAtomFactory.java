package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;

import java.util.Objects;

public final class HydrogenAtomFactory {

    public Atom createHydrogen(
            String name,
            Point3D position,
            double bFactor) {

        return create(
                name,
                position,
                Element.H,
                bFactor);
    }

    public Atom createTerminalOxygen(
            String name,
            Point3D position,
            double bFactor) {

        return create(
                name,
                position,
                Element.O,
                bFactor);
    }

    private Atom create(
            String name,
            Point3D position,
            Element element,
            double bFactor) {

        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(element, "element");

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "name must not be blank.");
        }

        return Atom.builder()
                .name(name.trim())
                .position(position)
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(bFactor)
                .element(element)
                .build();
    }
}
