package totah.lab.math;

import org.junit.jupiter.api.Test;
import totah.lab.math.charges.ChargeSystem;
import totah.lab.math.charges.GasteigerModel;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gasteiger-Marsili iterative charges: fast, topology-driven, normalized to
 * the target total charge after the iteration loop.
 */
public class GasteigerModelTest {

    private final GasteigerModel model = new GasteigerModel();

    @Test
    public void chargesConserveZeroTotalChargeOnWater() {
        double[] q = model.computeCharges(TestChargeSystems.water(), 0.0);
        assertEquals(0.0, Arrays.stream(q).sum(), 1e-12,
                "charges must be normalized to the formal charge");
    }

    @Test
    public void electronegativeOxygenTakesNegativeCharge() {
        double[] q = model.computeCharges(TestChargeSystems.water(), 0.0);
        assertTrue(q[0] < 0.0, "oxygen should carry negative partial charge: " + q[0]);
        assertTrue(q[1] > 0.0, "H1 should carry positive partial charge: " + q[1]);
        assertTrue(q[2] > 0.0, "H2 should carry positive partial charge: " + q[2]);
    }

    @Test
    public void symmetricGeometryYieldsSymmetricCharges() {
        double[] q = model.computeCharges(TestChargeSystems.water(), 0.0);
        assertEquals(q[1], q[2], 1e-12,
                "symmetry-equivalent hydrogens must get identical charges");
    }

    @Test
    public void nonZeroFormalChargeIsConserved() {
        double[] q = model.computeCharges(TestChargeSystems.water(), 1.0);
        assertEquals(1.0, Arrays.stream(q).sum(), 1e-12,
                "charges must be normalized to the +1 formal charge");
    }

    @Test
    public void isolatedAtomStaysNeutral() {
        ChargeSystem methane = TestChargeSystems.of(
                new String[]{"C"}, new double[][]{{0.0, 0.0, 0.0}}, new int[0][]);
        double[] q = model.computeCharges(methane, 0.0);
        assertEquals(0.0, q[0], 1e-12,
                "an atom without neighbors must keep zero charge");
    }

    @Test
    public void iterationCountDoesNotBreakConservation() {
        ChargeSystem water = TestChargeSystems.water();
        for (int iterations : new int[]{1, 3, 6, 12}) {
            double[] q = new GasteigerModel(iterations).computeCharges(water, 0.0);
            assertEquals(0.0, Arrays.stream(q).sum(), 1e-12,
                    "conservation broke at " + iterations + " iterations");
            assertTrue(q[0] < 0.0,
                    "oxygen sign flipped at " + iterations + " iterations");
        }
    }
}
