package totah.lab.daedalus.ligandprep;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileLigandPrepSamplerTest {
    @TempDir
    Path referenceDirectory;

    @Test
    void samplesFirstCountRowsSortedById() throws Exception {
        writePair("b-ligand", "BBB");
        writePair("a-ligand", "AAA");
        writePair("c-ligand", "CCC");
        Files.writeString(referenceDirectory.resolve("manifest.tsv"),
                "id\tname\th_added\n"
                        + "b-ligand\tBBB\tfalse\n"
                        + "a-ligand\tAAA\ttrue\n"
                        + "c-ligand\tCCC\tfalse\n");

        List<LigandPrepSample> samples =
                new FileLigandPrepSampler(referenceDirectory).sample(2);

        assertEquals(2, samples.size());
        assertEquals("a-ligand", samples.get(0).id());
        assertEquals("AAA", samples.get(0).name());
        assertEquals(referenceDirectory.resolve("a-ligand.sdf"),
                samples.get(0).sdf());
        assertEquals(referenceDirectory.resolve(
                        "a-ligand.meeko.pdbqt"),
                samples.get(0).meekoPdbqt());
        assertEquals("b-ligand", samples.get(1).id());
    }

    @Test
    void missingManifestIsAnIoFailure() {
        IOException exception = assertThrows(IOException.class, () ->
                new FileLigandPrepSampler(referenceDirectory).sample(1));
        assertTrue(exception.getMessage().contains("manifest"));
    }

    @Test
    void incompletePairIsAnIoFailure() throws Exception {
        Files.writeString(referenceDirectory.resolve("manifest.tsv"),
                "id\tname\th_added\nlone\tL\tfalse\n");
        Files.writeString(referenceDirectory.resolve("lone.sdf"), "sdf");

        IOException exception = assertThrows(IOException.class, () ->
                new FileLigandPrepSampler(referenceDirectory).sample(1));
        assertTrue(exception.getMessage().contains("lone"));
    }

    private void writePair(String id, String content) throws IOException {
        Files.writeString(referenceDirectory.resolve(id + ".sdf"),
                content);
        Files.writeString(referenceDirectory.resolve(
                id + ".meeko.pdbqt"), content);
    }
}
