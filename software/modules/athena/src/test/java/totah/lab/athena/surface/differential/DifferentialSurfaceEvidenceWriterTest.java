package totah.lab.athena.surface.differential;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentialSurfaceEvidenceWriterTest {
    @Test
    void traceUsesCanonicalMinimumOfDirectionalDistances() throws Exception {
        SurfaceResidue q1 = surface("A", 1, "ALA", 0.0, 0.0);
        SurfaceResidue q2 = surface("A", 2, "GLU", 4.0, 0.0);
        SurfaceResidue s1 = surface("B", 11, "ALA", 0.0, 0.0);
        SurfaceResidue s2 = surface("B", 12, "ASP", 3.0, 0.0);
        ExplicitResidueCorrespondence correspondence =
                new ExplicitResidueCorrespondence(Map.of(q1.id(), s1.id(), q2.id(), s2.id()));
        Path output = Files.createTempFile("surface-evidence-", ".csv");

        DifferentialSurfaceEvidenceWriter.write(output, "Q_VS_S",
                List.of(q1, q2), List.of(s1, s2), correspondence,
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE);

        String row = Files.readAllLines(output).stream()
                .filter(line -> line.contains(",A,2,GLU,B,12,ASP,"))
                .findFirst().orElseThrow();
        assertThat(row).contains(",4.000000000000,3.000000000000,3.000000000000,");
    }

    private static SurfaceResidue surface(String chain, int number, String name,
            double caX, double cbX) {
        Atom ca = Atom.builder().pdbSerial(number * 10).name("CA")
                .position(new Point3D(caX, 0, 0)).element(Element.C).build();
        Atom cb = Atom.builder().pdbSerial(number * 10 + 1).name("CB")
                .position(new Point3D(cbX == 0.0 ? caX : cbX, 0, 0))
                .element(Element.C).build();
        Residue residue = new Residue(name, number, List.of(ca, cb));
        return new SurfaceResidue(new ResidueId(chain, number, null), residue, 50, 0.5);
    }
}
