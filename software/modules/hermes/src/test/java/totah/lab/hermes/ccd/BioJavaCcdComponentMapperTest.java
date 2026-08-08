package totah.lab.hermes.ccd;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioJavaCcdComponentMapperTest {

    @Test
    void preservesCcdAtomOrderChemistryAndCoordinateKinds() {
        ChemComp chemComp = new ChemComp();
        chemComp.setId("LIG");
        chemComp.setAtoms(List.of(
                atom("N1", "N", 1, "Y", "N", 1.0, 2.0),
                atom("C1", "C", 0, "Y", "Y", 3.0, 4.0)));
        ChemCompBond bond = new ChemCompBond();
        bond.setAtomId1("N1");
        bond.setAtomId2("C1");
        bond.setValueOrder("AROM");
        bond.setPdbxAromaticFlag("Y");
        chemComp.setBonds(List.of(bond));

        CcdComponent result = new BioJavaCcdComponentMapper().map(chemComp);

        assertEquals(List.of("N1", "C1"),
                result.atoms().stream().map(CcdComponentAtom::atomId).toList());
        assertEquals(1, result.atoms().getFirst().formalCharge());
        assertTrue(result.atoms().getFirst().aromatic());
        assertTrue(result.atoms().get(1).leavingAtom());
        assertEquals(new Point3D(1.0, 1.5, 2.0),
                result.atoms().getFirst().modelPosition());
        assertEquals(new Point3D(2.0, 2.5, 3.0),
                result.atoms().getFirst().idealPosition());
        assertEquals(BondOrder.AROMATIC, result.bonds().getFirst().order());
        assertTrue(result.bonds().getFirst().aromatic());
    }

    private ChemCompAtom atom(
            String id, String element, int charge, String aromatic, String leaving,
            double modelX, double idealX) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(id);
        atom.setTypeSymbol(element);
        atom.setCharge(charge);
        atom.setPdbxAromaticFlag(aromatic);
        atom.setPdbxLeavingAtomFlag(leaving);
        atom.setModelCartnX(modelX);
        atom.setModelCartnY(modelX + 0.5);
        atom.setModelCartnZ(modelX + 1.0);
        atom.setPdbxModelCartnXIdeal(idealX);
        atom.setPdbxModelCartnYIdeal(idealX + 0.5);
        atom.setPdbxModelCartnZIdeal(idealX + 1.0);
        return atom;
    }
}
