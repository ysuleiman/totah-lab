package totah.lab.ligand.typing;

import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.Atom;
import totah.lab.protein.ElementResolver;
import totah.lab.topology.AutoDockType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Assigns AutoDock4 atom types from ligand graph chemistry.
 */
public final class LigandAd4AtomTyper {

    private static final Set<String> METAL_TYPES =
            Set.of("Mg", "Ca", "Mn", "Fe", "Zn");

    public LigandAd4TypingResult assign(MolecularGraph graph) {
        Objects.requireNonNull(graph, "graph is null");
        if (graph.atoms().isEmpty()) {
            throw new IllegalArgumentException("Cannot type an empty ligand graph");
        }

        List<List<BondedAtom>> adjacency = adjacency(graph);
        List<Atom> typedAtoms = new ArrayList<>(graph.atoms().size());
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (int index = 0; index < graph.atoms().size(); index++) {
            Atom atom = graph.atoms().get(index);
            if (!Double.isFinite(atom.getCharge())) {
                throw new IllegalStateException(
                        "Non-finite ligand charge at atom index " + index);
            }
            String type = assignType(graph, adjacency, index);
            assertLegalType(type);
            typedAtoms.add(atom.toBuilder().autoDockType(type).build());
            typeCounts.merge(type, 1, Integer::sum);
        }

        return new LigandAd4TypingResult(
                new MolecularGraph(typedAtoms, graph.bonds(), graph.atomProperties()),
                typeCounts);
    }

    private String assignType(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int atomIndex) {
        String element = element(graph, atomIndex);
        return switch (element) {
            case "H" -> hydrogenType(graph, adjacency, atomIndex);
            case "C" -> isAromatic(graph, adjacency, atomIndex) ? "A" : "C";
            case "N" -> nitrogenType(graph, adjacency, atomIndex);
            case "O" -> formalCharge(graph, atomIndex) > 0 ? "O" : "OA";
            case "S" -> sulfurType(graph, adjacency, atomIndex);
            case "P" -> "P";
            case "F", "Cl", "Br", "I" -> element;
            default -> {
                if (METAL_TYPES.contains(element)) {
                    yield element;
                }
                throw new IllegalArgumentException(
                        "Unsupported AutoDock4 ligand element '" + element
                                + "' at atom index " + atomIndex);
            }
        };
    }

    private String hydrogenType(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int atomIndex) {
        List<BondedAtom> neighbors = adjacency.get(atomIndex);
        if (neighbors.size() != 1) {
            throw new IllegalStateException(
                    "Ligand hydrogen at atom index " + atomIndex
                            + " must have exactly one bonded parent");
        }
        String parentElement = element(graph, neighbors.getFirst().atomIndex());
        return switch (parentElement) {
            case "N", "O", "S" -> "HD";
            default -> "H";
        };
    }

    private String nitrogenType(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int atomIndex) {
        if (formalCharge(graph, atomIndex) > 0
                || isAmideNitrogen(graph, adjacency, atomIndex)) {
            return "N";
        }
        if (isAromatic(graph, adjacency, atomIndex)
                && hasBondedElement(graph, adjacency, atomIndex, "H")) {
            return "N";
        }
        double valence = adjacency.get(atomIndex).stream()
                .mapToDouble(bonded -> numericalOrder(bonded.bond().order()))
                .sum();
        return valence >= 4.0 ? "N" : "NA";
    }

    private boolean isAmideNitrogen(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int nitrogenIndex) {
        for (BondedAtom bondedCarbon : adjacency.get(nitrogenIndex)) {
            if (bondedCarbon.bond().order() != BondOrder.SINGLE
                    || !"C".equals(element(graph, bondedCarbon.atomIndex()))) {
                continue;
            }
            for (BondedAtom carbonNeighbor : adjacency.get(bondedCarbon.atomIndex())) {
                if (carbonNeighbor.atomIndex() == nitrogenIndex) {
                    continue;
                }
                String neighborElement = element(graph, carbonNeighbor.atomIndex());
                if (carbonNeighbor.bond().order() == BondOrder.DOUBLE
                        && ("O".equals(neighborElement) || "S".equals(neighborElement))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String sulfurType(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int atomIndex) {
        if (formalCharge(graph, atomIndex) > 0
                || hasBondedElement(graph, adjacency, atomIndex, "S")) {
            return "S";
        }
        double valence = adjacency.get(atomIndex).stream()
                .mapToDouble(bonded -> numericalOrder(bonded.bond().order()))
                .sum();
        return valence > 2.0 ? "S" : "SA";
    }

    private boolean hasBondedElement(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int atomIndex,
            String element) {
        return adjacency.get(atomIndex).stream()
                .anyMatch(neighbor -> element.equals(element(graph, neighbor.atomIndex())));
    }

    private boolean isAromatic(
            MolecularGraph graph,
            List<List<BondedAtom>> adjacency,
            int atomIndex) {
        return graph.atomProperties().get(atomIndex).aromatic()
                || adjacency.get(atomIndex).stream().anyMatch(bonded -> bonded.bond().aromatic()
                || bonded.bond().order() == BondOrder.AROMATIC);
    }

    private int formalCharge(MolecularGraph graph, int atomIndex) {
        return graph.atomProperties().get(atomIndex).formalCharge();
    }

    private String element(MolecularGraph graph, int atomIndex) {
        return ElementResolver.resolveSymbol(graph.atoms().get(atomIndex), false);
    }

    private List<List<BondedAtom>> adjacency(MolecularGraph graph) {
        List<List<BondedAtom>> mutable = new ArrayList<>(graph.atoms().size());
        for (int index = 0; index < graph.atoms().size(); index++) {
            mutable.add(new ArrayList<>());
        }
        for (ChemicalBond bond : graph.bonds()) {
            mutable.get(bond.atomIndexA()).add(new BondedAtom(bond.atomIndexB(), bond));
            mutable.get(bond.atomIndexB()).add(new BondedAtom(bond.atomIndexA(), bond));
        }
        return mutable.stream().map(List::copyOf).toList();
    }

    private double numericalOrder(BondOrder order) {
        return switch (order) {
            case SINGLE -> 1.0;
            case DOUBLE -> 2.0;
            case TRIPLE -> 3.0;
            case AROMATIC -> 1.5;
        };
    }

    private void assertLegalType(String type) {
        for (AutoDockType value : AutoDockType.values()) {
            if (value.getSymbol().equals(type)) {
                return;
            }
        }
        throw new IllegalArgumentException("Illegal AutoDock4 atom type: " + type);
    }

    private record BondedAtom(int atomIndex, ChemicalBond bond) {
    }
}
