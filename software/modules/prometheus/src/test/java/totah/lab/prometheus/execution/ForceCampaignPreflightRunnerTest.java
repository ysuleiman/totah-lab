package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;

final class ForceCampaignPreflightRunnerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void energyCannotDefaultToZeroWhenMissingOrNonNumeric() throws Exception {
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(
                JSON.readTree("{}"), "energy_hartree")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ForceCampaignPreflightRunner.requiredFiniteDouble(
                JSON.readTree("{\"energy_hartree\":\"not-a-number\"}"), "energy_hartree"))
                .isInstanceOf(IOException.class);
        assertThat(ForceCampaignPreflightRunner.requiredFiniteDouble(
                JSON.readTree("{\"energy_hartree\":-1.25}"), "energy_hartree")).isEqualTo(-1.25);
    }
}
