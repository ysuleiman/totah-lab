package totah.lab.prometheus.potential.hybrid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.force.FermiNetNuclearForceValidation;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceEstimatorType;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceResult;
import totah.lab.prometheus.potential.PotentialEvaluation;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.baseline.AmberBaselinePotential;
import totah.lab.prometheus.potential.delta.basis.TwoBodyBasis;
import totah.lab.prometheus.potential.delta.environment.LocalEnvironment;
import totah.lab.prometheus.potential.delta.environment.SpeciesChannel;
import totah.lab.prometheus.potential.delta.model.DeltaModelIdentity;
import totah.lab.prometheus.potential.delta.model.DeltaModelParameters;
import totah.lab.prometheus.potential.delta.model.LinearDeltaModel;

/**
 * Adversarial invariance acceptance tests D8, D9, D10, D12 over a fitted
 * hybrid potential.
 *
 * <p>Fixture: a bent triatomic (water-like) environment
 * [O_ETHER, H_C, H_C] with graph distances O–H = 1 and H–H = 2; two active
 * two-body channels (O_ETHER,H_C,ONE_TWO) and (H_C,H_C,ONE_THREE); baseline
 * and delta are two {@link LinearDeltaModel}s over the shared production
 * basis with hand-set, all-nonzero coefficient vectors. Coordinates are in
 * the potential's declared unit (angstrom); every pair distance is below the
 * 4.5 angstrom cutoff so every channel contributes, and the base geometry is
 * chosen with all nine force components nonzero (no symmetric cancellation).
 *
 * <p>Rigid transforms: t = (7.3, −2.1, 11.0); R = Rot(axis (1,2,3)/√14,
 * 37°), non-axis-aligned so every component mixes. The 3×3 rotation helper is
 * written here (Rodrigues' formula), independent of any production code.
 */
final class AdversarialInvarianceAcceptanceTest {

    /** Hand-set delta coefficients (channel 0 then channel 1, T0..T3 each). */
    private static final double[] DELTA_COEFFICIENTS =
            {1.0, 0.5, -0.25, 0.125, 0.3, -0.15, 0.07, 0.02};
    /** Hand-set baseline coefficients, distinct from the delta's. */
    private static final double[] BASELINE_COEFFICIENTS =
            {0.7, -0.3, 0.11, 0.05, -0.2, 0.4, 0.09, -0.06};

    private static final double[][] BASE_GEOMETRY = {
            {0.1, -0.2, 0.3}, {1.9, 0.4, -0.7}, {-0.5, 1.8, 0.9}};

    private static final double[] TRANSLATION = {7.3, -2.1, 11.0};

    /** Coordinate-scale-aware tolerance for rigid-transform round-off. */
    private static final double TRANSFORM_TOLERANCE = 1.0e-12;

    /**
     * TEST_ID: D9/D10 — translation and rotation invariance.
     *
     * <p>Oracle: E(Rx + t) = E(x) to machine precision; forces are covariant:
     * F(Rx + t) = R·F(x) to 1e-12 (asserting F′ = F under rotation would
     * itself be a bug); total force and total torque about the origin vanish
     * to round-off (≲1e-14) in both frames — per-pair gradients are exact
     * negations, but the per-atom accumulation across pairs leaves a ~1e-17
     * floating-point residue, so the assertion uses a round-off tolerance
     * rather than bit-exact zero.
     */
    @Test
    void d9d10EnergyInvariantForcesCovariantAndConserved() {
        HybridPotential potential = hybridPotential();
        PotentialEvaluation base = potential.evaluate(new QuantumCoordinates(BASE_GEOMETRY));

        double[][] r = rotationMatrix();
        double[][] transformed = new double[BASE_GEOMETRY.length][3];
        for (int atom = 0; atom < BASE_GEOMETRY.length; atom++) {
            double[] rotated = multiply(r, BASE_GEOMETRY[atom]);
            for (int axis = 0; axis < 3; axis++) {
                transformed[atom][axis] = rotated[axis] + TRANSLATION[axis];
            }
        }
        PotentialEvaluation moved = potential.evaluate(new QuantumCoordinates(transformed));

        double energyScale = Math.max(1.0, Math.abs(base.energy()));
        assertEquals(base.energy(), moved.energy(), 1.0e-12 * energyScale,
                "E(Rx + t) = E(x) to machine precision");
        for (int atom = 0; atom < BASE_GEOMETRY.length; atom++) {
            double[] expectedForce = multiply(r, base.forces()[atom]);
            for (int axis = 0; axis < 3; axis++) {
                assertEquals(expectedForce[axis], moved.forces()[atom][axis],
                        TRANSFORM_TOLERANCE,
                        "forces are covariant: F(Rx + t) = R·F(x)");
            }
        }

        double[] totalBase = totalForce(base.forces());
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(0.0, totalBase[axis], 1.0e-14,
                    "total force vanishes to round-off in the base frame"
                            + " (per-pair gradients are exact negations, but the"
                            + " per-atom accumulation order across pairs leaves"
                            + " ~1e-17 residue; measured 2.3e-17)");
        }
        double[] totalMoved = totalForce(moved.forces());
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(0.0, totalMoved[axis], TRANSFORM_TOLERANCE,
                    "total force vanishes in the rotated/translated frame");
        }

        double[] torqueBase = totalTorque(BASE_GEOMETRY, base.forces());
        double[] torqueMoved = totalTorque(transformed, moved.forces());
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(0.0, torqueBase[axis], TRANSFORM_TOLERANCE,
                    "torque-free base frame");
            assertEquals(0.0, torqueMoved[axis], 1.0e-11,
                    "torque-free rotated/translated frame"
                            + " (torque scale includes |t|·|F| round-off)");
        }

        for (int atom = 0; atom < BASE_GEOMETRY.length; atom++) {
            for (int axis = 0; axis < 3; axis++) {
                assertNotEquals(0.0, base.forces()[atom][axis],
                        "fixture discipline: no zero force component in the base frame");
            }
        }
    }

    /**
     * TEST_ID: D12 — permutation invariance for chemically identical atoms.
     *
     * <p>Fixture: swap the indices and coordinates of the two H_C atoms. The
     * production basis sorts each pair's species channels and accumulates each
     * atom's gradient per pair, so the swap leaves every feature value
     * identical (pair (0,1) and (0,2) contributions exchange places; IEEE
     * addition is commutative, so the per-feature sums are bit-identical) and
     * exchanges the two hydrogens' gradient rows exactly. Oracle: E is
     * bit-identical and F is the exact permutation. Second fixture: swap the
     * O and H coordinates (invalid chemistry — the typed atom map no longer
     * matches the geometry): E must change; identical E is forbidden.
     */
    @Test
    void d12IdenticalAtomSwapPreservesEnergyAndPermutesForces() {
        HybridPotential potential = hybridPotential();
        PotentialEvaluation base = potential.evaluate(new QuantumCoordinates(BASE_GEOMETRY));

        double[][] hydrogenSwapped = {
                BASE_GEOMETRY[0], BASE_GEOMETRY[2], BASE_GEOMETRY[1]};
        PotentialEvaluation swapped =
                potential.evaluate(new QuantumCoordinates(hydrogenSwapped));
        assertEquals(Double.doubleToRawLongBits(base.energy()),
                Double.doubleToRawLongBits(swapped.energy()),
                "exchanging the two H atoms leaves E bit-identical");
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(Double.doubleToRawLongBits(base.forces()[0][axis]),
                    Double.doubleToRawLongBits(swapped.forces()[0][axis]));
            assertEquals(Double.doubleToRawLongBits(base.forces()[1][axis]),
                    Double.doubleToRawLongBits(swapped.forces()[2][axis]),
                    "the swap permutes the force rows");
            assertEquals(Double.doubleToRawLongBits(base.forces()[2][axis]),
                    Double.doubleToRawLongBits(swapped.forces()[1][axis]));
        }

        double[][] chemistryBroken = {
                BASE_GEOMETRY[1], BASE_GEOMETRY[0], BASE_GEOMETRY[2]};
        PotentialEvaluation broken =
                potential.evaluate(new QuantumCoordinates(chemistryBroken));
        assertNotEquals(0.0, Math.abs(broken.energy() - base.energy()),
                "swapping O and H coordinates under a fixed typed atom map is a"
                        + " different molecule: E must change (or be rejected)");
        assertTrue(Math.abs(broken.energy() - base.energy()) > 1.0e-9,
                "the energy change is material, not round-off: |ΔE| = "
                        + Math.abs(broken.energy() - base.energy()));
    }

    /**
     * TEST_ID: D8 — planarity test must be rotation-free.
     *
     * <p>Seam: {@link FermiNetNuclearForceValidation#validate}, the only
     * planarity/geometry check in the validation/potential tree
     * (grep-verified; the potential basis code is distance-based and has no
     * planarity notion). Fixture: a <em>four</em>-atom molecule exactly in
     * the XY plane — three atoms would be trivially coplanar — and the same
     * molecule rotated by R. Oracle: identical planarity verdict and
     * identical maximum absolute out-of-plane force in both orientations; a
     * genuinely non-planar four-atom control stays non-planar in both
     * orientations.
     */
    @Test
    void d8PlanarityVerdictIsInvariantUnderRotation() {
        double[][] planarXy = {
                {0.0, 0.0, 0.0}, {1.9, 0.0, 0.0}, {0.4, 1.7, 0.0}, {1.2, 0.8, 0.0}};
        double[][] planarForces = {
                {0.03, -0.01, 0.0}, {-0.02, 0.04, 0.0},
                {-0.01, -0.03, 0.0}, {0.0, 0.0, 0.0}};

        double[][] r = rotationMatrix();
        double[][] planarRotated = rotateAll(r, planarXy);
        double[][] forcesRotated = rotateAll(r, planarForces);

        var base = FermiNetNuclearForceValidation.validate(
                tetraAtomic(planarXy), forceResult(planarForces));
        var rotated = FermiNetNuclearForceValidation.validate(
                tetraAtomic(planarRotated), forceResult(forcesRotated));
        assertTrue(base.planarGeometry(), "control: the fixture is planar");
        assertEquals(base.planarGeometry(), rotated.planarGeometry(),
                "planarity is a geometric property, not a coordinate-system property");
        assertEquals(base.maximumAbsoluteOutOfPlaneForce(),
                rotated.maximumAbsoluteOutOfPlaneForce(), TRANSFORM_TOLERANCE,
                "out-of-plane force is rotation-covariant through the plane normal");

        double[][] bent = {
                {0.0, 0.0, 0.0}, {1.9, 0.0, 0.0}, {0.4, 1.7, 0.0}, {1.2, 0.8, 0.6}};
        var bentBase = FermiNetNuclearForceValidation.validate(
                tetraAtomic(bent), forceResult(planarForces));
        var bentRotated = FermiNetNuclearForceValidation.validate(
                tetraAtomic(rotateAll(r, bent)), forceResult(forcesRotated));
        assertFalse(bentBase.planarGeometry(),
                "control: the fourth atom 0.6 bohr off the plane is not planar");
        assertEquals(bentBase.planarGeometry(), bentRotated.planarGeometry(),
                "a non-planar molecule stays non-planar under rotation");
    }

    private static HybridPotential hybridPotential() {
        LocalEnvironment environment = new LocalEnvironment(
                new SpeciesChannel[]{SpeciesChannel.O_ETHER,
                        SpeciesChannel.H_C, SpeciesChannel.H_C},
                new int[][]{{0, 1, 1}, {1, 0, 2}, {1, 2, 0}});
        TwoBodyBasis basis = new TwoBodyBasis(environment, List.of(
                new TwoBodyBasis.Channel(SpeciesChannel.O_ETHER,
                        SpeciesChannel.H_C, TwoBodyBasis.TopologyClass.ONE_TWO),
                new TwoBodyBasis.Channel(SpeciesChannel.H_C,
                        SpeciesChannel.H_C, TwoBodyBasis.TopologyClass.ONE_THREE)));
        LinearDeltaModel baselineCore = new LinearDeltaModel(basis,
                new DeltaModelParameters(BASELINE_COEFFICIENTS), identity("baseline"));
        LinearDeltaModel delta = new LinearDeltaModel(basis,
                new DeltaModelParameters(DELTA_COEFFICIENTS), identity("delta"));
        return new HybridPotential(new AmberBaselinePotential(baselineCore), delta);
    }

    private static DeltaModelIdentity identity(String tag) {
        return new DeltaModelIdentity(
                "adversarial-invariance-" + tag, "basis-checksum",
                "chemical-types-checksum", "training-manifest-checksum",
                "software-identity");
    }

    /** Rodrigues' formula for Rot(axis (1,2,3)/√14, 37°); test-owned. */
    private static double[][] rotationMatrix() {
        double norm = Math.sqrt(14.0);
        double x = 1.0 / norm, y = 2.0 / norm, z = 3.0 / norm;
        double c = Math.cos(Math.toRadians(37.0));
        double s = Math.sin(Math.toRadians(37.0));
        double k = 1.0 - c;
        return new double[][]{
                {x * x * k + c, x * y * k - z * s, x * z * k + y * s},
                {y * x * k + z * s, y * y * k + c, y * z * k - x * s},
                {z * x * k - y * s, z * y * k + x * s, z * z * k + c}};
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] out = new double[3];
        for (int row = 0; row < 3; row++) {
            for (int k = 0; k < 3; k++) {
                out[row] += matrix[row][k] * vector[k];
            }
        }
        return out;
    }

    private static double[][] rotateAll(double[][] matrix, double[][] vectors) {
        double[][] out = new double[vectors.length][3];
        for (int i = 0; i < vectors.length; i++) {
            out[i] = multiply(matrix, vectors[i]);
        }
        return out;
    }

    private static double[] totalForce(double[][] forces) {
        double[] total = new double[3];
        for (double[] force : forces) {
            for (int axis = 0; axis < 3; axis++) {
                total[axis] += force[axis];
            }
        }
        return total;
    }

    /** Total torque about the origin: Σ r_i × F_i. */
    private static double[] totalTorque(double[][] positions, double[][] forces) {
        double[] torque = new double[3];
        for (int i = 0; i < positions.length; i++) {
            torque[0] += positions[i][1] * forces[i][2] - positions[i][2] * forces[i][1];
            torque[1] += positions[i][2] * forces[i][0] - positions[i][0] * forces[i][2];
            torque[2] += positions[i][0] * forces[i][1] - positions[i][1] * forces[i][0];
        }
        return torque;
    }

    private static Molecule tetraAtomic(double[][] positionsBohr) {
        List<NuclearCenter> nuclei = new ArrayList<>();
        for (int i = 0; i < positionsBohr.length; i++) {
            nuclei.add(new NuclearCenter(i, "H", new NuclearCharge(1),
                    new CartesianPosition(positionsBohr[i][0], positionsBohr[i][1],
                            positionsBohr[i][2], LengthUnit.BOHR)));
        }
        return new Molecule("adversarial-d8-h4", nuclei,
                new MolecularCharge(0), new ElectronCount(4), new SpinSector(2, 2, 1));
    }

    private static NuclearForceResult forceResult(double[][] forces) {
        String[] axisNames = {"x", "y", "z"};
        List<NuclearForceResult.Component> components = new ArrayList<>();
        for (int nucleus = 0; nucleus < forces.length; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                double mean = forces[nucleus][axis];
                components.add(new NuclearForceResult.Component(
                        nucleus, axis, axisNames[axis],
                        mean, 0.001, 1.0e-6, 100, 0,
                        new NuclearForceResult.TailDiagnostics(
                                mean - 0.01, mean - 0.005, mean - 0.002, mean,
                                mean + 0.002, mean + 0.005, mean + 0.01, 0, 0),
                        "d8-component-" + nucleus + "-" + axis,
                        new double[]{mean}));
            }
        }
        return new NuclearForceResult(
                NuclearForceEstimatorType.CORRELATED_FD, "adversarial-d8",
                "parameter-checksum", "geometry-identity", "dataset-checksum",
                "checkpoint-checksum", "estimator-configuration",
                100, 4, 25, List.copyOf(components),
                new NuclearForceResult.CorrelatedFdDiagnostics(1.0e-3, List.of()));
    }
}
