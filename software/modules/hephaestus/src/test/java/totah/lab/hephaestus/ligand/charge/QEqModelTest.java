package totah.lab.hephaestus.ligand.charge;

import org.junit.jupiter.api.Test;
import totah.lab.euclid.linear.DenseDirectSolver;

import java.nio.file.Path;

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

    private final QEqModel model = new QEqModel(new DenseDirectSolver());

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
    public void builtInParametersCoverCommonProteinElements() {
        for (String element : new String[]{"H", "C", "N", "O", "S", "P"}) {
            assertTrue(model.hasParameters(element),
                    "built-in QEq parameters missing for " + element);
        }
        assertFalse(model.hasParameters("Xx"),
                "unknown elements must not report parameters");
    }

    @Test
    public void customParameterFileWithoutCarbonFailsMeaningfully(
            @org.junit.jupiter.api.io.TempDir Path tempDir)
            throws java.io.IOException {
        Path paramFile = tempDir.resolve("qeq.txt");
        java.nio.file.Files.writeString(paramFile,
                "N 6.899 11.7600 0.9770\nO 8.741 13.3640 0.8597\n");
        QEqModel custom = new QEqModel(new DenseDirectSolver(), paramFile);

        ChargeSystem system = TestChargeSystems.of(
                new String[]{"N", "Xx"},
                new double[][]{{0.0, 0.0, 0.0}, {1.2, 0.0, 0.0}},
                new int[][]{{0, 1}});

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> custom.computeCharges(system, 0.0));
        assertTrue(error.getMessage().contains("'C'"),
                "exception must name the missing fallback parameter: "
                        + error.getMessage());
        assertTrue(error.getMessage().contains("Xx"),
                "exception must name the unknown element: "
                        + error.getMessage());
        assertTrue(error.getMessage().contains(paramFile.toString()),
                "exception must name the parameter file: "
                        + error.getMessage());
    }

    @Test
    public void coincidentAtomsDoNotProduceNaNCharges() {
        ChargeSystem coincident = TestChargeSystems.of(
                new String[]{"H", "H"},
                new double[][]{{0.0, 0.0, 0.0}, {0.0, 0.0, 0.0}},
                new int[][]{{0, 1}});

        double[] q = model.computeCharges(coincident, 0.0);

        assertTrue(Double.isFinite(q[0]), "charge must be finite: " + q[0]);
        assertTrue(Double.isFinite(q[1]), "charge must be finite: " + q[1]);
    }
}
