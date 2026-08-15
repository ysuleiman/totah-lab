package totah.lab.prometheus.ingest.authoritative;

import java.util.List;
import java.util.Objects;

/** Atom-ordered Cartesian geometry. Coordinates are in the declared unit. */
public record CartesianGeometry(List<Atom> atoms, String unit) {
    public CartesianGeometry {
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        Objects.requireNonNull(unit, "unit");
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("geometry must contain atoms");
        }
    }

    public record Atom(String element, double x, double y, double z) {
        public Atom {
            Objects.requireNonNull(element, "element");
        }
    }
}
