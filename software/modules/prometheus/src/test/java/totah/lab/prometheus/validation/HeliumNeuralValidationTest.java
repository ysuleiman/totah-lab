package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HeliumNeuralValidationTest {
    @Test
    void representsCorrelatedInteractingElectronHelium() {
        var result=HeliumNeuralValidation.run();
        assertThat(result.passed()).as(result.toJson()).isTrue();
        assertThat(result.correlatedEnergyHartree()).isLessThan(result.analyticUncorrelatedEnergyHartree());
        assertThat(result.recoveredCorrelationFraction()).isGreaterThanOrEqualTo(0.55);
        assertThat(result.permutationError()).isLessThanOrEqualTo(1e-12);
    }
}
