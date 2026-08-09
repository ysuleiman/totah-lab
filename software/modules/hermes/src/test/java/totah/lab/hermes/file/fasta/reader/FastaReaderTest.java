package totah.lab.hermes.file.fasta.reader;

import totah.lab.hermes.file.fasta.FastaRecord;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastaReaderTest {

    @Test
    void parsesMultipleRecordsAndWrappedSequences() throws Exception {
        Path path = Path.of(getClass().getResource("/fasta/proteins.fasta").toURI());

        var records = new FastaReader().parse(path);

        assertEquals(2, records.size());
        assertEquals("sp|Q9H8H3|METTL7A_HUMAN", records.getFirst().identifier());
        assertEquals("MARGKKIGYSPRKGTGRKGT", records.getFirst().sequence());
        assertEquals("Example protein", records.get(1).description());
        assertThrows(UnsupportedOperationException.class,
                () -> records.add(new FastaRecord("x", null, "A")));
    }
}
