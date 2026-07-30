package totah.lab.ligand.torsion;

import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.ElementResolver;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class RotatableBondClassifier {

    private static final Set<String> METALS = Set.of("Mg", "Ca", "Mn", "Fe", "Zn");

    private final LigandRingDetector ringDetector;

    public RotatableBondClassifier() {
        this(new LigandRingDetector());
    }

    RotatableBondClassifier(LigandRingDetector ringDetector) {
        this.ringDetector = Objects.requireNonNull(ringDetector, "ringDetector is null");
    }

    public LigandRotatableBondReport classify(MolecularGraph graph) {
        return classify(graph, Set.of());
    }

    public LigandRotatableBondReport classify(
            MolecularGraph graph,
            Set<Integer> explicitlyRigidBondIndices) {
        Objects.requireNonNull(graph, "graph is null");
        Objects.requireNonNull(explicitlyRigidBondIndices,
                "explicitlyRigidBondIndices is null");
        validateRigidIndices(graph, explicitlyRigidBondIndices);
        Set<Integer> ringBonds = ringDetector.detectRingBondIndices(graph);
        int[] heavyDegrees = heavyDegrees(graph);
        List<RotatableBondClassification> classifications =
                new ArrayList<>(graph.bonds().size());
        for (int index = 0; index < graph.bonds().size(); index++) {
            classifications.add(classifyBond(
                    graph, index, ringBonds, heavyDegrees, explicitlyRigidBondIndices));
        }
        return new LigandRotatableBondReport(classifications);
    }

    private RotatableBondClassification classifyBond(
            MolecularGraph graph,
            int bondIndex,
            Set<Integer> ringBonds,
            int[] heavyDegrees,
            Set<Integer> explicitlyRigidBondIndices) {
        ChemicalBond bond = graph.bonds().get(bondIndex);
        if (explicitlyRigidBondIndices.contains(bondIndex)) {
            return RotatableBondClassification.EXPLICITLY_RIGID;
        }
        if (bond.order() != BondOrder.SINGLE || bond.aromatic()) {
            return RotatableBondClassification.NON_SINGLE;
        }
        String firstElement = element(graph, bond.atomIndexA());
        String secondElement = element(graph, bond.atomIndexB());
        if ("H".equals(firstElement) || "H".equals(secondElement)) {
            return RotatableBondClassification.HYDROGEN;
        }
        if (METALS.contains(firstElement) || METALS.contains(secondElement)) {
            return RotatableBondClassification.METAL_COORDINATION;
        }
        if (ringBonds.contains(bondIndex)) {
            return RotatableBondClassification.RING;
        }
        if (isAmideLike(graph, bond)) {
            return RotatableBondClassification.RESONANCE_RESTRICTED;
        }
        if (heavyDegrees[bond.atomIndexA()] <= 1
                || heavyDegrees[bond.atomIndexB()] <= 1) {
            return RotatableBondClassification.TERMINAL;
        }
        return RotatableBondClassification.ROTATABLE;
    }

    private boolean isAmideLike(MolecularGraph graph, ChemicalBond bond) {
        int carbon;
        if ("C".equals(element(graph, bond.atomIndexA()))
                && "N".equals(element(graph, bond.atomIndexB()))) {
            carbon = bond.atomIndexA();
        } else if ("N".equals(element(graph, bond.atomIndexA()))
                && "C".equals(element(graph, bond.atomIndexB()))) {
            carbon = bond.atomIndexB();
        } else {
            return false;
        }
        for (ChemicalBond candidate : graph.bonds()) {
            if (candidate.order() != BondOrder.DOUBLE) {
                continue;
            }
            int neighbor = otherEndpoint(candidate, carbon);
            if (neighbor >= 0) {
                String element = element(graph, neighbor);
                if ("O".equals(element) || "S".equals(element)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int[] heavyDegrees(MolecularGraph graph) {
        int[] degrees = new int[graph.atoms().size()];
        for (ChemicalBond bond : graph.bonds()) {
            if (!"H".equals(element(graph, bond.atomIndexB()))) {
                degrees[bond.atomIndexA()]++;
            }
            if (!"H".equals(element(graph, bond.atomIndexA()))) {
                degrees[bond.atomIndexB()]++;
            }
        }
        return degrees;
    }

    private int otherEndpoint(ChemicalBond bond, int atomIndex) {
        if (bond.atomIndexA() == atomIndex) {
            return bond.atomIndexB();
        }
        if (bond.atomIndexB() == atomIndex) {
            return bond.atomIndexA();
        }
        return -1;
    }

    private String element(MolecularGraph graph, int atomIndex) {
        return ElementResolver.resolveSymbol(graph.atoms().get(atomIndex), false);
    }

    private void validateRigidIndices(
            MolecularGraph graph,
            Set<Integer> explicitlyRigidBondIndices) {
        Set<Integer> seen = new HashSet<>();
        for (Integer index : explicitlyRigidBondIndices) {
            if (index == null || index < 0 || index >= graph.bonds().size()
                    || !seen.add(index)) {
                throw new IllegalArgumentException(
                        "Invalid explicitly rigid ligand bond index: " + index);
            }
        }
    }
}
