package totah.lab.hermes.fasta;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FastaWriterTest {

    @Test
    void outputRoundTripsThroughParser() throws Exception {
        var expected = List.of(new FastaRecord("Q9H8H3", "Human protein", "ABCDEFGHIJ"));
        var output = new StringWriter();

        new FastaWriter(4).write(output, expected);

        assertEquals(expected, new FastaParser().parse(new StringReader(output.toString())));
    }
}
