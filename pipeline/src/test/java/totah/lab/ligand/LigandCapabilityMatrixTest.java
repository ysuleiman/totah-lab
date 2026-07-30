package totah.lab.ligand;

import totah.lab.ligand.ccd.CcdLigandGraphBuilder;
import totah.lab.ligand.charge.LigandChargeAssigner;
import totah.lab.ligand.hydrogen.LigandHydrogenator;
import totah.lab.ligand.torsion.LigandTorsionTreeBuilder;
import totah.lab.ligand.typing.LigandAd4AtomTyper;
import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import org.junit.jupiter.api.Test;
import totah.lab.math.charges.ChargeModel;
import totah.lab.math.charges.ChargeSystem;
import totah.lab.pipeline.cleanup.ResidueClassifier;
import totah.lab.pipeline.cleanup.ResidueKind;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LigandCapabilityMatrixTest {

    private static final List<LigandReferenceCase> PANEL = List.of(
            prepares("LIG", Fixture.SUPPORTED),
            excluded("ALA", Fixture.STANDARD_RESIDUE, ResidueKind.STANDARD_AMINO_ACID),
            excluded("HOH", Fixture.WATER, ResidueKind.WATER),
            excluded("ZN", Fixture.ION, ResidueKind.ION_OR_METAL),
            rejected("NOC", Fixture.INCOMPLETE_CCD, LigandUnsupportedReason.INCOMPLETE_CCD),
            rejected("MIS", Fixture.MISSING_HEAVY, LigandUnsupportedReason.MISSING_HEAVY_ATOMS),
            rejected("EXT", Fixture.EXTRA_HEAVY, LigandUnsupportedReason.EXTRA_HEAVY_ATOMS),
            rejected("VAL", Fixture.INVALID_VALENCE, LigandUnsupportedReason.INVALID_VALENCE),
            rejected("GEO", Fixture.UNUSABLE_HYDROGEN_GEOMETRY,
                    LigandUnsupportedReason.UNUSABLE_HYDROGEN_REFERENCE_GEOMETRY),
            rejected("CHG", Fixture.UNSUPPORTED_CHARGE_MODEL,
                    LigandUnsupportedReason.UNSUPPORTED_ELEMENT_FOR_CHARGE),
            rejected("DIS", Fixture.DISCONNECTED, LigandUnsupportedReason.DISCONNECTED_GRAPH));

    @Test
    void enforcesMachineReadableCapabilityPanel() {
        for (LigandReferenceCase reference : PANEL) {
            switch (reference.expectation()) {
                case PREPARES_SUCCESSFULLY -> assertDoesNotThrow(
                        () -> prepare(reference),
                        reference.componentId());
                case CLASSIFICATION_EXCLUSION -> assertEquals(
                        reference.excludedKind(),
                        new ResidueClassifier().classify(fixture(reference.fixture()).residue()),
                        reference.componentId());
                case EXPLICIT_REJECTION -> {
                    UnsupportedLigandException exception = assertThrows(
                            UnsupportedLigandException.class,
                            () -> prepare(reference),
                            reference.componentId());
                    assertEquals(reference.componentId(), exception.getComponentId());
                    assertEquals(
                            reference.unsupportedReason(),
                            exception.getReason(),
                            reference.componentId());
                }
            }
        }
    }

    private LigandPreparationResult prepare(LigandReferenceCase reference) {
        PreparationFixture fixture = fixture(reference.fixture());
        if (reference.fixture() == Fixture.UNSUPPORTED_CHARGE_MODEL) {
            ChargeModel unsupportedModel = new ChargeModel() {
                @Override
                public double[] computeCharges(
                        ChargeSystem system,
                        double totalFormalCharge) {
                    throw new AssertionError("Unsupported model must be rejected before computation");
                }

                @Override
                public boolean hasParameters(String element) {
                    return false;
                }
            };
            return new LigandPreparer(
                    componentId -> fixture.component(),
                    new CcdLigandGraphBuilder(),
                    new LigandHydrogenator(),
                    new LigandChargeAssigner(unsupportedModel),
                    new LigandAd4AtomTyper(),
                    new LigandTorsionTreeBuilder())
                    .prepare(fixture.residue());
        }
        return new LigandPreparer(componentId -> fixture.component()).prepare(fixture.residue());
    }

    private PreparationFixture fixture(Fixture fixture) {
        return switch (fixture) {
            case SUPPORTED -> new PreparationFixture(
                    residue("LIG", atom("C1", "C", 0.0), atom("C2", "C", 1.5)),
                    component("LIG",
                            List.of(ccdAtom("C1", "C"), ccdAtom("C2", "C")),
                            List.of(bond("C1", "C2"))));
            case STANDARD_RESIDUE -> new PreparationFixture(
                    residue("ALA", atom("CA", "C", 0.0)), null);
            case WATER -> new PreparationFixture(
                    residue("HOH", atom("O", "O", 0.0)), null);
            case ION -> new PreparationFixture(
                    residue("ZN", atom("ZN", "Zn", 0.0)), null);
            case INCOMPLETE_CCD -> new PreparationFixture(
                    residue("NOC", atom("C1", "C", 0.0)), null);
            case MISSING_HEAVY -> new PreparationFixture(
                    residue("MIS", atom("C1", "C", 0.0)),
                    component("MIS",
                            List.of(ccdAtom("C1", "C"), ccdAtom("C2", "C")),
                            List.of(bond("C1", "C2"))));
            case EXTRA_HEAVY -> new PreparationFixture(
                    residue("EXT",
                            atom("C1", "C", 0.0),
                            atom("C2", "C", 1.5),
                            atom("C3", "C", 3.0)),
                    component("EXT",
                            List.of(ccdAtom("C1", "C"), ccdAtom("C2", "C")),
                            List.of(bond("C1", "C2"))));
            case INVALID_VALENCE -> invalidValenceFixture();
            case UNUSABLE_HYDROGEN_GEOMETRY -> new PreparationFixture(
                    residue("GEO", atom("C1", "C", 0.0), atom("C2", "C", 1.5)),
                    component("GEO",
                            List.of(
                                    ccdAtom("C1", "C"),
                                    ccdAtom("C2", "C"),
                                    ccdAtom("H1", "H")),
                            List.of(bond("C1", "C2"), bond("C1", "H1"))));
            case UNSUPPORTED_CHARGE_MODEL -> new PreparationFixture(
                    residue("CHG", atom("C1", "C", 0.0), atom("C2", "C", 1.5)),
                    component("CHG",
                            List.of(ccdAtom("C1", "C"), ccdAtom("C2", "C")),
                            List.of(bond("C1", "C2"))));
            case DISCONNECTED -> new PreparationFixture(
                    residue("DIS",
                            atom("C1", "C", 0.0),
                            atom("C2", "C", 1.5),
                            atom("C3", "C", 4.0)),
                    component("DIS",
                            List.of(
                                    ccdAtom("C1", "C"),
                                    ccdAtom("C2", "C"),
                                    ccdAtom("C3", "C")),
                            List.of(bond("C1", "C2"))));
        };
    }

    private PreparationFixture invalidValenceFixture() {
        List<Atom> atoms = new ArrayList<>();
        List<ChemCompAtom> ccdAtoms = new ArrayList<>();
        List<ChemCompBond> bonds = new ArrayList<>();
        atoms.add(atom("C1", "C", 0.0));
        ccdAtoms.add(ccdAtom("C1", "C"));
        for (int index = 1; index <= 5; index++) {
            String name = "H" + index;
            atoms.add(atom(name, "H", index));
            ccdAtoms.add(ccdAtom(name, "H"));
            bonds.add(bond("C1", name));
        }
        return new PreparationFixture(
                residue("VAL", atoms.toArray(Atom[]::new)),
                component("VAL", ccdAtoms, bonds));
    }

    private Residue residue(String name, Atom... atoms) {
        return Residue.builder()
                .name(name)
                .chain("A")
                .number(1)
                .insertionCode(' ')
                .atoms(List.of(atoms))
                .build();
    }

    private Atom atom(String name, String element, double x) {
        return Atom.builder()
                .name(name)
                .element(Element.fromSymbol(element))
                .position(new Point3D(x, 0.0, 0.0))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(0.0)
                .build();
    }

    private ChemComp component(
            String id,
            List<ChemCompAtom> atoms,
            List<ChemCompBond> bonds) {
        ChemComp component = new ChemComp();
        component.setId(id);
        component.setAtoms(atoms);
        component.setBonds(bonds);
        return component;
    }

    private ChemCompAtom ccdAtom(String id, String element) {
        ChemCompAtom atom = new ChemCompAtom();
        atom.setAtomId(id);
        atom.setTypeSymbol(element);
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

    private static LigandReferenceCase prepares(String id, Fixture fixture) {
        return new LigandReferenceCase(
                id, ReferenceExpectation.PREPARES_SUCCESSFULLY, fixture, null, null);
    }

    private static LigandReferenceCase excluded(
            String id,
            Fixture fixture,
            ResidueKind kind) {
        return new LigandReferenceCase(
                id, ReferenceExpectation.CLASSIFICATION_EXCLUSION, fixture, kind, null);
    }

    private static LigandReferenceCase rejected(
            String id,
            Fixture fixture,
            LigandUnsupportedReason reason) {
        return new LigandReferenceCase(
                id, ReferenceExpectation.EXPLICIT_REJECTION, fixture, null, reason);
    }

    private enum ReferenceExpectation {
        PREPARES_SUCCESSFULLY,
        CLASSIFICATION_EXCLUSION,
        EXPLICIT_REJECTION
    }

    private enum Fixture {
        SUPPORTED,
        STANDARD_RESIDUE,
        WATER,
        ION,
        INCOMPLETE_CCD,
        MISSING_HEAVY,
        EXTRA_HEAVY,
        INVALID_VALENCE,
        UNUSABLE_HYDROGEN_GEOMETRY,
        UNSUPPORTED_CHARGE_MODEL,
        DISCONNECTED
    }

    private record LigandReferenceCase(
            String componentId,
            ReferenceExpectation expectation,
            Fixture fixture,
            ResidueKind excludedKind,
            LigandUnsupportedReason unsupportedReason) {
    }

    private record PreparationFixture(Residue residue, ChemComp component) {
    }
}
