package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.force.GeneralMolecularSpaceWarp;

/**
 * Analytic-oracle falsification tests for the package-private static seams of
 * {@link SwctFermiNetForceEstimator} (lines 444-621). The fixture is
 * deliberately asymmetric: three nuclei with different charges (H, He, C) at
 * non-collinear positions and three electrons at asymmetric positions, so no
 * term cancels trivially and every warp weight is distinct.
 */
final class SwctAnalyticOracleTest {

    // Nuclei: H (Z=1), He (Z=2), C (Z=6) at non-collinear positions, bohr.
    private static final double[] H = {0.0, 0.0, 0.0};
    private static final double[] HE = {1.4, 0.3, 0.0};
    private static final double[] C = {0.2, 1.1, 0.7};
    private static final double[][] NUCLEI = {H, HE, C};
    private static final int[] CHARGES = {1, 2, 6};
    private static final String[] ELEMENTS = {"H", "He", "C"};

    // Electrons at asymmetric positions, bohr.
    private static final double[] E0 = {0.5, -0.4, 0.9};
    private static final double[] E1 = {-0.8, 0.6, 0.2};
    private static final double[] E2 = {1.1, 0.9, -0.5};
    private static final double[][] ELECTRONS = {E0, E1, E2};

    /** ORACLE-SWCT-01: V matches explicit per-term hand arithmetic, 1e-12. */
    @Test
    void coulombPotentialMatchesHandComputedTermwiseSum() {
        double enE0H = -1.0 / distance(E0, H);
        double enE0He = -2.0 / distance(E0, HE);
        double enE0C = -6.0 / distance(E0, C);
        double enE1H = -1.0 / distance(E1, H);
        double enE1He = -2.0 / distance(E1, HE);
        double enE1C = -6.0 / distance(E1, C);
        double enE2H = -1.0 / distance(E2, H);
        double enE2He = -2.0 / distance(E2, HE);
        double enE2C = -6.0 / distance(E2, C);
        double eeE0E1 = 1.0 / distance(E0, E1);
        double eeE0E2 = 1.0 / distance(E0, E2);
        double eeE1E2 = 1.0 / distance(E1, E2);
        double nnHHe = 1.0 * 2.0 / distance(H, HE);
        double nnHC = 1.0 * 6.0 / distance(H, C);
        double nnHeC = 2.0 * 6.0 / distance(HE, C);
        double expected = enE0H + enE0He + enE0C
                + enE1H + enE1He + enE1C
                + enE2H + enE2He + enE2C
                + eeE0E1 + eeE0E2 + eeE1E2
                + nnHHe + nnHC + nnHeC;

        assertThat(SwctFermiNetForceEstimator.coulombPotential(
                molecule(), coordinates(ELECTRONS)))
                .isCloseTo(expected, within(1.0e-12));
    }

    /**
     * ORACLE-SWCT-02: an electron exactly on a nucleus must fail closed via
     * the distance() guard (SwctFermiNetForceEstimator.java ~line 606).
     */
    @Test
    void coulombPotentialFailsClosedAtElectronNucleusCoalescence() {
        QuantumCoordinates coincident = coordinates(
                new double[][] {HE, E1, E2});

        assertThatThrownBy(() -> SwctFermiNetForceEstimator.coulombPotential(
                molecule(), coincident))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Coulomb singularity at coincident charged particles");
    }

    /**
     * ORACLE-SWCT-03: the analytic directional derivative (bare nuclear part
     * plus warp-weighted electron part) matches an independent warped central
     * finite difference of coulombPotential, h = 1e-5.
     */
    @Test
    void coulombDirectionalDerivativeMatchesWarpedFiniteDifferenceOfPotential() {
        Molecule base = molecule();
        QuantumCoordinates baseCoordinates = coordinates(ELECTRONS);
        var weights = SwctFermiNetForceEstimator.warpWeights(
                base, baseCoordinates);
        double h = 1.0e-5;
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            double[] electronWeights = perElectronWeights(weights, nucleus);
            for (int axis = 0; axis < 3; axis++) {
                double plus = SwctFermiNetForceEstimator.coulombPotential(
                        displacedMolecule(nucleus, axis, h),
                        displacedCoordinates(axis, electronWeights, h));
                double minus = SwctFermiNetForceEstimator.coulombPotential(
                        displacedMolecule(nucleus, axis, -h),
                        displacedCoordinates(axis, electronWeights, -h));
                double finiteDifference = (plus - minus) / (2.0 * h);
                double analytic = SwctFermiNetForceEstimator
                        .coulombDirectionalDerivative(base, baseCoordinates,
                                nucleus, axis, electronWeights);
                assertThat(analytic)
                        .as("coulombDirectionalDerivative nucleus %d axis %d",
                                nucleus, axis)
                        .isCloseTo(finiteDifference,
                                within(1.0e-8 + 1.0e-7 * Math.abs(
                                        finiteDifference)));
            }
        }
    }

    /**
     * ORACLE-SWCT-04: warp weights are normalized per electron and equal the
     * hand-computed r^-4 ratio r_iA^-4 / sum_B r_iB^-4.
     */
    @Test
    void warpWeightsAreNormalizedAndMatchHandComputedInverseFourthPower() {
        var weights = SwctFermiNetForceEstimator.warpWeights(
                molecule(), coordinates(ELECTRONS));
        for (int electron = 0; electron < ELECTRONS.length; electron++) {
            double sum = 0.0;
            for (int nucleus = 0; nucleus < NUCLEI.length; nucleus++) {
                sum += weights[electron][nucleus].value();
                assertThat(weights[electron][nucleus].value())
                        .as("weight electron %d nucleus %d", electron, nucleus)
                        .isCloseTo(handWeight(ELECTRONS[electron], nucleus),
                                within(1.0e-14));
            }
            assertThat(sum)
                    .as("weight normalization electron %d", electron)
                    .isCloseTo(1.0, within(1.0e-12));
        }
    }

    /**
     * ORACLE-SWCT-05: translational invariance - the total derivative of V
     * under a uniform translation of the whole system vanishes, so the
     * per-axis sum over all nuclei of the directional derivative (with
     * normalized warp weights) must be zero to 1e-10.
     */
    @Test
    void coulombDirectionalDerivativeSumsToZeroOverNucleiPerAxis() {
        Molecule molecule = molecule();
        QuantumCoordinates coordinates = coordinates(ELECTRONS);
        var weights = SwctFermiNetForceEstimator.warpWeights(
                molecule, coordinates);
        for (int axis = 0; axis < 3; axis++) {
            double total = 0.0;
            for (int nucleus = 0; nucleus < 3; nucleus++) {
                total += SwctFermiNetForceEstimator.coulombDirectionalDerivative(
                        molecule, coordinates, nucleus, axis,
                        perElectronWeights(weights, nucleus));
            }
            assertThat(total)
                    .as("translational invariance axis %d", axis)
                    .isCloseTo(0.0, within(1.0e-10));
        }
    }

    /**
     * ORACLE-SWCT-06: electronDirection places each warp weight at slot
     * 3*electron+axis and zero everywhere else.
     */
    @Test
    void electronDirectionPlacesWeightsOnWarpedAxisOnly() {
        var weights = SwctFermiNetForceEstimator.warpWeights(
                molecule(), coordinates(ELECTRONS));
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                double[] direction = SwctFermiNetForceEstimator
                        .electronDirection(weights, nucleus, axis);
                assertThat(direction).hasSize(3 * ELECTRONS.length);
                for (int electron = 0; electron < ELECTRONS.length; electron++) {
                    for (int component = 0; component < 3; component++) {
                        double expected = component == axis
                                ? weights[electron][nucleus].value() : 0.0;
                        assertThat(direction[3 * electron + component])
                                .as("direction nucleus %d axis %d electron %d "
                                                + "component %d",
                                        nucleus, axis, electron, component)
                                .isEqualTo(expected);
                    }
                }
            }
        }
    }

    /**
     * ORACLE-SWCT-07: halfWarpDivergence matches the hand-computed quotient
     * rule of the r^-4 weight formula for every nucleus/axis, and one weight
     * derivative is cross-checked by finite difference of
     * GeneralMolecularSpaceWarp.weight w.r.t. the electron coordinate.
     */
    @Test
    void halfWarpDivergenceMatchesHandComputedQuotientRule() {
        Molecule molecule = molecule();
        QuantumCoordinates coordinates = coordinates(ELECTRONS);
        var weights = SwctFermiNetForceEstimator.warpWeights(
                molecule, coordinates);
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                double hand = 0.0;
                for (double[] electron : ELECTRONS) {
                    hand += handWeightDerivative(electron, nucleus, axis);
                }
                hand *= 0.5;
                assertThat(SwctFermiNetForceEstimator.halfWarpDivergence(
                        weights, nucleus, axis))
                        .as("halfWarpDivergence nucleus %d axis %d",
                                nucleus, axis)
                        .isCloseTo(hand, within(1.0e-12));
            }
        }

        // Independent FD cross-check of one derivative value: d w_1^C / d y_1.
        double h = 1.0e-5;
        int electron = 1, nucleus = 2, axis = 1;
        double analytic = GeneralMolecularSpaceWarp.weight(molecule,
                coordinates.particles().get(electron), nucleus).derivative(axis);
        double[] displaced = ELECTRONS[electron].clone();
        displaced[axis] += h;
        double plus = GeneralMolecularSpaceWarp.weight(molecule,
                particle(electron, displaced), nucleus).value();
        displaced[axis] -= 2.0 * h;
        double minus = GeneralMolecularSpaceWarp.weight(molecule,
                particle(electron, displaced), nucleus).value();
        double finiteDifference = (plus - minus) / (2.0 * h);
        assertThat(analytic).isCloseTo(finiteDifference, within(1.0e-7));
        // And the production seam agrees with the same FD through the sum.
        double seam = SwctFermiNetForceEstimator.halfWarpDivergence(
                weights, nucleus, axis);
        double handPartial = 0.0;
        for (int i = 0; i < ELECTRONS.length; i++) {
            if (i != electron) {
                handPartial += weights[i][nucleus].derivative(axis);
            }
        }
        assertThat(seam)
                .isCloseTo(0.5 * (handPartial + finiteDifference),
                        within(1.0e-7));
    }

    /** ORACLE-SWCT-08: forceSample matches the literal locked formula. */
    @Test
    void forceSampleMatchesLiteralFormulaOnAsymmetricInputs() {
        double localEnergy = -1.3731;
        double meanLocalEnergy = -1.4207;
        double directionalLocalEnergy = 0.5311;
        double directionalLogJacobianState = -0.2087;
        double expected = -directionalLocalEnergy
                - 2.0 * (localEnergy - meanLocalEnergy)
                        * directionalLogJacobianState;
        assertThat(SwctFermiNetForceEstimator.forceSample(
                localEnergy, meanLocalEnergy, directionalLocalEnergy,
                directionalLogJacobianState))
                .isCloseTo(expected, within(1.0e-15));
    }

    /** ORACLE-SWCT-09: E_L == <E_L> reduces forceSample to -D E_L exactly. */
    @Test
    void forceSampleReducesToMinusDirectionalWhenLocalEnergyEqualsMean() {
        double directionalLocalEnergy = 0.5311;
        assertThat(SwctFermiNetForceEstimator.forceSample(
                -1.4207, -1.4207, directionalLocalEnergy, -0.2087))
                .isEqualTo(-directionalLocalEnergy);
    }

    /** ORACLE-SWCT-10: both directionals zero gives a zero force sample. */
    @Test
    void forceSampleIsZeroWhenBothDirectionalsAreZero() {
        assertThat(SwctFermiNetForceEstimator.forceSample(
                -1.3731, -1.4207, 0.0, 0.0))
                .isEqualTo(0.0, within(0.0));
    }

    /**
     * ORACLE-SWCT-11 (guard-consistency probe): at exact electron-nucleus
     * coalescence, coulombPotential fails closed through distance() while
     * coulombDirectionalDerivative relies on cubeDistance()
     * (SwctFermiNetForceEstimator.java ~line 597), which has NO singularity
     * guard and yields 0/0. This test characterizes the observed behavior:
     * the seam silently returns NaN instead of throwing. The inconsistency is
     * contained end to end only because warpWeights ("SWCT singular at
     * nucleus", evaluated first in evaluateSample, lines ~314-325) fails
     * closed before coulombDirectionalDerivative is ever reached; a NaN from
     * this seam would be marked non-finite evidence by evaluateComponent
     * (lines ~281-287) and classify the run SWCT_IMPLEMENTATION_FAILURE.
     */
    @Test
    void coincidentElectronProbeDocumentsGuardAsymmetry() {
        Molecule molecule = molecule();
        QuantumCoordinates coincident = coordinates(
                new double[][] {HE, E1, E2});

        // distance() guard: fail closed with a precise message.
        assertThatThrownBy(() -> SwctFermiNetForceEstimator.coulombPotential(
                molecule, coincident))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Coulomb singularity at coincident charged particles");

        // warpWeights guard: also fails closed, and runs first in
        // evaluateSample, so the unguarded seam below is unreachable from
        // estimate() at exact coalescence.
        assertThatThrownBy(() -> SwctFermiNetForceEstimator.warpWeights(
                molecule, coincident))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SWCT singular at nucleus");

        // cubeDistance() has no guard: the same geometry silently produces
        // NaN here instead of throwing. Lock the observed behavior.
        double silent = SwctFermiNetForceEstimator.coulombDirectionalDerivative(
                molecule, coincident, 1, 0,
                new double[] {1.0 / 3.0, 1.0 / 3.0, 1.0 / 3.0});
        assertThat(silent).isNaN();
    }

    // ------------------------------------------------------------------
    // Fixture and hand-oracle helpers (self-contained for reuse).
    // ------------------------------------------------------------------

    private static Molecule molecule() {
        List<NuclearCenter> centers = new ArrayList<>();
        for (int a = 0; a < NUCLEI.length; a++) {
            centers.add(new NuclearCenter(a, ELEMENTS[a],
                    new NuclearCharge(CHARGES[a]),
                    new CartesianPosition(NUCLEI[a][0], NUCLEI[a][1],
                            NUCLEI[a][2], LengthUnit.BOHR)));
        }
        return new Molecule("swct-oracle-hhec", centers,
                new MolecularCharge(6), new ElectronCount(3),
                new SpinSector(2, 1, 2));
    }

    private static Molecule displacedMolecule(
            int nucleus, int axis, double displacement) {
        List<NuclearCenter> centers = new ArrayList<>();
        for (int a = 0; a < NUCLEI.length; a++) {
            double[] position = NUCLEI[a].clone();
            if (a == nucleus) {
                position[axis] += displacement;
            }
            centers.add(new NuclearCenter(a, ELEMENTS[a],
                    new NuclearCharge(CHARGES[a]),
                    new CartesianPosition(position[0], position[1], position[2],
                            LengthUnit.BOHR)));
        }
        return new Molecule("swct-oracle-hhec", centers,
                new MolecularCharge(6), new ElectronCount(3),
                new SpinSector(2, 1, 2));
    }

    private static QuantumCoordinates coordinates(double[][] electrons) {
        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();
        for (int i = 0; i < electrons.length; i++) {
            particles.add(particle(i, electrons[i]));
        }
        return new QuantumCoordinates(particles);
    }

    private static QuantumCoordinates.ParticleCoordinate particle(
            int index, double[] position) {
        return new QuantumCoordinates.ParticleCoordinate(index, position[0],
                position[1], position[2],
                index == 1 ? SpinProjection.BETA : SpinProjection.ALPHA);
    }

    private static QuantumCoordinates displacedCoordinates(
            int axis, double[] electronWeights, double displacement) {
        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();
        for (int i = 0; i < ELECTRONS.length; i++) {
            double[] position = ELECTRONS[i].clone();
            position[axis] += displacement * electronWeights[i];
            particles.add(particle(i, position));
        }
        return new QuantumCoordinates(particles);
    }

    private static double[] perElectronWeights(
            GeneralMolecularSpaceWarp.Weight[][] weights, int nucleus) {
        double[] values = new double[weights.length];
        for (int electron = 0; electron < weights.length; electron++) {
            values[electron] = weights[electron][nucleus].value();
        }
        return values;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Hand-computed normalized r^-4 weight: f_n / sum_a f_a, f_a = r_a^-4. */
    private static double handWeight(double[] electron, int nucleus) {
        double selected = 0.0, sum = 0.0;
        for (int a = 0; a < NUCLEI.length; a++) {
            double dx = electron[0] - NUCLEI[a][0];
            double dy = electron[1] - NUCLEI[a][1];
            double dz = electron[2] - NUCLEI[a][2];
            double r2 = dx * dx + dy * dy + dz * dz;
            double f = 1.0 / (r2 * r2);
            if (a == nucleus) {
                selected = f;
            }
            sum += f;
        }
        return selected / sum;
    }

    /**
     * Hand-computed derivative of the normalized r^-4 weight w.r.t. one
     * electron Cartesian coordinate: f_a = r_a^-4,
     * df_a/dx = -4 (x - X_a) r_a^-6, quotient rule on f_n / sum_a f_a.
     */
    private static double handWeightDerivative(
            double[] electron, int nucleus, int axis) {
        double selected = 0.0, selectedDerivative = 0.0;
        double sum = 0.0, sumDerivative = 0.0;
        for (int a = 0; a < NUCLEI.length; a++) {
            double dx = electron[0] - NUCLEI[a][0];
            double dy = electron[1] - NUCLEI[a][1];
            double dz = electron[2] - NUCLEI[a][2];
            double r2 = dx * dx + dy * dy + dz * dz;
            double f = 1.0 / (r2 * r2);
            double df = -4.0 * (electron[axis] - NUCLEI[a][axis])
                    / (r2 * r2 * r2);
            if (a == nucleus) {
                selected = f;
                selectedDerivative = df;
            }
            sum += f;
            sumDerivative += df;
        }
        return (selectedDerivative * sum - selected * sumDerivative)
                / (sum * sum);
    }
}
