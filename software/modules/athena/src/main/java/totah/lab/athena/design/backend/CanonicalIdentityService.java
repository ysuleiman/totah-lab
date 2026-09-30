package totah.lab.athena.design.backend;

import java.util.*;

public interface CanonicalIdentityService {
    Result identify(MolecularGraph graph) throws MolecularBackendException;
    record Result(String canonicalKey, BackendEvidence evidence) { }

    /**
     * Conservative attributed-graph lineage proof, not another canonical identity algorithm.
     * Coordinates and source metadata do not determine chemical equivalence. A backend may
     * override this for explicitly supported normalization equivalences. Unknown equivalence
     * fails closed. Stable-ID ordering selects one mapping without asserting physical lineage
     * between symmetry-equivalent atoms. Alternatives and truncation remain explicit.
     */
    default Correspondence correspondence(MolecularGraph attempted, MolecularGraph representative)
            throws MolecularBackendException {
        return new MappingSearch(attempted, representative).run();
    }

    record Mapping(Map<String,String> atoms, Map<String,String> bonds) {
        public Mapping { atoms = Map.copyOf(atoms); bonds = Map.copyOf(bonds); }
        public Map<String,String> composeAtoms(Map<String,String> parentToAttempt) {
            return compose(parentToAttempt, atoms);
        }
        public Map<String,String> composeBonds(Map<String,String> parentToAttempt) {
            return compose(parentToAttempt, bonds);
        }
        private static Map<String,String> compose(Map<String,String> first, Map<String,String> second) {
            var result = new TreeMap<String,String>();
            first.forEach((source, intermediate) -> {
                if (!second.containsKey(intermediate)) throw new IllegalArgumentException("incomplete lineage mapping");
                result.put(source, second.get(intermediate));
            });
            return Map.copyOf(result);
        }
    }
    record Correspondence(List<Mapping> alternatives, boolean exhaustive, int visitedStates) {
        public Correspondence { alternatives = List.copyOf(alternatives); }
        public Mapping selected() {
            if (alternatives.isEmpty()) throw new IllegalStateException("no verified representative mapping");
            return alternatives.getFirst();
        }
        public boolean ambiguous() { return alternatives.size() > 1 || !exhaustive; }
    }

    /** Bounded exact graph matching owned by the identity contract. No identity keys are invented. */
    final class MappingSearch {
        private static final int MAX_STATES = 100_000, MAX_MAPPINGS = 256;
        private final MolecularGraph source, target;
        private final List<MolecularGraph.Atom> left, right;
        private final List<Mapping> results = new ArrayList<>();
        private int visited;
        private boolean truncated;
        MappingSearch(MolecularGraph source, MolecularGraph target) {
            this.source = source; this.target = target;
            left = source.atoms().stream().sorted(Comparator.comparing(MolecularGraph.Atom::id)).toList();
            right = target.atoms().stream().sorted(Comparator.comparing(MolecularGraph.Atom::id)).toList();
        }
        Correspondence run() throws MolecularBackendException {
            validate(source); validate(target);
            if (left.size() == right.size() && source.bonds().size() == target.bonds().size())
                search(0, new LinkedHashMap<>(), new HashSet<>());
            return new Correspondence(results, !truncated, visited);
        }
        private void search(int index, Map<String,String> map, Set<String> used) {
            if (visited++ >= MAX_STATES || results.size() >= MAX_MAPPINGS) { truncated = true; return; }
            if (index == left.size()) {
                var bonds = new TreeMap<String,String>();
                for (var b : source.bonds()) {
                    var match = between(target, map.get(b.firstAtomId()), map.get(b.secondAtomId()));
                    if (match == null || !sameBond(b, match)) return;
                    bonds.put(b.id(), match.id());
                }
                results.add(new Mapping(map, bonds)); return;
            }
            var atom = left.get(index);
            for (var candidate : right) {
                if (truncated) return;
                if (used.contains(candidate.id()) || !sameAtom(atom, candidate)
                        || degree(source, atom.id()) != degree(target, candidate.id())) continue;
                boolean compatible = true;
                for (var entry : map.entrySet()) {
                    var a = between(source, atom.id(), entry.getKey());
                    var b = between(target, candidate.id(), entry.getValue());
                    if ((a == null) != (b == null) || (a != null && !sameBond(a, b))) { compatible = false; break; }
                }
                if (!compatible) continue;
                map.put(atom.id(), candidate.id()); used.add(candidate.id());
                search(index + 1, map, used);
                map.remove(atom.id()); used.remove(candidate.id());
            }
        }
        private static boolean sameAtom(MolecularGraph.Atom a, MolecularGraph.Atom b) {
            return a.element().equals(b.element()) && Objects.equals(a.isotope(), b.isotope())
                    && a.formalCharge() == b.formalCharge() && a.explicitHydrogens() == b.explicitHydrogens()
                    && a.aromatic() == b.aromatic() && a.stereochemistry().equals(b.stereochemistry());
        }
        private static boolean sameBond(MolecularGraph.Bond a, MolecularGraph.Bond b) {
            return a.order() == b.order() && a.aromatic() == b.aromatic()
                    && a.stereochemistry().equals(b.stereochemistry());
        }
        private static long degree(MolecularGraph g, String id) {
            return g.bonds().stream().filter(b -> b.firstAtomId().equals(id) || b.secondAtomId().equals(id)).count();
        }
        private static MolecularGraph.Bond between(MolecularGraph g, String a, String b) {
            return g.bonds().stream().filter(x -> (x.firstAtomId().equals(a) && x.secondAtomId().equals(b))
                    || (x.firstAtomId().equals(b) && x.secondAtomId().equals(a))).findFirst().orElse(null);
        }
        private static void validate(MolecularGraph g) throws MolecularBackendException {
            try { g.validateTopology(false); }
            catch (IllegalArgumentException error) { throw new MolecularBackendException(error.getMessage(), error); }
            if (g.atoms().stream().anyMatch(a -> a.stereochemistry().startsWith("PARITY_")))
                throw new MolecularBackendException("index-relative stereo requires backend correspondence proof");
        }
    }
}
