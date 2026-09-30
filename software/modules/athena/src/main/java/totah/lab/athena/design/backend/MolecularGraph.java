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

    /** Structural integrity, independent of chemical sanitization or canonical identity. */
    public void validateTopology(boolean requireConnected) {
        var adjacent = new java.util.HashMap<String, java.util.Set<String>>();
        for (var atom : atoms) {
            if (adjacent.put(atom.id(), new java.util.HashSet<>()) != null)
                throw new IllegalArgumentException("duplicate atom ID: " + atom.id());
        }
        var bondIds = new java.util.HashSet<String>();
        for (var bond : bonds) {
            if (!bondIds.add(bond.id()) || !adjacent.containsKey(bond.firstAtomId())
                    || !adjacent.containsKey(bond.secondAtomId()) || bond.firstAtomId().equals(bond.secondAtomId())
                    || !adjacent.get(bond.firstAtomId()).add(bond.secondAtomId()))
                throw new IllegalArgumentException("invalid bond topology: " + bond.id());
            adjacent.get(bond.secondAtomId()).add(bond.firstAtomId());
        }
        if (requireConnected) {
            if (atoms.isEmpty()) throw new IllegalArgumentException("empty graph");
            var seen = new java.util.HashSet<String>();
            var pending = new java.util.ArrayDeque<String>(); pending.add(atoms.getFirst().id());
            while (!pending.isEmpty()) { var id = pending.removeFirst(); if (seen.add(id)) pending.addAll(adjacent.get(id)); }
            if (seen.size() != atoms.size()) throw new IllegalArgumentException("disconnected graph");
        }
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

    /** Full state snapshots are authoritative; coordinate changes are reported separately. */
    public record Delta(MolecularGraph before, MolecularGraph after,
                        List<AtomChange> atoms, List<BondChange> bonds,
                        List<CoordinateChange> coordinates) {
        public Delta {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            atoms = List.copyOf(atoms); bonds = List.copyOf(bonds); coordinates = List.copyOf(coordinates);
            if (!atoms.equals(atomChanges(before, after)) || !bonds.equals(bondChanges(before, after))
                    || !coordinates.equals(coordinateChanges(before, after)))
                throw new IllegalArgumentException("delta does not describe its snapshots");
        }
        public static Delta between(MolecularGraph before, MolecularGraph after) {
            return new Delta(before, after, atomChanges(before, after), bondChanges(before, after),
                    coordinateChanges(before, after));
        }
        public MolecularGraph replay(MolecularGraph parent) {
            if (!before.equals(parent)) throw new IllegalArgumentException("replay parent state mismatch");
            // Snapshots also preserve ordering and graph/atom/bond metadata, including source charges.
            return after;
        }
        public boolean chemicalGraphChanged() { return !atoms.isEmpty() || !bonds.isEmpty(); }
        private static List<AtomChange> atomChanges(MolecularGraph a, MolecularGraph b) {
            return ids(a.atoms.stream().map(Atom::id).toList(), b.atoms.stream().map(Atom::id).toList())
                    .stream().map(id -> new AtomChange(id, chemical(a.atom(id).orElse(null)),
                            chemical(b.atom(id).orElse(null))))
                    .filter(c -> !Objects.equals(c.before(), c.after())).toList();
        }
        private static List<BondChange> bondChanges(MolecularGraph a, MolecularGraph b) {
            return ids(a.bonds.stream().map(Bond::id).toList(), b.bonds.stream().map(Bond::id).toList())
                    .stream().map(id -> new BondChange(id, a.bond(id).orElse(null), b.bond(id).orElse(null)))
                    .filter(c -> !Objects.equals(c.before(), c.after())).toList();
        }
        private static List<CoordinateChange> coordinateChanges(MolecularGraph a, MolecularGraph b) {
            return ids(a.atoms.stream().map(Atom::id).toList(), b.atoms.stream().map(Atom::id).toList())
                    .stream().map(id -> new CoordinateChange(id, a.atom(id).map(Atom::coordinates).orElse(null),
                            b.atom(id).map(Atom::coordinates).orElse(null)))
                    .filter(c -> !Objects.equals(c.before(), c.after())).toList();
        }
        private static java.util.Set<String> ids(List<String> a, List<String> b) {
            var ids = new java.util.TreeSet<String>(a); ids.addAll(b); return ids;
        }
        private static Atom chemical(Atom a) {
            return a == null ? null : new Atom(a.id, a.element, a.isotope, a.formalCharge,
                    a.explicitHydrogens, a.aromatic, a.stereochemistry, null, a.properties);
        }
    }
    /** Null before/after means addition/deletion respectively. */
    public record AtomChange(String atomId, Atom before, Atom after) { }
    public record BondChange(String bondId, Bond before, Bond after) { }
    public record CoordinateChange(String atomId, Coordinates before, Coordinates after) { }

    private static String require(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
