package totah.lab.topology;


import lombok.Getter;
import java.util.*;
@Getter
public class ResidueTemplate {
    private final String name;
    private final List<AtomTemplate> atoms = new ArrayList<>();
    private final Map<String, AtomTemplate> atomMap = new HashMap<>();
    private final List<BondTemplate> bonds = new ArrayList<>();
    public ResidueTemplate(String name) {
        this.name = name;
    }
    public void addAtom(AtomTemplate atom) {
        atoms.add(atom);
        atomMap.put(atom.getName(), atom);
    }
    public AtomTemplate getAtom(String atomName) {
        return atomMap.get(atomName);
    }

    public void addBond(BondTemplate bond) {
        bonds.add(bond);
    }
    public String getAtomNameByIndex(int index) {
        if (index < 1 || index > atoms.size()) return null;
        return atoms.get(index - 1).getName();
    }
}
