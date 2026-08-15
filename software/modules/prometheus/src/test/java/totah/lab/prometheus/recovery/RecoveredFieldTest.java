package totah.lab.prometheus.recovery;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RecoveredFieldTest {

    @Test
    void recoverableValueRequiresExactProvenance() {
        assertThatThrownBy(() -> new RecoveredField<>(
                "softwareVersion", Optional.of("2.14.0"),
                RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                List.of(), "parsed"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unrecoverableValueCannotSmuggleInAGuess() {
        assertThatThrownBy(() -> new RecoveredField<>(
                "softwareVersion", Optional.of("guessed"),
                RecoveryClassification.GENUINELY_UNRECOVERABLE,
                List.of(), "not present"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
