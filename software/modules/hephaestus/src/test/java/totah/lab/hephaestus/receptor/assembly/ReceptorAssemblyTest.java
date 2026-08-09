package totah.lab.hephaestus.receptor.assembly;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceptorAssemblyTest {

    @Test
    void retainsPreparedChemistryWithValidatedPoseCoordinates() {
        PreparedLigand prepared = PreparedLigand.of(ligand(
                preparedAtom("C1", Element.C, 0.0, -0.25, "C"),
                atom("O1", Element.O, 1.0)));
        Ligand posed = ligand(
                atom("C1", Element.C, 10.0),
                atom("O1", Element.O, 11.0));

        LigandPose pose = new LigandPose(
                "sam-pose-1", prepared, posed, Map.of("run", "sam-run"));

        assertSame(prepared.topology(), pose.preparedPose().topology());
        assertSame(prepared.charges(), pose.preparedPose().charges());
        assertEquals(
                10.0,
                pose.preparedPose().ligand().structure().getChains()
                        .getFirst().residues().getFirst().getAtoms()
                        .getFirst().getPosition().x());
        Atom positionedCarbon = pose.preparedPose().ligand().structure()
                .getChains().getFirst().residues().getFirst().getAtoms()
                .getFirst();
        assertEquals(-0.25, positionedCarbon.getCharge());
        assertEquals("C", positionedCarbon.getAutoDockType());
    }

    @Test
    void rejectsPosesThatChangeAtomOrderOrIdentity() {
        PreparedLigand prepared = PreparedLigand.of(ligand(
                atom("C1", Element.C, 0.0),
                atom("O1", Element.O, 1.0)));
        Ligand reordered = ligand(
                atom("O1", Element.O, 11.0),
                atom("C1", Element.C, 10.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> new LigandPose(
                        "sam-pose-1", prepared, reordered, Map.of()));
    }

    @Test
    void preservesFixedCofactorOrderAndRejectsDuplicateIds() {
        PreparedProtein protein = PreparedProtein.of(new Protein(
                "protein", null, "Protein", null, null, null,
                new Structure(List.of())));
        LigandPose pose = new LigandPose(
                "pose-1",
                PreparedLigand.of(ligand(atom("C1", Element.C, 0.0))),
                ligand(atom("C1", Element.C, 2.0)),
                Map.of());
        FixedCofactor sam = new FixedCofactor("sam", "sam", pose);

        ReceptorAssembly assembly = ReceptorAssembly.of(protein)
                .withFixedCofactor(sam);

        assertEquals(List.of(sam), assembly.fixedCofactors());
        assertEquals("SAM", sam.componentCode());
        assertThrows(
                IllegalArgumentException.class,
                () -> assembly.withFixedCofactor(sam));
    }

    @Test
    void buildsProteinFirstStructureWithPreparedCofactorChemistry() {
        PreparedProtein protein = PreparedProtein.of(new Protein(
                "protein", null, "Protein", null, null, null,
                new Structure(List.of(new Chain(
                        "A",
                        List.of(new Residue(
                                "ALA", 1,
                                List.of(atom("CA", Element.C, 0.0)))))))));
        PreparedLigand preparedSam = PreparedLigand.of(ligand(
                preparedAtom("C1", Element.C, 1.0, 0.5, "C")));
        FixedCofactor sam = new FixedCofactor(
                "sam",
                "SAM",
                new LigandPose(
                        "pose-1",
                        preparedSam,
                        ligand(atom("C1", Element.C, 8.0)),
                        Map.of()));

        Structure structure = new ReceptorAssemblyStructureBuilder().build(
                ReceptorAssembly.of(protein).withFixedCofactor(sam));

        assertEquals(List.of("A", "Z"), structure.getChains().stream()
                .map(Chain::id)
                .toList());
        Atom cofactorAtom = structure.getChains().get(1).residues()
                .getFirst().getAtoms().getFirst();
        assertEquals(8.0, cofactorAtom.getPosition().x());
        assertEquals(0.5, cofactorAtom.getCharge());
        assertEquals("C", cofactorAtom.getAutoDockType());
    }

    @Test
    void rejectsCofactorChainCollisions() {
        PreparedProtein protein = PreparedProtein.of(new Protein(
                "protein", null, "Protein", null, null, null,
                new Structure(List.of(new Chain(
                        "Z",
                        List.of(new Residue(
                                "ALA", 1,
                                List.of(atom("CA", Element.C, 0.0)))))))));
        FixedCofactor sam = new FixedCofactor(
                "sam",
                "SAM",
                new LigandPose(
                        "pose-1",
                        PreparedLigand.of(ligand(
                                atom("C1", Element.C, 1.0))),
                        ligand(atom("C1", Element.C, 8.0)),
                        Map.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ReceptorAssemblyStructureBuilder().build(
                        ReceptorAssembly.of(protein)
                                .withFixedCofactor(sam)));
    }

    private static Ligand ligand(Atom... atoms) {
        return new Ligand(
                "SAM",
                "SAM",
                "SAM",
                null,
                null,
                null,
                new Structure(List.of(new Chain(
                        "Z",
                        List.of(new Residue("SAM", 1, List.of(atoms)))))));
    }

    private static Atom atom(
            String name,
            Element element,
            double x) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, 0.0, 0.0))
                .build();
    }

    private static Atom preparedAtom(
            String name,
            Element element,
            double x,
            double charge,
            String autoDockType) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, 0.0, 0.0))
                .charge(charge)
                .autoDockType(autoDockType)
                .build();
    }
}
