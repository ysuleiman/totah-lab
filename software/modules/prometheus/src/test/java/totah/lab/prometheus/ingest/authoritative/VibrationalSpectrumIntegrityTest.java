package totah.lab.prometheus.ingest.authoritative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class VibrationalSpectrumIntegrityTest {

    private static final String PROJECTED =
            "PySCF harmonic_analysis exclude_trans=True exclude_rot=True";

    @Test
    void preservesNegativeModesAndClassifiesTheirActualCount() throws IOException {
        VibrationalSpectrumIntegrity.Assessment result = VibrationalSpectrumIntegrity.assessProjected(
                List.of(-80.0, -12.0, 31.0, 92.0), PROJECTED, Path.of("signed-frequencies.txt"));

        assertThat(result.classification())
                .isEqualTo(VibrationalSpectrumIntegrity.Classification.SADDLE_POINT);
        assertThat(result.negativeVibrationalModes()).isEqualTo(2);
    }

    @Test
    void rejectsExactZeroAfterRigidBodyModesWereProjectedOut() {
        assertThatThrownBy(() -> VibrationalSpectrumIntegrity.assessProjected(
                List.of(0.0, 31.0), PROJECTED, Path.of("legacy-frequencies.txt")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("suspicious exact zero")
                .hasMessageContaining("translation/rotation were declared excluded");
    }

    @Test
    void requiresExplicitRigidBodyProjectionIdentity() {
        assertThatThrownBy(() -> VibrationalSpectrumIntegrity.assessProjected(
                List.of(31.0, 92.0), "unknown projection", Path.of("frequencies.txt")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("does not establish removal of translation and rotation");
    }
}
