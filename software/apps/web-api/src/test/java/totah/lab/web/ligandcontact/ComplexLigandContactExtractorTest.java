package totah.lab.web.ligandcontact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.ligandcontact.ComplexLigandContactExtractor
        .ResidueMoietyContact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexLigandContactExtractorTest {

    private final ComplexLigandContactExtractor extractor =
            new ComplexLigandContactExtractor();

    @TempDir
    Path directory;

    @Test
    void classifiesResiduesByTheirNearestMoiety() throws IOException {
        Path pdb = directory.resolve("complex.pdb");
        Files.write(pdb, List.of(
                atom("ATOM", 1, "CA", "ALA", "A", 10, 0, 0, 3),
                atom("ATOM", 2, "CB", "ALA", "A", 10, 1, 1, 3.2),
                atom("ATOM", 3, "CA", "PHE", "A", 20, 30, 0, 2.5),
                atom("ATOM", 4, "CA", "GLY", "A", 30, 100, 100, 100),
                // SAM ligand: sulfonium near residue 10, adenine
                // near residue 20, methionine between, ribose far
                atom("HETATM", 5, "SD", "SAM", "L", 1, 0, 0, 0),
                atom("HETATM", 6, "CE", "SAM", "L", 1, 1.5, 0, 0),
                atom("HETATM", 7, "CA", "SAM", "L", 1, 0, 3, 0),
                atom("HETATM", 8, "N1", "SAM", "L", 1, 30, 0, 0),
                atom("HETATM", 9, "C6", "SAM", "L", 1, 31, 0, 0),
                atom("HETATM", 10, "C1'", "SAM", "L", 1, 15, 0, 0)
        ));

        List<ResidueMoietyContact> contacts =
                extractor.extract(pdb, "SAM", 4.5);

        assertEquals(3, contacts.size());

        ResidueMoietyContact alanine = contacts.get(0);
        assertEquals(10, alanine.residueNumber());
        assertEquals(SamMoiety.SULFONIUM, alanine.facingMoiety());
        assertEquals(3.0, alanine.facingDistance(), 1.0e-6);
        assertTrue(alanine.directContact());
        assertEquals(3.904, alanine.minimumDistances()
                .get(SamMoiety.METHIONINE), 1.0e-3);

        ResidueMoietyContact phenylalanine = contacts.get(1);
        assertEquals(SamMoiety.ADENINE, phenylalanine.facingMoiety());
        assertEquals(2.5, phenylalanine.facingDistance(), 1.0e-6);
        assertTrue(phenylalanine.directContact());

        ResidueMoietyContact glycine = contacts.get(2);
        assertFalse(glycine.directContact());
    }

    private static String atom(
            String record,
            int serial,
            String name,
            String residueName,
            String chain,
            int residueNumber,
            double x,
            double y,
            double z
    ) {
        return String.format(
                Locale.ROOT,
                "%-6s%5d %-4s %-3s %s%4d    %8.3f%8.3f%8.3f",
                record,
                serial,
                name,
                residueName,
                chain,
                residueNumber,
                x,
                y,
                z
        );
    }
}
