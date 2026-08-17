package totah.lab.prometheus.numerics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class StreamingMeanCenteredCovarianceOperatorTest {

    @Test
    void onTheFlyCenteringMatchesExistingCenteredOperator() {
        double[][] observations = {
                {1.2, -0.7, 0.3, 2.1},
                {-0.4, 0.8, 1.5, -1.2},
                {0.9, 0.1, -0.6, 0.4}
        };

        double[] weights = {
                0.2,
                0.3,
                0.5
        };

        double[] mean = new double[4];

        for (int sample = 0; sample < observations.length; sample++) {
            for (int i = 0; i < mean.length; i++) {
                mean[i] +=
                        weights[sample]
                                * observations[sample][i];
            }
        }

        List<double[]> centered = new ArrayList<>();

        for (double[] observation : observations) {
            double[] value = observation.clone();

            for (int i = 0; i < value.length; i++) {
                value[i] -= mean[i];
            }

            centered.add(value);
        }

        double damping = 0.37;

        StreamingCovarianceOperator existing =
                new StreamingCovarianceOperator(
                        4,
                        consumer -> {
                            for (int sample = 0;
                                 sample < centered.size();
                                 sample++) {

                                consumer.accept(
                                        weights[sample],
                                        centered.get(sample));
                            }
                        },
                        damping);

        StreamingMeanCenteredCovarianceOperator optimized =
                new StreamingMeanCenteredCovarianceOperator(
                        4,
                        consumer -> {
                            for (int sample = 0;
                                 sample < observations.length;
                                 sample++) {

                                consumer.accept(
                                        weights[sample],
                                        observations[sample]);
                            }
                        },
                        mean,
                        damping);

        double[][] probes = {
                {0.5, -0.2, 1.1, 0.3},
                {-1.0, 2.0, 0.25, -0.75},
                {1.0, 1.0, 1.0, 1.0}
        };

        for (double[] probe : probes) {
            double[] expected =
                    existing.apply(probe);

            double[] actual =
                    optimized.apply(probe);

            assertArrayEquals(
                    expected,
                    actual,
                    2e-15);
        }

        assertEquals(
                probes.length,
                optimized.counters().operatorApplications());

        assertEquals(
                probes.length,
                optimized.counters().streamedPasses());

        assertEquals(
                (long) probes.length * observations.length,
                optimized.counters().observations());
    }

    @Test
    void invalidInputsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StreamingMeanCenteredCovarianceOperator(
                        2,
                        consumer -> {
                        },
                        new double[] {0.0},
                        1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> new StreamingMeanCenteredCovarianceOperator(
                        2,
                        consumer -> {
                        },
                        new double[] {0.0, Double.NaN},
                        1.0));

        StreamingMeanCenteredCovarianceOperator operator =
                new StreamingMeanCenteredCovarianceOperator(
                        2,
                        consumer -> consumer.accept(
                                1.0,
                                new double[] {1.0, 2.0}),
                        new double[] {0.0, 0.0},
                        1.0);

        assertThrows(
                IllegalArgumentException.class,
                () -> operator.apply(
                        new double[] {1.0}));

        assertThrows(
                IllegalArgumentException.class,
                () -> operator.apply(
                        new double[] {1.0, Double.NaN}));
    }
}