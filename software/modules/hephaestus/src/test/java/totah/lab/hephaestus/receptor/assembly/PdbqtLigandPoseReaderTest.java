package totah.lab.hephaestus.receptor.assembly;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hermes.file.pdbqt.AtomRecordType;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdbqtLigandPoseReaderTest {

    @Test
    void mapsOnlyPoseCoordinatesAndRetainsPreparedChemistry() {
        PreparedLigand prepared = PreparedLigand.of(ligand(
                preparedAtom("C1", Element.C, -0.2, "A"),
                preparedAtom("O1", Element.O, -0.4, "OA")));
        PdbqtModel model = new PdbqtModel(
                3,
                List.of(
                        pdbqtAtom(1, "C", "A", 10.0),
                        pdbqtAtom(2, "O", "OA", 11.0)),
                null,
                List.of("REMARK VINA RESULT: -7.4 0.0 0.0"));

        LigandPose pose = new PdbqtLigandPoseReader().map(
                model, prepared, "sam-run");

        List<Atom> atoms = pose.preparedPose().ligand().structure()
                .getChains().getFirst().residues().getFirst().getAtoms();
        assertEquals("sam-run:model-3", pose.id());
        assertEquals(10.0, atoms.get(0).getPosition().x());
        assertEquals(11.0, atoms.get(1).getPosition().x());
        assertEquals(-0.2, atoms.get(0).getCharge());
        assertEquals("A", atoms.get(0).getAutoDockType());
        assertEquals("-7.4", pose.provenance()
                .get("vina-affinity-kcal-per-mol"));
        assertEquals("3", pose.provenance().get("pdbqt-model"));
    }

    @Test
    void rejectsAtomCountAndElementMismatches() {
        PreparedLigand prepared = PreparedLigand.of(ligand(
                preparedAtom("C1", Element.C, 0.0, "C")));
        PdbqtLigandPoseReader reader = new PdbqtLigandPoseReader();

        assertThrows(
                IllegalArgumentException.class,
                () -> reader.map(
                        new PdbqtModel(1, List.of(), null, List.of()),
                        prepared,
                        "run"));
        assertThrows(
                IllegalArgumentException.class,
                () -> reader.map(
                        new PdbqtModel(
                                1,
                                List.of(pdbqtAtom(1, "O", "OA", 1.0)),
                                null,
                                List.of()),
                        prepared,
                        "run"));
    }

    private static Ligand ligand(Atom... atoms) {
        return new Ligand(
                "SAM", "SAM", "SAM", null, null, null,
                new Structure(List.of(new Chain(
                        "Z",
                        List.of(new Residue("SAM", 1, List.of(atoms)))))));
    }

    private static Atom preparedAtom(
            String name,
            Element element,
            double charge,
            String autoDockType) {

        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(0.0, 0.0, 0.0))
                .charge(charge)
                .autoDockType(autoDockType)
                .build();
    }

    private static PdbqtAtom pdbqtAtom(
            int serial,
            String element,
            String autoDockType,
            double x) {

        return new PdbqtAtom(
                AtomRecordType.ATOM,
                serial,
                element,
                "SAM",
                "Z",
                1,
                null,
                x,
                0.0,
                0.0,
                1.0,
                0.0,
                99.0,
                autoDockType);
    }
}
