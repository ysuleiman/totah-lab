package totah.lab.euclid.spatial;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class RigidSuperpositionTest {

    private static final double TOLERANCE = 1.0e-8;

    private static final List<double[]> REFERENCE = List.of(
            new double[] {0.0, 0.0, 0.0},
            new double[] {1.0, 0.0, 0.0},
            new double[] {0.0, 2.0, 0.0},
            new double[] {0.0, 0.0, 3.0},
            new double[] {1.0, 1.0, 1.0});

    @Test
    void identicalSetsShouldHaveZeroRmsd() {
        RigidSuperposition fit =
                RigidSuperposition.fit(REFERENCE, copyOf(REFERENCE));

        assertThat(fit.rmsd()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(fit.pointCount()).isEqualTo(REFERENCE.size());
    }

    @Test
    void translatedSetShouldRecoverTranslation() {
        double[] shift = {5.0, -3.0, 2.0};
        List<double[]> moved = translate(REFERENCE, shift);

        RigidSuperposition fit = RigidSuperposition.fit(REFERENCE, moved);

        assertThat(fit.rmsd()).isCloseTo(0.0, within(TOLERANCE));
        // The transform maps mobile onto reference, undoing the shift.
        assertThat(fit.translation())
                .containsExactly(
                        new double[] {-5.0, 3.0, -2.0},
                        within(TOLERANCE));
        for (int i = 0; i < REFERENCE.size(); i++) {
            assertThat(fit.apply(moved.get(i)))
                    .containsExactly(REFERENCE.get(i), within(TOLERANCE));
        }
    }

    @Test
    void knownRotationShouldBeUndone() {
        // 90 degrees about the z-axis, plus a translation.
        double[] shift = {1.0, 2.0, 3.0};
        List<double[]> moved = new ArrayList<>();
        for (double[] point : REFERENCE) {
            moved.add(new double[] {
                    -point[1] + shift[0],
                    point[0] + shift[1],
                    point[2] + shift[2]});
        }

        RigidSuperposition fit = RigidSuperposition.fit(REFERENCE, moved);

        assertThat(fit.rmsd()).isCloseTo(0.0, within(TOLERANCE));
        for (int i = 0; i < REFERENCE.size(); i++) {
            assertThat(fit.apply(moved.get(i)))
                    .containsExactly(REFERENCE.get(i), within(TOLERANCE));
        }
    }

    @Test
    void arbitraryRotationShouldGiveZeroRmsd() {
        // 30 degrees about the axis (1, 1, 1)/sqrt(3).
        double angle = Math.toRadians(30.0);
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double u = 1.0 / Math.sqrt(3.0);
        double[][] rotation = {
                {c + u * u * (1 - c), u * u * (1 - c) - u * s, u * u * (1 - c) + u * s},
                {u * u * (1 - c) + u * s, c + u * u * (1 - c), u * u * (1 - c) - u * s},
                {u * u * (1 - c) - u * s, u * u * (1 - c) + u * s, c + u * u * (1 - c)}
        };
        double[] shift = {-2.0, 4.0, 1.0};
        List<double[]> moved = new ArrayList<>();
        for (double[] point : REFERENCE) {
            double[] rotated = new double[3];
            for (int row = 0; row < 3; row++) {
                rotated[row] = rotation[row][0] * point[0]
                        + rotation[row][1] * point[1]
                        + rotation[row][2] * point[2]
                        + shift[row];
            }
            moved.add(rotated);
        }

        RigidSuperposition fit = RigidSuperposition.fit(REFERENCE, moved);

        assertThat(fit.rmsd()).isCloseTo(0.0, within(TOLERANCE));
        for (int i = 0; i < REFERENCE.size(); i++) {
            assertThat(fit.apply(moved.get(i)))
                    .containsExactly(REFERENCE.get(i), within(1.0e-8));
        }
    }

    @Test
    void noisySetShouldHaveRmsdWithinNoiseLevel() {
        List<double[]> noisy = List.of(
                new double[] {0.05, -0.02, 0.01},
                new double[] {1.03, 0.04, -0.02},
                new double[] {-0.01, 1.97, 0.03},
                new double[] {0.02, 0.01, 3.04},
                new double[] {0.96, 1.02, 0.98});

        double rmsd = RigidSuperposition.rmsd(REFERENCE, noisy);

        assertThat(rmsd).isGreaterThan(0.0);
        assertThat(rmsd).isLessThan(0.1);
    }

    @Test
    void singlePointShouldYieldPureTranslation() {
        List<double[]> reference = List.of(new double[] {1.0, 2.0, 3.0});
        List<double[]> mobile = List.of(new double[] {4.0, 0.0, -1.0});

        RigidSuperposition fit = RigidSuperposition.fit(reference, mobile);

        assertThat(fit.rmsd()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(fit.translation())
                .containsExactly(new double[] {-3.0, 2.0, 4.0}, within(TOLERANCE));
    }

    @Test
    void twoPointsShouldBeHandledGracefully() {
        List<double[]> reference = List.of(
                new double[] {0.0, 0.0, 0.0},
                new double[] {2.0, 0.0, 0.0});
        List<double[]> mobile = List.of(
                new double[] {1.0, 1.0, 0.0},
                new double[] {1.0, -1.0, 0.0});

        RigidSuperposition fit = RigidSuperposition.fit(reference, mobile);

        assertThat(fit.rmsd()).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void mismatchedSizesShouldBeRejected() {
        List<double[]> shortList = List.of(new double[] {0.0, 0.0, 0.0});

        assertThatThrownBy(() -> RigidSuperposition.fit(REFERENCE, shortList))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptySetsShouldBeRejected() {
        assertThatThrownBy(() -> RigidSuperposition.fit(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedCoordinatesShouldBeRejected() {
        List<double[]> bad = List.of(new double[] {1.0, 2.0});

        assertThatThrownBy(() -> RigidSuperposition.fit(bad, bad))
                .isInstanceOf(IllegalArgumentException.class);

        List<double[]> nan = List.of(
                new double[] {Double.NaN, 0.0, 0.0});
        assertThatThrownBy(() -> RigidSuperposition.fit(nan, nan))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputsShouldBeRejected() {
        assertThatThrownBy(() -> RigidSuperposition.fit(null, REFERENCE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RigidSuperposition.fit(REFERENCE, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static List<double[]> copyOf(List<double[]> points) {
        List<double[]> copy = new ArrayList<>();
        for (double[] point : points) {
            copy.add(point.clone());
        }
        return copy;
    }

    private static List<double[]> translate(List<double[]> points, double[] shift) {
        List<double[]> moved = new ArrayList<>();
        for (double[] point : points) {
            moved.add(new double[] {
                    point[0] + shift[0],
                    point[1] + shift[1],
                    point[2] + shift[2]});
        }
        return moved;
    }
}
