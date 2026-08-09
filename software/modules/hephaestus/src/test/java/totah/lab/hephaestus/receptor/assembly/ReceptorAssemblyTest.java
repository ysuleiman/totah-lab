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
                atom("C1", Element.C, 0.0),
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
}
