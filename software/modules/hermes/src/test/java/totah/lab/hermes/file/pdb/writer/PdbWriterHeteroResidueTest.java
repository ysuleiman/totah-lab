package totah.lab.hermes.file.pdb.writer;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.PdbWriteOptions;

import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdbWriterHeteroResidueTest {

    @Test
    void writesOnlySelectedResiduesAsHetatm() throws Exception {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(residue("ALA", 1, "CA"))),
                new Chain("Z", List.of(residue("SAM", 1, "C1")))));
        StringWriter output = new StringWriter();

        new PdbWriter().write(
                structure,
                output,
                PdbWriteOptions.defaults(),
                Set.of(new ResidueId("Z", 1, null)));

        String text = output.toString();
        assertTrue(text.lines().anyMatch(line ->
                line.startsWith("ATOM") && line.contains("ALA")));
        assertTrue(text.lines().anyMatch(line ->
                line.startsWith("HETATM") && line.contains("SAM")));
    }

    private static Residue residue(
            String name,
            int number,
            String atomName) {

        return new Residue(name, number, List.of(Atom.builder()
                .name(atomName)
                .element(Element.C)
                .position(new Point3D(number, 0.0, 0.0))
                .build()));
    }
}
