package totah.lab.ligand;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LigandPreparerTest {

    @Test
    void resolvesComponentThroughInjectedBioJavaProvider() {
        ChemComp component = bondedComponent();
        AtomicReference<String> requestedId = new AtomicReference<>();
        LigandPreparer preparer = new LigandPreparer(componentId -> {
            requestedId.set(componentId);
            return component;
        });

        LigandPreparationResult result = preparer.prepare(residue());

        assertEquals("LIG", requestedId.get());
        assertEquals(2, result.graph().atoms().size());
        assertTrue(result.pdbqt().startsWith("ROOT"));
    }

    @Test
    void retainsExplicitChemCompOverload() {
        LigandPreparationResult result =
                new LigandPreparer(componentId -> null)
                        .prepare(residue(), bondedComponent());

        assertEquals(2, result.graph().atoms().size());
    }

    @Test
    void rejectsMissingAtomAndBondDefinitionsWithStructuredReason() {
        UnsupportedLigandException missing = assertThrows(
                UnsupportedLigandException.class,
                () -> new LigandPreparer(componentId -> null).prepare(residue()));
        assertEquals("LIG", missing.getComponentId());
        assertEquals(LigandUnsupportedReason.INCOMPLETE_CCD, missing.getReason());

        ChemComp noAtoms = new ChemComp();
        noAtoms.setId("LIG");
        UnsupportedLigandException atoms = assertThrows(
                UnsupportedLigandException.class,
                () -> new LigandPreparer(componentId -> noAtoms).prepare(residue()));
        assertEquals(LigandUnsupportedReason.INCOMPLETE_CCD, atoms.getReason());
        assertTrue(atoms.getMessage().contains("no atom definitions"));

        ChemComp noBonds = new ChemComp();
        noBonds.setId("LIG");
        noBonds.setAtoms(List.of(ccdAtom("C1")));
        UnsupportedLigandException bonds = assertThrows(
                UnsupportedLigandException.class,
                () -> new LigandPreparer(componentId -> noBonds).prepare(residue()));
        assertEquals(LigandUnsupportedReason.INCOMPLETE_CCD, bonds.getReason());
        assertTrue(bonds.getMessage().contains("no bond definitions"));
    }

    private Residue residue() {
        return Residue.builder()
                .name("LIG")
                .chain("A")
                .number(1)
                .insertionCode(' ')
                .atoms(List.of(atom("C1", 0.0), atom("C2", 1.5)))
                .build();
    }

    private Atom atom(String name, double x) {
        return Atom.builder()
                .name(name)
                .element(Element.builder().symbol("C").build())
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .build();
    }

    private ChemComp bondedComponent() {
        ChemComp component = new ChemComp();
        component.setId("LIG");
        component.setAtoms(List.of(ccdAtom("C1"), ccdAtom("C2")));
        ChemCompBond bond = new ChemCompBond();
        bond.setAtomId1("C1");
        bond.setAtomId2("C2");
        bond.setValueOrder("SING");
        bond.setPdbxAromaticFlag("N");
        component.setBonds(List.of(bond));
        return component;
    }

    private ChemCompAtom ccdAtom(String id) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(id);
        atom.setTypeSymbol("C");
        atom.setCharge(0);
        atom.setPdbxAromaticFlag("N");
        atom.setPdbxLeavingAtomFlag("N");
        return atom;
    }
}
