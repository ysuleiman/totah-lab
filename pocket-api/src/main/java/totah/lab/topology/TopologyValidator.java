package totah.lab.topology;

import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;
import totah.lab.protein.Topology;

public class TopologyValidator {

    private TopologyValidator(){}

    public static void validate(Structure structure, Topology topology) {
        int atoms = 0;
        int missingCharges = 0;
        for (Residue residue : structure.getResidues()) {
            for (Atom atom : residue.getAtoms()) {
                atoms++;
                if (atom.getCharge() == 0) {
                    missingCharges++;
                }
            }
        }
        System.out.println("Atoms: " + atoms);
        System.out.println("Bonds: " + topology.getBondCount());
        System.out.println("Zero charges: " + missingCharges);
    }
}
