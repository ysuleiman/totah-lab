package totah.lab.chemistry;

import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;

import java.util.Objects;

public final class ChemicalAtomFactory {

    private ChemicalAtomFactory() {
    }

    public static Atom hydrogen(String name, Point3D position, double bFactor) {
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(position, "position is null");
        return Atom.builder()
                .name(name)
                .position(position)
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(bFactor)
                .element(Element.H)
                .build();
    }
}
