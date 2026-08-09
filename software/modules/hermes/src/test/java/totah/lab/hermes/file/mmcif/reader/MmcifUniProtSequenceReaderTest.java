package totah.lab.hermes.file.mmcif.reader;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MmcifUniProtSequenceReaderTest {
    @Test
    void readsSourceReportedUniProtSequence() throws Exception {
        Path source = Path.of(getClass().getResource(
                "/mmcif/residue-mapping-entry.cif").toURI());
        var references = new MmcifUniProtSequenceReader().read(source);
        assertEquals(1, references.size());
        assertEquals("QTEST1", references.getFirst().accession());
        assertEquals("MKAGS", references.getFirst().sequence());
    }
}
