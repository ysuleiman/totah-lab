package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

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
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetPhysicalSingularityException;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Analytic oracles for the public static molecular primitives of
 * {@link AcZvFermiNetForceEstimator} on a deliberately asymmetric
 * three-nucleus geometry (H at the origin, He and C at non-collinear,
 * non-symmetric positions) with three asymmetric electrons: no force, Q, or
 * contraction component is trivially zero, and no cancellation can hide a
 * sign or permutation error.
 */
final class AcZvAnalyticOracleTest {

    private static final double TOLERANCE = 1e-12;

    /** H (Z=1), He (Z=2), C (Z=6) at asymmetric positions, bohr. */
    private static final double[][] NUCLEI = {
            {0.0, 0.0, 0.0},
            {1.4, 0.3, 0.0},
            {0.2, 1.1, 0.7}};
    private static final int[] CHARGES = {1, 2, 6};

    /** Asymmetric electron positions, bohr. */
    private static final double[][] ELECTRONS = {
            {0.9, -0.6, 0.4},
            {-0.8, 0.5, 1.2},
            {0.1, 0.2, -0.9}};

    /** Asymmetric, nonzero made-up electron log-gradient, 1/bohr. */
    private static final double[] LOG_GRADIENT = {
            0.37, -0.29, 0.11,
            0.05, 0.41, -0.23,
            -0.17, 0.31, 0.07};

    /**
     * ANALYTIC-NN-01: every nucleus/axis component must equal the
     * hand-computed Z_A sum_{B!=A} Z_B (R_A - R_B)_axis / |R_A - R_B|^3,
     * evaluated here with explicit arithmetic from the raw fixture constants.
     */
    @Test
    void nuclearRepulsionForceMatchesHandComputedComponentsForEveryNucleusAndAxis() {
        Molecule molecule = asymmetricMolecule();
        for (int a = 0; a < 3; a++) {
            for (int axis = 0; axis < 3; axis++) {
                double expected = 0.0;
                for (int b = 0; b < 3; b++) {
                    if (b == a) continue;
                    double dx = NUCLEI[a][0] - NUCLEI[b][0];
                    double dy = NUCLEI[a][1] - NUCLEI[b][1];
                    double dz = NUCLEI[a][2] - NUCLEI[b][2];
                    double r3 = Math.pow(dx * dx + dy * dy + dz * dz, 1.5);
                    double displacement = switch (axis) {
                        case 0 -> dx;
                        case 1 -> dy;
                        default -> dz;
                    };
                    expected += CHARGES[a] * CHARGES[b] * displacement / r3;
                }
                assertThat(expected)
                        .as("fixture must make nucleus %d axis %d nontrivial", a, axis)
                        .isNotEqualTo(0.0);
                assertThat(AcZvFermiNetForceEstimator
                        .nuclearRepulsionForce(molecule, a, axis))
                        .as("nucleus %d axis %d", a, axis)
                        .isCloseTo(expected, within(TOLERANCE));
            }
        }
    }

    /**
     * ANALYTIC-NN-02: Newton's third law - the bare nucleus-nucleus force
     * components must sum to zero over nuclei for every axis.
     */
    @Test
    void nuclearRepulsionForceSumsToZeroOverNucleiPerAxis() {
        Molecule molecule = asymmetricMolecule();
        for (int axis = 0; axis < 3; axis++) {
            double total = 0.0, magnitude = 0.0;
            for (int a = 0; a < 3; a++) {
                double force = AcZvFermiNetForceEstimator
                        .nuclearRepulsionForce(molecule, a, axis);
                total += force;
                magnitude = Math.max(magnitude, Math.abs(force));
            }
            assertThat(magnitude)
                    .as("fixture must be force-bearing on axis %d", axis)
                    .isGreaterThan(0.1);
            assertThat(total)
                    .as("third-law sum on axis %d", axis)
                    .isCloseTo(0.0, within(TOLERANCE));
        }
    }

    /**
     * ANALYTIC-NN-03: fail-closed argument validation - coincident nuclei,
     * out-of-range nucleus index, and null molecule.
     */
    @Test
    void nuclearRepulsionForceFailsClosedOnCoincidentNucleiBadIndexAndNull() {
        assertThatThrownBy(() -> AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(coincidentMolecule(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coincident nuclei");
        assertThatThrownBy(() -> AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(asymmetricMolecule(), 3, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nucleus index out of range");
        assertThatThrownBy(() -> AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(asymmetricMolecule(), -1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nucleus index out of range");
        assertThatNullPointerException().isThrownBy(() ->
                AcZvFermiNetForceEstimator.nuclearRepulsionForce(null, 0, 0));
    }

    /**
     * ANALYTIC-Q-01: auxiliaryQ must equal the hand-computed
     * Z_A sum_i (r_i - R_A)_axis / |r_i - R_A| with asymmetric electrons, so
     * no component can cancel or vanish.
     */
    @Test
    void auxiliaryQMatchesHandComputedElectronNucleusDirectionSums() {
        Molecule molecule = asymmetricMolecule();
        QuantumCoordinates coordinates = asymmetricCoordinates();
        for (int a = 0; a < 3; a++) {
            for (int axis = 0; axis < 3; axis++) {
                double sum = 0.0;
                for (double[] electron : ELECTRONS) {
                    double dx = electron[0] - NUCLEI[a][0];
                    double dy = electron[1] - NUCLEI[a][1];
                    double dz = electron[2] - NUCLEI[a][2];
                    double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double displacement = switch (axis) {
                        case 0 -> dx;
                        case 1 -> dy;
                        default -> dz;
                    };
                    sum += displacement / r;
                }
                double expected = CHARGES[a] * sum;
                assertThat(Math.abs(expected))
                        .as("fixture must make Q(%d, %d) nontrivial", a, axis)
                        .isGreaterThan(1e-6);
                assertThat(AcZvFermiNetForceEstimator
                        .auxiliaryQ(molecule, coordinates, a, axis))
                        .as("Q nucleus %d axis %d", a, axis)
                        .isCloseTo(expected, within(TOLERANCE));
            }
        }
    }

    /**
     * ANALYTIC-CT-01: auxiliaryContraction must equal the hand-computed
     * documented jacobian contraction
     * Z_A sum_i sum_alpha (delta_c,alpha / r - disp_c disp_alpha / r^3)
     * g_{3i+alpha} with the made-up asymmetric log-gradient.
     */
    @Test
    void auxiliaryContractionMatchesHandComputedJacobianLogGradientContraction() {
        Molecule molecule = asymmetricMolecule();
        QuantumCoordinates coordinates = asymmetricCoordinates();
        for (int a = 0; a < 3; a++) {
            for (int axis = 0; axis < 3; axis++) {
                double sum = 0.0;
                for (int i = 0; i < ELECTRONS.length; i++) {
                    double dx = ELECTRONS[i][0] - NUCLEI[a][0];
                    double dy = ELECTRONS[i][1] - NUCLEI[a][1];
                    double dz = ELECTRONS[i][2] - NUCLEI[a][2];
                    double[] disp = {dx, dy, dz};
                    double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    double r3 = r * r * r;
                    for (int alpha = 0; alpha < 3; alpha++) {
                        double derivative = (alpha == axis ? 1.0 / r : 0.0)
                                - disp[axis] * disp[alpha] / r3;
                        sum += derivative * LOG_GRADIENT[3 * i + alpha];
                    }
                }
                double expected = CHARGES[a] * sum;
                assertThat(Math.abs(expected))
                        .as("fixture must make contraction(%d, %d) nontrivial", a, axis)
                        .isGreaterThan(1e-6);
                assertThat(AcZvFermiNetForceEstimator.auxiliaryContraction(
                        molecule, coordinates, LOG_GRADIENT, a, axis))
                        .as("contraction nucleus %d axis %d", a, axis)
                        .isCloseTo(expected, within(TOLERANCE));
            }
        }
    }

    /**
     * ANALYTIC-COAL-01 (exception-channel probe): an electron exactly on a
     * nucleus must surface through the documented
     * {@link FermiNetPhysicalSingularityException} channel - the exact type
     * the estimate() catch blocks handle (AcZvzbFermiNetForceEstimator.java
     * lines 123-136, AcZvFermiNetForceEstimator.java lines 127 and 141) - not
     * a bare IllegalArgumentException. radius() (AcZvFermiNetForceEstimator
     * .java lines 325-334) implements this channel for both auxiliaryQ and
     * auxiliaryContraction.
     */
    @Test
    void electronNucleusCoalescenceThrowsDocumentedPhysicalSingularityChannel() {
        Molecule molecule = asymmetricMolecule();
        QuantumCoordinates coalesced = new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,
                        NUCLEI[1][0], NUCLEI[1][1], NUCLEI[1][2],
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,
                        ELECTRONS[1][0], ELECTRONS[1][1], ELECTRONS[1][2],
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(2,
                        ELECTRONS[2][0], ELECTRONS[2][1], ELECTRONS[2][2],
                        SpinProjection.BETA)));
        for (int axis = 0; axis < 3; axis++) {
            int a = axis;
            assertThatThrownBy(() -> AcZvFermiNetForceEstimator
                    .auxiliaryQ(molecule, coalesced, 1, a))
                    .as("auxiliaryQ coalescence channel, axis %d", a)
                    .isInstanceOf(FermiNetPhysicalSingularityException.class);
            assertThatThrownBy(() -> AcZvFermiNetForceEstimator
                    .auxiliaryContraction(molecule, coalesced, LOG_GRADIENT, 1, a))
                    .as("auxiliaryContraction coalescence channel, axis %d", a)
                    .isInstanceOf(FermiNetPhysicalSingularityException.class);
        }
    }

    private static Molecule asymmetricMolecule() {
        return new Molecule("h-he-c-asymmetric-oracle", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(CHARGES[0]),
                        new CartesianPosition(NUCLEI[0][0], NUCLEI[0][1],
                                NUCLEI[0][2], LengthUnit.BOHR)),
                new NuclearCenter(1, "He", new NuclearCharge(CHARGES[1]),
                        new CartesianPosition(NUCLEI[1][0], NUCLEI[1][1],
                                NUCLEI[1][2], LengthUnit.BOHR)),
                new NuclearCenter(2, "C", new NuclearCharge(CHARGES[2]),
                        new CartesianPosition(NUCLEI[2][0], NUCLEI[2][1],
                                NUCLEI[2][2], LengthUnit.BOHR))),
                new MolecularCharge(6), new ElectronCount(3),
                new SpinSector(2, 1, 2));
    }

    private static Molecule coincidentMolecule() {
        return new Molecule("coincident-nuclei-probe", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(1),
                        new CartesianPosition(0.5, 0.5, 0.5, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0.5, 0.5, 0.5, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static QuantumCoordinates asymmetricCoordinates() {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,
                        ELECTRONS[0][0], ELECTRONS[0][1], ELECTRONS[0][2],
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,
                        ELECTRONS[1][0], ELECTRONS[1][1], ELECTRONS[1][2],
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(2,
                        ELECTRONS[2][0], ELECTRONS[2][1], ELECTRONS[2][2],
                        SpinProjection.BETA)));
    }
}
