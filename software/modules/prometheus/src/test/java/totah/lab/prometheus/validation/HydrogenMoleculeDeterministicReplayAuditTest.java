package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HydrogenMoleculeDeterministicReplayAuditTest {
    @Test void replayIsBitwiseDeterministic(){var result=HydrogenMoleculeDeterministicReplayAudit.run();
        assertThat(result.passed()).isTrue();assertThat(result.maximumParameterDifference()).isZero();}
}
