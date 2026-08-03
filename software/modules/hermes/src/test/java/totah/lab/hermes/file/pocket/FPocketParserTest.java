package totah.lab.hermes.file.pocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FPocketParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesPocketWithEmptyAlphaSphereFileAsSphereLessPocket()
            throws Exception {

        Files.writeString(tempDir.resolve("prot_info.txt"), """
                Pocket 1 :
                \tScore : \t0.5
                \tDruggability Score : \t0.1
                \tNumber of Alpha Spheres : \t0
                """);
        Path pockets = Files.createDirectory(tempDir.resolve("pockets"));
        Files.writeString(pockets.resolve("pocket1_atm.pdb"),
                "ATOM      1  CA  ALA A   1      11.104   6.134   9.473"
                        + "  1.00  0.00           C  \n");
        Files.writeString(pockets.resolve("pocket1_vert.pqr"), "");

        List<Pocket> result = FPocketParser.parse(tempDir);

        assertEquals(1, result.size());
        Pocket pocket = result.getFirst();
        assertEquals(PocketSource.FPOCKET, pocket.source());
        assertEquals(new Point3D(0.0, 0.0, 0.0), pocket.center());
        assertTrue(pocket.alphaSphereSet().isEmpty());
        assertEquals(1, pocket.residues().size());
        assertEquals("A", pocket.residues().getFirst().chainId());
        assertEquals(0.5, pocket.metric(
                PocketMetricType.FPOCKET_SCORE).orElseThrow(), 1.0e-9);
    }

    @Test
    void parsesSphereLessPocketAlongsidePocketsWithSpheres()
            throws Exception {

        Files.writeString(tempDir.resolve("prot_info.txt"), """
                Pocket 1 :
                \tScore : \t0.5
                Pocket 2 :
                \tScore : \t0.9
                """);
        Path pockets = Files.createDirectory(tempDir.resolve("pockets"));
        Files.writeString(pockets.resolve("pocket1_atm.pdb"),
                "ATOM      1  CA  ALA A   1      11.104   6.134   9.473"
                        + "  1.00  0.00           C  \n");
        Files.writeString(pockets.resolve("pocket1_vert.pqr"), "");
        Files.writeString(pockets.resolve("pocket2_atm.pdb"),
                "ATOM      1  CA  ALA A   2      11.104   6.134   9.473"
                        + "  1.00  0.00           C  \n");
        Files.writeString(pockets.resolve("pocket2_vert.pqr"),
                "ATOM      1    O STP     1       1.0   2.0   3.0"
                        + "    0.00     1.50\n");

        List<Pocket> result = FPocketParser.parse(tempDir);

        assertEquals(2, result.size());
        assertTrue(result.get(0).alphaSphereSet().isEmpty());
        assertEquals(1, result.get(1).alphaSphereSet()
                .orElseThrow().spheres().size());
        assertEquals(new Point3D(1.0, 2.0, 3.0), result.get(1).center());
    }
}
