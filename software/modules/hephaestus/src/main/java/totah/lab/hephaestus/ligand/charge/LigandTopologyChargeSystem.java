package totah.lab.hephaestus.ligand.charge;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.ligand.charge.ChargeSystem;

import java.util.ArrayList;
import java.util.List;

public final class LigandTopologyChargeSystem implements ChargeSystem {
    private final List<Atom> atoms;
    private final LigandTopology topology;
    private final List<List<Integer>> neighbors;

    public LigandTopologyChargeSystem(List<Atom> atoms, LigandTopology topology) {
        this.atoms = List.copyOf(atoms);
        this.topology = topology;
        if (atoms.size() != topology.atomCount()) {
            throw new IllegalArgumentException("Atom and topology counts differ.");
        }
        List<List<Integer>> mutable = new ArrayList<>();
        for (int index = 0; index < atoms.size(); index++) {
            mutable.add(new ArrayList<>());
        }
        for (ChemicalBond bond : topology.bonds()) {
            mutable.get(bond.atomIndexA()).add(bond.atomIndexB());
            mutable.get(bond.atomIndexB()).add(bond.atomIndexA());
        }
        this.neighbors = mutable.stream().map(List::copyOf).toList();
    }

    @Override public int size() { return atoms.size(); }
    @Override public double getX(int i) { return atoms.get(i).getPosition().x(); }
    @Override public double getY(int i) { return atoms.get(i).getPosition().y(); }
    @Override public double getZ(int i) { return atoms.get(i).getPosition().z(); }
    @Override public String getElement(int i) { return atoms.get(i).getElement().symbol(); }
    @Override public List<Integer> getNeighbors(int i) { return neighbors.get(i); }
    @Override public int getFormalCharge(int i) {
        return topology.atomProperties().get(i).formalCharge();
    }
    @Override public boolean isAromatic(int i) {
        return topology.atomProperties().get(i).aromatic();
    }
    @Override public double getBondOrder(int first, int second) {
        return topology.bonds().stream()
                .filter(bond -> bond.atomIndexA() == first && bond.atomIndexB() == second
                        || bond.atomIndexA() == second && bond.atomIndexB() == first)
                .findFirst().map(ChemicalBond::order).map(this::numericOrder).orElse(0.0);
    }

    private double numericOrder(BondOrder order) {
        return switch (order) {
            case SINGLE -> 1.0;
            case DOUBLE -> 2.0;
            case TRIPLE -> 3.0;
            case AROMATIC -> 1.5;
        };
    }
}
