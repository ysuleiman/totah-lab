package totah.lab.hermes.fasta;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FastaParserTest {

    @Test
    void parsesMultipleRecordsAndWrappedSequences() throws Exception {
        Path path = Path.of(getClass().getResource("/fasta/proteins.fasta").toURI());

        var records = new FastaParser().parse(path);

        assertEquals(2, records.size());
        assertEquals("sp|Q9H8H3|METTL7A_HUMAN", records.getFirst().identifier());
        assertEquals("MARGKKIGYSPRKGTGRKGT", records.getFirst().sequence());
        assertEquals("Example protein", records.get(1).description());
        assertThrows(UnsupportedOperationException.class,
                () -> records.add(new FastaRecord("x", null, "A")));
    }
}
