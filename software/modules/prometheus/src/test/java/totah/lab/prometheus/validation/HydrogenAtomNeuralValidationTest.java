package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HydrogenAtomNeuralValidationTest {
    @Test
    void solvesHydrogenAtomAndPassesCoulombPhysicsGates() {
        var result=HydrogenAtomNeuralValidation.run();
        assertThat(result.passed()).isTrue();
        assertThat(result.absoluteEnergyErrorHartree())
                .isLessThanOrEqualTo(HydrogenAtomNeuralValidation.ENERGY_ERROR_GATE_HARTREE);
        assertThat(result.normalizedWavefunctionOverlap())
                .isGreaterThanOrEqualTo(HydrogenAtomNeuralValidation.OVERLAP_GATE);
        assertThat(Math.abs(result.cuspLogarithmicDerivative()+1.0))
                .isLessThanOrEqualTo(HydrogenAtomNeuralValidation.CUSP_ERROR_GATE);
        assertThat(result.derivativeAudit().maxGradientComponentError())
                .isLessThanOrEqualTo(HydrogenAtomNeuralValidation.GRADIENT_COMPONENT_ERROR_GATE);
        assertThat(result.derivativeAudit().laplacianAbsoluteError())
                .isLessThanOrEqualTo(HydrogenAtomNeuralValidation.LAPLACIAN_ERROR_GATE);
    }
}
