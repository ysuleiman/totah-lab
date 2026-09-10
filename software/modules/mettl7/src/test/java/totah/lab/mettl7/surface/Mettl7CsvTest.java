package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7CsvTest {
    @Test
    void roundTripsCommaAndQuote() throws Exception {
        Path path = Files.createTempFile("mettl7-csv-", ".csv");
        Files.writeString(path, Mettl7Csv.row("a", "b")
                + Mettl7Csv.row("x,y", "q\"z"));
        assertThat(Mettl7Csv.read(path).getFirst())
                .containsEntry("a", "x,y").containsEntry("b", "q\"z");
    }

    @Test
    void rejectsNonRectangularRows() throws Exception {
        Path path = Files.createTempFile("mettl7-csv-bad-", ".csv");
        Files.writeString(path, "a,b\n1\n");
        assertThatThrownBy(() -> Mettl7Csv.read(path)).isInstanceOf(Exception.class);
    }

    @Test
    void rejectsCharactersAfterClosingQuote() throws Exception {
        Path path = Files.createTempFile("mettl7-csv-quote-", ".csv");
        Files.writeString(path, "a,b\n\"x\"junk,y\n");
        assertThatThrownBy(() -> Mettl7Csv.read(path))
                .hasMessageContaining("closing CSV quote");
    }
}
