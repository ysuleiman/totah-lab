package totah.lab.math;

import org.junit.jupiter.api.Test;
import totah.lab.math.charges.ChargeSystem;
import totah.lab.math.charges.QEqModel;
import totah.lab.math.linear.DenseDirectSolver;
import totah.lab.math.linear.HybridSolver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QEq charge equilibration on a water molecule, using the built-in Open
 * Babel parameter set (no external qeq.txt needed).
 *
 * Note: computeCharges returns the raw KKT solution vector - n atom charges
 * followed by the Lagrange multiplier - so all assertions read the first
 * system.size() entries, exactly as ChargeAssignmentStage does.
 */
public class QEqModelTest {

    private final QEqModel model = new QEqModel(new HybridSolver(2000));

    @Test
    public void chargesConserveZeroTotalChargeOnWater() {
        ChargeSystem water = TestChargeSystems.water();
        double[] q = model.computeCharges(water, 0.0);
        double sum = q[0] + q[1] + q[2];
        assertEquals(0.0, sum, 1e-9, "atom charges must sum to the formal charge");
    }

    @Test
    public void electronegativeOxygenTakesNegativeCharge() {
        ChargeSystem water = TestChargeSystems.water();
        double[] q = model.computeCharges(water, 0.0);
        assertTrue(q[0] < 0.0, "oxygen should carry negative partial charge: " + q[0]);
        assertTrue(q[1] > 0.0, "H1 should carry positive partial charge: " + q[1]);
        assertTrue(q[2] > 0.0, "H2 should carry positive partial charge: " + q[2]);
        assertEquals(-2.0 * q[1], q[0], 1e-9,
                "charge should partition evenly onto the two symmetric hydrogens");
    }

    @Test
    public void symmetricGeometryYieldsSymmetricCharges() {
        ChargeSystem water = TestChargeSystems.water();
        double[] q = model.computeCharges(water, 0.0);
        assertEquals(q[1], q[2], 1e-9,
                "symmetry-equivalent hydrogens must get identical charges");
    }

    @Test
    public void nonZeroFormalChargeIsConserved() {
        ChargeSystem water = TestChargeSystems.water();
        double[] q = model.computeCharges(water, 1.0);
        assertEquals(1.0, q[0] + q[1] + q[2], 1e-9,
                "atom charges must sum to the +1 formal charge");
    }

    @Test
    public void directAndIterativeSolversAgreeOnWater() {
        ChargeSystem water = TestChargeSystems.water();
        double[] direct = new QEqModel(new DenseDirectSolver()).computeCharges(water, 0.0);
        // HybridSolver(0) routes every system to the preconditioned CG path
        double[] iterative = new QEqModel(new HybridSolver(0)).computeCharges(water, 0.0);
        for (int i = 0; i < water.size(); i++) {
            assertEquals(direct[i], iterative[i], 1e-4,
                    "direct and PCG solvers disagree on atom " + i);
        }
    }

    @Test
    public void builtInParametersCoverCommonProteinElements() {
        for (String element : new String[]{"H", "C", "N", "O", "S", "P"}) {
            assertTrue(model.hasParameters(element),
                    "built-in QEq parameters missing for " + element);
        }
        assertFalse(model.hasParameters("Xx"),
                "unknown elements must not report parameters");
    }
}
