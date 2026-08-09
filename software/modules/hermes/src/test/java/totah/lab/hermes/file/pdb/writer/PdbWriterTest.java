package totah.lab.hermes.file.pdb.writer;

import totah.lab.hermes.file.pdb.PdbWriteOptions;
import totah.lab.hermes.file.pdb.writer.PdbWriter;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdbWriterTest {

    @Test
    void writesStandardFixedColumnAtomRecords() throws java.io.IOException {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(
                        new Residue("PHE", 43, List.of(
                                atom(1, "N", Element.N,
                                        10.123, -4.567, 1.25, -0.3, 12.34),
                                atom(2, "CA", Element.C,
                                        11.0, -5.0, 0.5, 0.1, 10.0),
                                atom(3, "CD1", Element.C,
                                        9.462, -4.882, 1.366, -0.1, 11.5))))),
                new Chain("B", List.of(
                        new Residue("ZN", 1, 'A', List.of(
                                atom(4, "ZN", Element.ZN,
                                        -1.5, 2.25, 0.0, 1.0, 5.5)))))));

        StringWriter out = new StringWriter();
        new PdbWriter().write(structure, out, null);

        String[] lines = out.toString().split("\n");
        assertThat(lines).containsExactly(
                "ATOM      1  N   PHE A  43      10.123  -4.567   1.250"
                        + " -0.30 12.34           N",
                "ATOM      2  CA  PHE A  43      11.000  -5.000   0.500"
                        + "  0.10 10.00           C",
                "ATOM      3  CD1 PHE A  43       9.462  -4.882   1.366"
                        + " -0.10 11.50           C",
                "TER",
                "ATOM      4 ZN   ZN  B   1A     -1.500   2.250   0.000"
                        + "  1.00  5.50          Zn",
                "TER",
                "END");
    }

    @Test
    void columnsLandInTheirFixedPositions() throws java.io.IOException {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(
                        new Residue("LEU", 7, List.of(
                                atom(1, "CG", Element.C,
                                        13.323, -5.673, 1.638, 0.05, 9.99)))))));

        StringWriter out = new StringWriter();
        new PdbWriter().write(structure, out,
                new PdbWriteOptions(false, false));

        String line = out.toString().lines().findFirst().orElseThrow();
        assertThat(line).hasSize(78);
        assertThat(line.substring(0, 6)).isEqualTo("ATOM  ");
        assertThat(line.substring(6, 11)).isEqualTo("    1");
        assertThat(line.substring(12, 16)).isEqualTo(" CG ");
        assertThat(line.substring(17, 20)).isEqualTo("LEU");
        assertThat(line.charAt(21)).isEqualTo('A');
        assertThat(line.substring(22, 26)).isEqualTo("   7");
        assertThat(line.substring(30, 38)).isEqualTo("  13.323");
        assertThat(line.substring(38, 46)).isEqualTo("  -5.673");
        assertThat(line.substring(46, 54)).isEqualTo("   1.638");
        assertThat(line.substring(54, 60)).isEqualTo("  0.05");
        assertThat(line.substring(60, 66)).isEqualTo("  9.99");
        assertThat(line.substring(76, 78)).isEqualTo(" C");
    }

    @Test
    void honorsChainTerminatorAndEndOptions() throws java.io.IOException {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(
                        new Residue("GLY", 1, List.of(
                                atom(1, "CA", Element.C,
                                        0.0, 0.0, 0.0, 0.0, 0.0)))))));

        StringWriter out = new StringWriter();
        new PdbWriter().write(structure, out,
                new PdbWriteOptions(false, false));

        assertThat(out.toString().lines())
                .noneMatch(line -> line.equals("TER")
                        || line.equals("END"));
    }

    private static Atom atom(int serial, String name, Element element,
            double x, double y, double z, double occupancy,
            double bFactor) {
        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(occupancy)
                .bFactor(bFactor)
                .element(element)
                .build();
    }
}
