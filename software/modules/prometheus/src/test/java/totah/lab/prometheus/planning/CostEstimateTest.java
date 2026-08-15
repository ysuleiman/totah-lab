package totah.lab.prometheus.planning;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cost estimate arithmetic: zero, plus, aggregate. */
class CostEstimateTest {

    @Test
    void plusSumsEveryComponent() {
        CostEstimate a = new CostEstimate(1, 16.0, 0.5, 2.0, 0.5);
        CostEstimate b = new CostEstimate(2, 64.0, 2.0, 8.0, 1.25);

        CostEstimate sum = a.plus(b);

        assertThat(sum.jobCount()).isEqualTo(3);
        assertThat(sum.cpuHoursPerJob()).isEqualTo(80.0);
        assertThat(sum.expectedWallHours()).isEqualTo(2.5);
        assertThat(sum.expectedLocalRuntimeHours()).isEqualTo(10.0);
        assertThat(sum.estimatedRemoteCostUsd()).isEqualTo(1.75);
    }

    @Test
    void aggregateOfEmptyIsZero() {
        assertThat(CostEstimate.aggregate(List.of())).isEqualTo(CostEstimate.zero());
    }

    @Test
    void aggregateEqualsRepeatedPlus() {
        CostEstimate a = new CostEstimate(1, 16.0, 0.5, 2.0, 0.5);
        CostEstimate b = new CostEstimate(2, 64.0, 2.0, 8.0, 1.25);
        CostEstimate c = new CostEstimate(1, 4.0, 0.125, 0.5, 0.25);

        assertThat(CostEstimate.aggregate(List.of(a, b, c)))
                .isEqualTo(a.plus(b).plus(c));
    }

    @Test
    void negativeComponentsAreRejected() {
        assertThatThrownBy(() -> new CostEstimate(-1, 0.0, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CostEstimate(0, -1.0, 0.0, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CostEstimate(0, 0.0, 0.0, 0.0, -0.01))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
