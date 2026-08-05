package totah.lab.daedalus.docking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VinaOutputParserTest {

    @Test
    void parsesPoseTableRows() {
        String output = """
                AutoDock Vina v1.2.5
                Detected 8 CPUs
                Reading input ... done.

                   mode |   affinity | dist from best mode
                        | (kcal/mol) | rmsd l.b.| rmsd u.b.
                   -----+------------+----------+----------
                      1        -7.5      0.000      0.000
                      2        -7.1      1.234      2.345
                      3        -6.8      2.000      3.500

                Writing output ... done.
                """;

        List<VinaPose> poses = VinaOutputParser.parse(output);

        assertEquals(3, poses.size());
        assertEquals(new VinaPose(1, -7.5, 0.0, 0.0), poses.get(0));
        assertEquals(new VinaPose(2, -7.1, 1.234, 2.345), poses.get(1));
        assertEquals(new VinaPose(3, -6.8, 2.0, 3.5), poses.get(2));
    }

    @Test
    void ignoresNonTableLines() {
        assertTrue(VinaOutputParser.parse("no table here\n1 2 3\n").isEmpty());
        assertTrue(VinaOutputParser.parse("").isEmpty());
        assertTrue(VinaOutputParser.parse(null).isEmpty());
    }
}
