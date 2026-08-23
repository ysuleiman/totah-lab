package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ForceCampaignPreflightRunnerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test void energyCannotDefaultToZeroWhenMissingOrNonNumeric() throws Exception {
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(
                JSON.readTree("{}"), "energy_hartree")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(
                JSON.readTree("{\"energy_hartree\":\"not-a-number\"}"), "energy_hartree"))
                .isInstanceOf(IOException.class);
        assertThat(ForceCampaignPreflightRunner.requiredFiniteDouble(
                JSON.readTree("{\"energy_hartree\":-1.25}"), "energy_hartree")).isEqualTo(-1.25);
    }

    @Test void privateAliasGradientPromotionRejectsNonfiniteComponentsAtEveryPosition() throws Exception {
        for (String row : new String[] {"NaN 1 2", "1 Infinity 2", "1 2 -Infinity"}) {
            Path gradient = temp.resolve("gradient-" + Math.abs(row.hashCode()) + ".txt");
            Files.writeString(gradient, row + "\n");
            assertThatThrownBy(() -> ForceCampaignPreflightRunner.readFiniteGradient(gradient, 3))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("non-finite");
        }
    }
}
