package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

/**
 * TEST_ID: D4, D5, D6 — component map integrity: duplicate, missing,
 * out-of-range.
 *
 * <p>Seam: the (nucleus, axis)-keyed force result over N atoms is
 * {@link NuclearForceResult.Component} (nucleus, axis), and the assembly
 * contract is enforced where the components are consumed:
 * {@link FermiNetNuclearForceValidation#validate} /
 * {@code physicalDiagnostics} require exactly 3N components, each
 * (nucleus, axis) ∈ {0..N−1}×{0,1,2} exactly once, and reject violations
 * descriptively before any statistics are computed
 * ({@code requireCompleteCartesianIdentity}). A duplicate must not silently
 * overwrite; a missing component must not be zero-filled; an out-of-range
 * index must not become an AIOOBE deep in the math.
 *
 * <p>All fixtures use N = 2 (six addressed components) with distinct,
 * nonzero means so a swap or drop would also be numerically visible.
 */
final class AdversarialComponentMapAcceptanceTest {

    /**
     * D4 — duplicate (0,x) with missing (1,z): six components are supplied
     * (the count check passes) but the identity map is wrong. Oracle:
     * descriptive rejection naming the duplicate — never a silent overwrite
     * of (0,x) and implicit zero at (1,z).
     */
    @Test
    void d4DuplicateComponentWithMissingPartnerIsRejected() {
        List<NuclearForceResult.Component> components = validComponents();
        components.set(5, component(0, 0, -0.123));
        NuclearForceResult result = result(components);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FermiNetNuclearForceValidation.validate(molecule(), result));
        assertTrue(error.getMessage().contains("duplicate"),
                "rejection must name the duplicate identity, got: " + error.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> FermiNetNuclearForceValidation.physicalDiagnostics(molecule(), result));
    }

    /**
     * D5 — only five components: the count itself violates the 3N contract.
     * Oracle: descriptive rejection for an incomplete vector, before any
     * identity scan or statistic.
     */
    @Test
    void d5IncompleteComponentVectorIsRejected() {
        List<NuclearForceResult.Component> components = validComponents();
        components.remove(5);
        NuclearForceResult result = result(components);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FermiNetNuclearForceValidation.validate(molecule(), result));
        assertTrue(error.getMessage().contains("incomplete"),
                "rejection must name the incomplete vector, got: " + error.getMessage());
    }

    /**
     * D5 complement — completeness is identity-based, not position-based: by
     * the pigeonhole principle a six-component map for N=2 that misses one
     * identity must either duplicate another (D4) or go out of range (D6), so
     * no missing-component fixture with correct count can evade those two
     * rejections. What remains to lock is the positive direction: the same six
     * identities in scrambled order are a complete vector and must validate to
     * the same totals.
     */
    @Test
    void d5bComponentOrderCarriesNoMeaning() {
        List<NuclearForceResult.Component> scrambled = new ArrayList<>(validComponents());
        java.util.Collections.swap(scrambled, 0, 5);
        java.util.Collections.swap(scrambled, 1, 3);
        var ordered = FermiNetNuclearForceValidation.validate(
                molecule(), result(validComponents()));
        var shuffled = assertDoesNotThrow(() -> FermiNetNuclearForceValidation
                .validate(molecule(), result(scrambled)));
        assertEquals(6, shuffled.finiteComponents());
        assertEquals(ordered.totalForceHartreePerBohr().x(),
                shuffled.totalForceHartreePerBohr().x(), 0.0);
        assertEquals(ordered.totalForceHartreePerBohr().y(),
                shuffled.totalForceHartreePerBohr().y(), 0.0);
        assertEquals(ordered.totalForceHartreePerBohr().z(),
                shuffled.totalForceHartreePerBohr().z(), 0.0);
    }

    /**
     * D6 — out-of-range identities: nucleus index 2 (== N), nucleus index −1,
     * and axis index 3. Oracle: descriptive rejection naming the out-of-range
     * index — never an ArrayIndexOutOfBoundsException from downstream math.
     */
    @Test
    void d6OutOfRangeNucleusOrAxisIsRejectedBeforeMath() {
        List<NuclearForceResult.Component> nucleusTooLarge = validComponents();
        nucleusTooLarge.set(5, component(2, 2, 0.5));
        IllegalArgumentException tooLarge = assertThrows(IllegalArgumentException.class,
                () -> FermiNetNuclearForceValidation.validate(
                        molecule(), result(nucleusTooLarge)));
        assertTrue(tooLarge.getMessage().contains("out of range"),
                "got: " + tooLarge.getMessage());

        List<NuclearForceResult.Component> nucleusNegative = validComponents();
        nucleusNegative.set(5, component(-1, 2, 0.5));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> FermiNetNuclearForceValidation.validate(
                        molecule(), result(nucleusNegative)));
        assertTrue(negative.getMessage().contains("out of range"),
                "got: " + negative.getMessage());

        List<NuclearForceResult.Component> axisTooLarge = validComponents();
        axisTooLarge.set(5, component(1, 3, 0.5));
        IllegalArgumentException axis = assertThrows(IllegalArgumentException.class,
                () -> FermiNetNuclearForceValidation.validate(
                        molecule(), result(axisTooLarge)));
        assertTrue(axis.getMessage().contains("out of range"),
                "got: " + axis.getMessage());
    }

    private static final double[] MEANS = {0.031, -0.017, 0.044, -0.029, 0.021, -0.037};

    private static List<NuclearForceResult.Component> validComponents() {
        List<NuclearForceResult.Component> components = new ArrayList<>();
        for (int nucleus = 0; nucleus < 2; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                components.add(component(nucleus, axis, MEANS[nucleus * 3 + axis]));
            }
        }
        return components;
    }

    private static NuclearForceResult.Component component(int nucleus, int axis, double mean) {
        String axisName = axis >= 0 && axis < 3
                ? new String[]{"x", "y", "z"}[axis] : "axis-" + axis;
        return new NuclearForceResult.Component(
                nucleus, axis, axisName, mean, 0.001, 1.0e-6, 100, 0,
                new NuclearForceResult.TailDiagnostics(
                        mean - 0.01, mean - 0.005, mean - 0.002, mean,
                        mean + 0.002, mean + 0.005, mean + 0.01, 0, 0),
                "d4-d6-component-" + nucleus + "-" + axis,
                new double[]{mean});
    }

    private static NuclearForceResult result(List<NuclearForceResult.Component> components) {
        return new NuclearForceResult(
                NuclearForceEstimatorType.CORRELATED_FD, "adversarial-d4-d6",
                "parameter-checksum", "geometry-identity", "dataset-checksum",
                "checkpoint-checksum", "estimator-configuration",
                100, 4, 25, List.copyOf(components),
                new NuclearForceResult.CorrelatedFdDiagnostics(1.0e-3, List.of()));
    }

    private static Molecule molecule() {
        return new Molecule(
                "adversarial-component-map-h2",
                List.of(
                        new NuclearCenter(0, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.7, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(0.7, 0.0, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2), new SpinSector(1, 1, 1));
    }
}
