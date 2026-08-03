package totah.lab.hephaestus.ligand.charge;

import org.junit.jupiter.api.Test;
import totah.lab.euclid.linear.DenseDirectSolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shared QEqModel must be safe for parallel batch pipelines: the
 * fallback-warning dedup set is mutated inside computeCharges.
 */
class QEqModelConcurrencyTest {

    @Test
    void sharedModelSurvivesParallelFallbackComputations() throws Exception {
        QEqModel shared = new QEqModel(new DenseDirectSolver());
        // "Xx" has no QEq parameters and falls back to carbon.
        ChargeSystem system = TestChargeSystems.of(
                new String[]{"Xx", "H", "H"},
                new double[][]{
                        {0.000, 0.000, 0.0},
                        {0.757, 0.586, 0.0},
                        {-0.757, 0.586, 0.0}},
                new int[][]{{0, 1}, {0, 2}});

        int workers = 8;
        int iterations = 25;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<double[]>> futures = new ArrayList<>();
            for (int i = 0; i < workers * iterations; i++) {
                Callable<double[]> task =
                        () -> shared.computeCharges(system, 0.0);
                futures.add(pool.submit(task));
            }
            double[] reference = futures.getFirst().get();
            for (Future<double[]> future : futures) {
                double[] charges = future.get();
                assertEquals(0.0,
                        charges[0] + charges[1] + charges[2], 1e-9);
                assertEquals(reference[0], charges[0], 1e-12);
                assertTrue(Double.isFinite(charges[0]));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
