package totah.lab.ligand.charge;

import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.math.charges.ChargeSystem;
import totah.lab.protein.Atom;
import totah.lab.protein.ElementResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MolecularGraphChargeSystem implements ChargeSystem {

    private final MolecularGraph graph;
    private final List<List<Integer>> neighbors;
    private final Map<Long, Double> bondOrders;

    public MolecularGraphChargeSystem(MolecularGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph is null");
        List<List<Integer>> mutableNeighbors = new ArrayList<>(graph.atoms().size());
        for (int index = 0; index < graph.atoms().size(); index++) {
            mutableNeighbors.add(new ArrayList<>());
        }
        Map<Long, Double> orders = new HashMap<>();
        for (ChemicalBond bond : graph.bonds()) {
            mutableNeighbors.get(bond.atomIndexA()).add(bond.atomIndexB());
            mutableNeighbors.get(bond.atomIndexB()).add(bond.atomIndexA());
            orders.put(key(bond.atomIndexA(), bond.atomIndexB()),
                    numericalOrder(bond.order()));
        }
        this.neighbors = mutableNeighbors.stream()
                .map(List::copyOf)
                .toList();
        this.bondOrders = Map.copyOf(orders);
    }

    @Override
    public int size() {
        return graph.atoms().size();
    }

    @Override
    public double getX(int i) {
        return atom(i).getPosition().x();
    }

    @Override
    public double getY(int i) {
        return atom(i).getPosition().y();
    }

    @Override
    public double getZ(int i) {
        return atom(i).getPosition().z();
    }

    @Override
    public String getElement(int i) {
        return ElementResolver.resolveSymbol(atom(i), false);
    }

    @Override
    public List<Integer> getNeighbors(int i) {
        return neighbors.get(i);
    }

    @Override
    public int getFormalCharge(int i) {
        return graph.atomProperties().get(i).formalCharge();
    }

    @Override
    public double getBondOrder(int atomIndexA, int atomIndexB) {
        return bondOrders.getOrDefault(key(atomIndexA, atomIndexB), 0.0);
    }

    @Override
    public boolean isAromatic(int i) {
        return graph.atomProperties().get(i).aromatic();
    }

    private Atom atom(int index) {
        return graph.atoms().get(index);
    }

    private long key(int first, int second) {
        int low = Math.min(first, second);
        int high = Math.max(first, second);
        return ((long) low << 32) | (high & 0xffffffffL);
    }

    private double numericalOrder(BondOrder order) {
        return switch (order) {
            case SINGLE -> 1.0;
            case DOUBLE -> 2.0;
            case TRIPLE -> 3.0;
            case AROMATIC -> 1.5;
        };
    }
}
