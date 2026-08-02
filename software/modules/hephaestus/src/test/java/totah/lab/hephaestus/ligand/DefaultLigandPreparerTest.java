package totah.lab.hephaestus.ligand;

import org.junit.jupiter.api.Test;
import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.preparation.OperationResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DefaultLigandPreparerTest {

    @Test
    void shouldCarryPreparedLigandAcrossOperations() {
        Ligand ligand = new Ligand(
                "test",
                "test ligand",
                null,
                null,
                null,
                null,
                new Structure(List.of()));

        LigandPreparationOperation operation =
                (prepared, options) ->
                        OperationResult.success(
                                prepared.withAttribute("typed", true));

        LigandPreparationResult result =
                new DefaultLigandPreparer(List.of(operation))
                        .prepare(new LigandPreparationRequest(ligand));

        assertEquals(
                true,
                result.preparedLigand().attributes().get("typed"));
        assertTrue(result.successful());
    }

    @Test
    void reportsMissingHeavyAtomsWithStableNativeReason() {
        ChemComp component = new ChemComp();
        component.setId("MIS");
        component.setAtoms(List.of(ccdAtom("C1"), ccdAtom("C2")));
        component.setBonds(List.of(bond("C1", "C2")));
        Residue residue = new Residue("MIS", 1, List.of(atom("C1")));
        Ligand ligand = new Ligand("mis", "Missing atom", "MIS", null, null, null,
                new Structure(List.of(new Chain("A", List.of(residue)))));

        UnsupportedLigandException exception = assertThrows(
                UnsupportedLigandException.class,
                () -> DefaultLigandPreparer.standard(id -> component)
                        .prepare(new LigandPreparationRequest(ligand)));

        assertEquals("MIS", exception.getComponentId());
        assertEquals(LigandUnsupportedReason.MISSING_HEAVY_ATOMS, exception.getReason());
    }

    @Test
    void defaultsDoNotClaimUnsupportedEnumerationFeatures() {
        LigandPreparationOptions options = LigandPreparationOptions.defaults();
        assertFalse(options.generateProtonationStates());
        assertFalse(options.generateTautomers());
        assertFalse(options.generateConformers());
    }

    private Atom atom(String name) {
        return Atom.builder().pdbSerial(1).name(name).element(Element.C)
                .position(new Point3D(0, 0, 0)).charge(0).occupancy(1).bFactor(0).build();
    }

    private ChemCompAtom ccdAtom(String name) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(name);
        atom.setTypeSymbol("C");
        atom.setCharge(0);
        atom.setPdbxAromaticFlag("N");
        atom.setPdbxLeavingAtomFlag("N");
        return atom;
    }

    private ChemCompBond bond(String first, String second) {
        ChemCompBond bond = new ChemCompBond();
        bond.setAtomId1(first);
        bond.setAtomId2(second);
        bond.setValueOrder("SING");
        bond.setPdbxAromaticFlag("N");
        return bond;
    }
}
