package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable, toolkit-neutral molecular graph with durable lineage identifiers. */
public record MolecularGraph(List<Atom> atoms, List<Bond> bonds,
                             Map<String, String> properties) {
    public MolecularGraph {
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        bonds = List.copyOf(Objects.requireNonNull(bonds, "bonds"));
        properties = Map.copyOf(Objects.requireNonNull(properties, "properties"));
    }

    public Optional<Atom> atom(String id) {
        return atoms.stream().filter(atom -> atom.id().equals(id)).findFirst();
    }

    public Optional<Bond> bond(String id) {
        return bonds.stream().filter(bond -> bond.id().equals(id)).findFirst();
    }

    public record Atom(String id, String element, Integer isotope, int formalCharge,
                       int explicitHydrogens, boolean aromatic, String stereochemistry,
                       Coordinates coordinates, Map<String, String> properties) {
        public Atom {
            require(id, "atom id");
            require(element, "element");
            stereochemistry = stereochemistry == null ? "UNSPECIFIED" : stereochemistry;
            properties = Map.copyOf(properties == null ? Map.of() : properties);
        }
    }

    public record Bond(String id, String firstAtomId, String secondAtomId,
                       BondOrder order, boolean aromatic, String stereochemistry,
                       Map<String, String> properties) {
        public Bond {
            require(id, "bond id");
            require(firstAtomId, "first atom id");
            require(secondAtomId, "second atom id");
            Objects.requireNonNull(order, "order");
            stereochemistry = stereochemistry == null ? "UNSPECIFIED" : stereochemistry;
            properties = Map.copyOf(properties == null ? Map.of() : properties);
        }
    }

    public enum BondOrder { SINGLE, DOUBLE, TRIPLE, AROMATIC }

    public record Coordinates(double x, double y, double z) { }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
