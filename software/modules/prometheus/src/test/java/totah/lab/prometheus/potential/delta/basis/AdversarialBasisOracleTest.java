package totah.lab.prometheus.potential.delta.basis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.potential.PotentialEvaluation;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.delta.DeltaPotential;
import totah.lab.prometheus.potential.delta.basis.FourBodyBasis.Kind;
import totah.lab.prometheus.potential.delta.basis.FourBodyBasis.Motif;
import totah.lab.prometheus.potential.delta.environment.LocalEnvironment;
import totah.lab.prometheus.potential.delta.environment.SpeciesChannel;
import totah.lab.prometheus.potential.delta.model.DeltaModelIdentity;
import totah.lab.prometheus.potential.delta.model.DeltaModelParameters;
import totah.lab.prometheus.potential.delta.model.LinearDeltaModel;

/**
 * Adversarial acceptance tests C5-C7 of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md
 * against the many-body delta basis and {@link LinearDeltaModel}. Oracles are
 * central finite differences (F = -dE/dx cannot be faked by self-consistent
 * wrong code), hand-computed torsion features, and permutation remapping.
 * Fixtures keep every equation term nonzero (non-axis-aligned geometries,
 * mid-switch radii where the cutoff derivative is active, all-nonzero
 * coefficient vectors).
 */
class AdversarialBasisOracleTest {

    private static final double H = 1e-5;
    private static final double FD_TOLERANCE = 1e-6;
    /** Machine-precision bound for the exact 0 / ±1 torsion oracle entries. */
    private static final double EXACT = 1e-15;

    /** Hand-computed torsion chain of the suite: A(0,0,0) B(1,0,0) C(1,1,0) D(1,1,1). */
    private static final double[][] CHAIN = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}};

    /**
     * TEST_ID: C5 (two-body) — FD consistency with the all-nonzero
     * coefficient vector (1, 0.5, -0.25, 0.125) on one pair, at a plain
     * mid-cutoff radius (r ≈ 2.23, switch inactive) and at a mid-switch
     * radius (r ≈ 4.26, switch value AND derivative nonzero so no term of
     * the radial chain rule is masked). Translation: for a single pair the
     * gradient rows are the same double added and subtracted, so the force
     * sum is exactly zero in IEEE arithmetic — asserted exactly, per spec.
     */
    @Test void c5_twoBodyAnalyticForcesMatchFiniteDifferencesAndSumToZeroExactly() {
        LocalEnvironment environment = new LocalEnvironment(
                new SpeciesChannel[] {SpeciesChannel.C_SP3, SpeciesChannel.O_ETHER},
                new int[][] {{0, 1}, {1, 0}});
        TwoBodyBasis basis = new TwoBodyBasis(environment, List.of(
                new TwoBodyBasis.Channel(SpeciesChannel.C_SP3, SpeciesChannel.O_ETHER,
                        TwoBodyBasis.TopologyClass.ONE_TWO)));
        LinearDeltaModel model = new LinearDeltaModel(basis,
                new DeltaModelParameters(new double[] {1.0, 0.5, -0.25, 0.125}), identity());

        for (double[] offset : new double[][] {{2.0, 0.9, -0.4}, {4.0, 1.3, 0.7}}) {
            QuantumCoordinates coordinates = new QuantumCoordinates(
                    new double[][] {{0, 0, 0}, offset});
            PotentialEvaluation evaluated = model.evaluate(coordinates);
            double[][] finiteDifference = finiteDifferenceForces(model, coordinates);
            double scale = forceScale(evaluated.forces());
            for (int atom = 0; atom < 2; atom++) {
                for (int axis = 0; axis < 3; axis++) {
                    assertThat(evaluated.forces()[atom][axis])
                            .as("two-body force atom %d axis %d at offset %s", atom, axis,
                                    java.util.Arrays.toString(offset))
                            .isCloseTo(finiteDifference[atom][axis],
                                    within(FD_TOLERANCE * scale));
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                assertThat(evaluated.forces()[0][axis] + evaluated.forces()[1][axis])
                        .as("exact translation sum, axis %d", axis).isEqualTo(0.0);
            }
        }
    }

    /**
     * TEST_ID: C5 (three-body) — one triplet at ≈97° (no symmetry), both
     * arms at r = 4.25 so cutoff value and derivative are nonzero, Legendre
     * orders 1 and 2 both nonzero (cos 97° ≈ -0.122, P2 ≈ -0.478), all-nonzero
     * coefficients. FD consistency on every coordinate; translation sum to
     * 1e-12 (the centered gradient is assembled as gu+gv then negated, so
     * the sum is not guaranteed bit-exact — the bound is far below any
     * physically meaningful scale error).
     */
    @Test void c5_threeBodyAnalyticForcesMatchFiniteDifferences() {
        LocalEnvironment environment = new LocalEnvironment(
                new SpeciesChannel[] {SpeciesChannel.C_SP3, SpeciesChannel.O_ETHER,
                        SpeciesChannel.S_THIOL},
                new int[][] {{0, 1, 1}, {1, 0, 2}, {1, 2, 0}});
        ThreeBodyBasis basis = new ThreeBodyBasis(environment, List.of(
                new ThreeBodyBasis.Channel(SpeciesChannel.C_SP3,
                        new ThreeBodyBasis.Neighbor(SpeciesChannel.O_ETHER, 1),
                        new ThreeBodyBasis.Neighbor(SpeciesChannel.S_THIOL, 1))));
        LinearDeltaModel model = new LinearDeltaModel(basis,
                new DeltaModelParameters(new double[] {1.0, -0.375}), identity());

        double arm = 4.25;
        double radians = Math.toRadians(97.0);
        QuantumCoordinates coordinates = new QuantumCoordinates(new double[][] {
                {0, 0, 0}, {arm, 0, 0},
                {arm * Math.cos(radians), arm * Math.sin(radians), 0}});
        PotentialEvaluation evaluated = model.evaluate(coordinates);
        double[][] finiteDifference = finiteDifferenceForces(model, coordinates);
        double scale = forceScale(evaluated.forces());
        for (int atom = 0; atom < 3; atom++) {
            for (int axis = 0; axis < 3; axis++) {
                assertThat(evaluated.forces()[atom][axis])
                        .as("three-body force atom %d axis %d", atom, axis)
                        .isCloseTo(finiteDifference[atom][axis], within(FD_TOLERANCE * scale));
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            double sum = evaluated.forces()[0][axis] + evaluated.forces()[1][axis]
                    + evaluated.forces()[2][axis];
            assertThat(Math.abs(sum)).as("translation sum, axis %d", axis).isLessThan(1e-12);
        }
    }

    /**
     * TEST_ID: C5 (four-body) — the C6 torsion geometry fitted by a
     * {@link LinearDeltaModel} over a single TORSION_FOURIER motif with six
     * all-nonzero coefficients; FD consistency on every coordinate of all
     * four atoms, translation sum and total torque below 1e-12.
     */
    @Test void c5_torsionAnalyticForcesMatchFiniteDifferencesAndConserve() {
        LinearDeltaModel model = new LinearDeltaModel(
                new FourBodyBasis(List.of(new Motif(Kind.TORSION_FOURIER, 0, 1, 2, 3))),
                new DeltaModelParameters(
                        new double[] {1.0, 0.5, -0.25, 0.125, 0.0625, -0.03125}),
                identity());
        QuantumCoordinates coordinates = new QuantumCoordinates(CHAIN);
        PotentialEvaluation evaluated = model.evaluate(coordinates);
        double[][] finiteDifference = finiteDifferenceForces(model, coordinates);
        double scale = forceScale(evaluated.forces());
        for (int atom = 0; atom < 4; atom++) {
            for (int axis = 0; axis < 3; axis++) {
                assertThat(evaluated.forces()[atom][axis])
                        .as("torsion force atom %d axis %d", atom, axis)
                        .isCloseTo(finiteDifference[atom][axis], within(FD_TOLERANCE * scale));
            }
        }
        assertConservation(evaluated.forces(), CHAIN);
    }

    /**
     * TEST_ID: C6 (torsion oracle, executable and convention-independent) —
     * a single Motif(TORSION_FOURIER, 0, 1, 2, 3) on the hand-computed chain
     * A(0,0,0) B(1,0,0) C(1,1,0) D(1,1,1). Hand result: b0=(1,0,0),
     * b1=(0,1,0), b2=(0,0,1); n1=(0,0,1), n2=(1,0,0), m1=(-1,0,0);
     * phi = atan2(-1, 0) = -pi/2; features (sin k*phi, cos k*phi) for
     * k = 1..3 are exactly (-1, 0, 0, -1, 1, 0). Asserted to machine
     * precision (1e-15 absolute, tight on the 0 entries). Gradient-vs-FD
     * agreement is asserted on the same geometry for every feature and
     * every coordinate.
     */
    @Test void c6_torsionFeaturesMatchHandComputedValuesAndGradientsMatchFd() {
        FourBodyBasis basis = new FourBodyBasis(
                List.of(new Motif(Kind.TORSION_FOURIER, 0, 1, 2, 3)));
        QuantumCoordinates coordinates = new QuantumCoordinates(CHAIN);
        BasisEvaluation evaluation = basis.evaluate(coordinates);
        assertThat(evaluation.size()).isEqualTo(6);
        double[] expected = {-1, 0, 0, -1, 1, 0};
        for (int feature = 0; feature < 6; feature++) {
            assertThat(evaluation.value(feature))
                    .as("torsion feature %d (hand oracle)", feature)
                    .isCloseTo(expected[feature], within(EXACT));
        }
        assertBasisGradientsMatchFiniteDifferences(basis, coordinates, evaluation);
    }

    /**
     * TEST_ID: C6 (ANGLE_PAIR) — SPECIFICATION_BLOCKED for angle VALUES. The
     * suite records that no written motif specification states which atom
     * tuples ANGLE_PAIR couples (audit suspect: the implementation computes
     * angle(1,2,3) and angle(1,2,4) — both at vertex 2 sharing arm 1 — while
     * the chemically conventional flanking pair of a chain is angle(1,2,3)
     * and angle(2,3,4)). Until that convention is pinned in writing, no
     * angle-value oracle may be encoded here; AD-vs-FD agreement is
     * convention-blind but still guards sign/factor/chain-rule errors, so
     * this test asserts ONLY gradient-vs-FD consistency on an all-nonzero
     * geometry: A(0,0,0), B(1,0,0), C(2,sqrt(3),0), D(0,1,0) — under the
     * current implementation's convention these are angle ABC = 120°
     * (cos = -1/2) and angle ABD = 45° (cos = sqrt(2)/2), making every
     * Legendre product P_l(x)*P_m(y) for l,m in {1,2} nonzero; the nonzero
     * assertions below are convention-independent fixture discipline proving
     * the FD comparison is not vacuous, not an angle-value oracle.
     */
    @Test void c6_anglePairGradientMatchesFd_valueOracleSpecificationBlocked() {
        FourBodyBasis basis = new FourBodyBasis(
                List.of(new Motif(Kind.ANGLE_PAIR, 0, 1, 2, 3)));
        QuantumCoordinates coordinates = new QuantumCoordinates(new double[][] {
                {0, 0, 0}, {1, 0, 0}, {2, Math.sqrt(3), 0}, {0, 1, 0}});
        BasisEvaluation evaluation = basis.evaluate(coordinates);
        assertThat(evaluation.size()).isEqualTo(4);
        for (int feature = 0; feature < 4; feature++) {
            assertThat(evaluation.value(feature))
                    .as("ANGLE_PAIR feature %d must be nonzero (non-vacuous FD fixture)", feature)
                    .isNotZero();
        }
        assertBasisGradientsMatchFiniteDifferences(basis, coordinates, evaluation);
    }

    /**
     * TEST_ID: C7 — reordering the input atoms with the motif indices
     * remapped leaves feature values unchanged and permutes gradient rows.
     * The C6 chain sits at indices 0..3 with a decoy atom at index 4; the
     * scrambled run places the chain at (4, 2, 0, 1) and the decoy at 3.
     * Values must be bit-identical (same doubles, same operation order);
     * gradient rows must be equal up to the permutation.
     */
    @Test void c7_permutedInputLeavesValuesIdenticalAndPermutesGradientRows() {
        double[] decoy = {3.1, -2.4, 5.7};
        QuantumCoordinates natural = new QuantumCoordinates(new double[][] {
                CHAIN[0], CHAIN[1], CHAIN[2], CHAIN[3], decoy});
        // permutation p: original index -> scrambled index
        int[] p = {4, 2, 0, 1, 3};
        double[][] scrambled = new double[5][3];
        double[][] original = {CHAIN[0], CHAIN[1], CHAIN[2], CHAIN[3], decoy};
        for (int i = 0; i < 5; i++) scrambled[p[i]] = original[i];
        QuantumCoordinates permuted = new QuantumCoordinates(scrambled);

        BasisEvaluation first = new FourBodyBasis(
                List.of(new Motif(Kind.TORSION_FOURIER, 0, 1, 2, 3))).evaluate(natural);
        BasisEvaluation second = new FourBodyBasis(
                List.of(new Motif(Kind.TORSION_FOURIER, 4, 2, 0, 1))).evaluate(permuted);

        assertThat(second.size()).isEqualTo(first.size());
        for (int feature = 0; feature < first.size(); feature++) {
            assertThat(second.value(feature))
                    .as("feature %d invariant under atom reordering", feature)
                    .isEqualTo(first.value(feature));
            for (int atom = 0; atom < 5; atom++) {
                for (int axis = 0; axis < 3; axis++) {
                    assertThat(second.gradient(feature, p[atom], axis))
                            .as("gradient feature %d original atom %d axis %d", feature, atom, axis)
                            .isEqualTo(first.gradient(feature, atom, axis));
                }
            }
        }
    }

    private static DeltaModelIdentity identity() {
        return new DeltaModelIdentity("adversarial-oracle", "basis", "types", "manifest",
                "AdversarialBasisOracleTest");
    }

    private static void assertBasisGradientsMatchFiniteDifferences(
            ManyBodyBasis basis, QuantumCoordinates coordinates, BasisEvaluation evaluation) {
        for (int feature = 0; feature < evaluation.size(); feature++) {
            for (int atom = 0; atom < coordinates.atomCount(); atom++) {
                for (int axis = 0; axis < 3; axis++) {
                    double analytic = evaluation.gradient(feature, atom, axis);
                    int f = feature;
                    double numeric = centralDifference(
                            c -> basis.evaluate(c).value(f), coordinates, atom, axis);
                    assertThat(analytic)
                            .as("basis gradient feature %d atom %d axis %d", feature, atom, axis)
                            .isCloseTo(numeric,
                                    within(FD_TOLERANCE * Math.max(1.0, Math.abs(analytic))));
                }
            }
        }
    }

    private static double[][] finiteDifferenceForces(
            DeltaPotential model, QuantumCoordinates coordinates) {
        double[][] forces = new double[coordinates.atomCount()][3];
        for (int atom = 0; atom < coordinates.atomCount(); atom++) {
            for (int axis = 0; axis < 3; axis++) {
                forces[atom][axis] = -centralDifference(
                        c -> model.evaluate(c).energy(), coordinates, atom, axis);
            }
        }
        return forces;
    }

    private static double centralDifference(
            java.util.function.ToDoubleFunction<QuantumCoordinates> observable,
            QuantumCoordinates coordinates, int atom, int axis) {
        double[][] plus = coordinates.positions();
        double[][] minus = coordinates.positions();
        plus[atom][axis] += H;
        minus[atom][axis] -= H;
        return (observable.applyAsDouble(new QuantumCoordinates(plus))
                - observable.applyAsDouble(new QuantumCoordinates(minus))) / (2 * H);
    }

    private static double forceScale(double[][] forces) {
        double scale = 1.0;
        for (double[] row : forces) for (double value : row) {
            scale = Math.max(scale, Math.abs(value));
        }
        return scale;
    }

    private static void assertConservation(double[][] forces, double[][] positions) {
        double[] sum = new double[3];
        double[] torque = new double[3];
        for (int atom = 0; atom < forces.length; atom++) {
            for (int axis = 0; axis < 3; axis++) sum[axis] += forces[atom][axis];
            double[] r = positions[atom];
            double[] f = forces[atom];
            torque[0] += r[1] * f[2] - r[2] * f[1];
            torque[1] += r[2] * f[0] - r[0] * f[2];
            torque[2] += r[0] * f[1] - r[1] * f[0];
        }
        for (int axis = 0; axis < 3; axis++) {
            assertThat(Math.abs(sum[axis])).as("translation sum axis %d", axis)
                    .isLessThan(1e-12);
            assertThat(Math.abs(torque[axis])).as("torque axis %d", axis).isLessThan(1e-12);
        }
    }
}
