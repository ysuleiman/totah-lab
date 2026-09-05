package totah.lab.mettl7.triage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleArtifactTest {
    @Test
    void checkedInExampleOutputIsProducedByCheckedInInput() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        Mettl7TriageJsonCodec codec = new Mettl7TriageJsonCodec();
        Mettl7TriageResult actual = new Mettl7LigandTriageService()
                .assess(codec.readInput(root.resolve("EXAMPLE_INPUT.json")));
        Mettl7TriageResult expected = codec.readResult(
                Files.readString(root.resolve("EXAMPLE_OUTPUT.json")));
        assertThat(actual).isEqualTo(expected);
    }
}
