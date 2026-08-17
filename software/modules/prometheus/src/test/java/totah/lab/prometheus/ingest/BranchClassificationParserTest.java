package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BranchClassificationParserTest {

    @Test
    void finalDecisionTakesPrecedenceOverAlphabeticallyEarlierDevelopmentDecision(@TempDir Path directory)
            throws Exception {
        Files.writeString(directory.resolve("DEVELOPMENT_DECISION.json"),
                "{\"classification\":\"DEVELOPMENT_CANDIDATE_ACCEPTED\"}");
        Files.writeString(directory.resolve("FINAL_DECISION.json"),
                "{\"classification\":\"FINAL_HOLDOUT_REJECTED\"}");

        BranchClassificationParser.RecoveredClassification recovered =
                BranchClassificationParser.find(directory).orElseThrow();

        assertThat(recovered.classification()).isEqualTo("FINAL_HOLDOUT_REJECTED");
        assertThat(recovered.reportPath()).hasFileName("FINAL_DECISION.json");
    }
}
