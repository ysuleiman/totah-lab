package totah.lab.hephaestus.ligand.charge;

import org.junit.jupiter.api.Test;
import totah.lab.hephaestus.ligand.charge.ChargeSystem;
import totah.lab.hephaestus.ligand.charge.GasteigerModel;

import java.util.Arrays;
import java.util.List;

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

    @Test
    public void bondOrderSelectsHybridizationSpecificParameters() {
        ChargeSystem singleBond = carbonOxygenSystem(1.0, false);
        ChargeSystem doubleBond = carbonOxygenSystem(2.0, false);
        ChargeSystem aromaticBond = carbonOxygenSystem(1.0, true);

        double[] singleCharges = model.computeCharges(singleBond, 0.0);
        double[] doubleCharges = model.computeCharges(doubleBond, 0.0);
        double[] aromaticCharges = model.computeCharges(aromaticBond, 0.0);

        assertNotEquals(singleCharges[0], doubleCharges[0],
                "sp3 and sp2 carbon parameters must differ");
        assertEquals(doubleCharges[0], aromaticCharges[0], 1.0e-12,
                "aromatic atoms should select sp2 parameters");
    }

    private ChargeSystem carbonOxygenSystem(double bondOrder, boolean aromatic) {
        return new ChargeSystem() {
            @Override public int size() { return 2; }
            @Override public double getX(int i) { return i; }
            @Override public double getY(int i) { return 0.0; }
            @Override public double getZ(int i) { return 0.0; }
            @Override public String getElement(int i) { return i == 0 ? "C" : "O"; }
            @Override public List<Integer> getNeighbors(int i) {
                return List.of(i == 0 ? 1 : 0);
            }
            @Override public double getBondOrder(int first, int second) {
                return bondOrder;
            }
            @Override public boolean isAromatic(int i) {
                return aromatic;
            }
        };
    }
}
