package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HydrogenMoleculeCurveValidationTest {
    @Test
    void preservesCompleteGateFailureWithoutRelaxingCriteria() {
        var result=HydrogenMoleculeCurveValidation.run();
        assertThat(result.passed()).as(result.toJson()).isFalse();
        assertThat(result.points()).hasSize(9);
        assertThat(result.points()).allMatch(point->point.redundantEvaluations()==0);
        assertThat(result.rmse()).isLessThanOrEqualTo(0.015);
        assertThat(result.points()).anyMatch(point->!point.converged());
        assertThat(result.points()).anyMatch(point->point.variance()>0.10);
    }
}
