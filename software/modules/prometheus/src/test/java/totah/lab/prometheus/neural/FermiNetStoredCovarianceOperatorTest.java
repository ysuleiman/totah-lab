package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Verifies that cached sample-major covariance matches the already-qualified
 * streaming covariance action while performing zero neural reevaluations
 * during repeated operator applications.
 */
final class FermiNetStoredCovarianceOperatorTest {

    @Test
    void cachedOperatorMatchesStreamingAndDoesNotReevaluate() {
        Fixture fixture = fixture();

        FermiNetSrEvaluationStore store =
                FermiNetSrEvaluationStore.build(
                        fixture.state,
                        fixture.samples);

        assertEquals(
                4,
                store.sampleCount(),
                "zero-weight sample must not be stored");

        assertEquals(
                4L,
                store.neuralEvaluations(),
                "each nonzero sample must be evaluated exactly once");

        StoredCenteredCovarianceOperator cached =
                new StoredCenteredCovarianceOperator(
                        store,
                        0.2);

        FermiNetMatrixFreeSrOptimizer streaming =
                new FermiNetMatrixFreeSrOptimizer();

        int p = fixture.state.parameterCount();
        double[] probe = new double[p];

        for (int i = 0; i < p; i++) {
            probe[i] =
                    Math.sin(
                            0.17
                                    * (i + 1));
        }

        double[] expected =
                streaming.explicitJacobianCovarianceActionReference(
                        fixture.state,
                        fixture.samples,
                        0.2,
                        probe);

        long before =
                store.neuralEvaluations();

        double[] first =
                cached.apply(probe);

        double[] second =
                cached.apply(probe);

        long after =
                store.neuralEvaluations();

        double firstError =
                maxError(
                        expected,
                        first);

        double repeatError =
                maxError(
                        first,
                        second);

        System.out.printf(
                """
                FERMINET_STORED_COVARIANCE_PARITY
                  parameters=%d
                  stored_samples=%d
                  primitive_store_bytes=%d
                  streaming_vs_cached_max_error=%.17g
                  repeat_max_error=%.17g
                  neural_evaluations_before=%d
                  neural_evaluations_after=%d
                  operator_applications=%d
                  derivative_rows_read=%d

                """,
                p,
                store.sampleCount(),
                store.primitiveStorageBytes(),
                firstError,
                repeatError,
                before,
                after,
                cached.counters().operatorApplications(),
                cached.counters().derivativeRowsRead());

        assertTrue(
                firstError < 2e-10,
                "streaming/cached covariance action error="
                        + firstError);

        assertTrue(
                repeatError < 1e-15,
                "cached action must be deterministic");

        assertEquals(
                before,
                after,
                "cached covariance application must not reevaluate FermiNet");

        assertEquals(
                2L,
                cached.counters().operatorApplications());

        assertEquals(
                4L * 2L * 2L,
                cached.counters().derivativeRowsRead());
    }

    private static double maxError(
            double[] expected,
            double[] actual) {

        double maximum = 0.0;

        for (int i = 0; i < expected.length; i++) {
            maximum =
                    Math.max(
                            maximum,
                            Math.abs(
                                    expected[i]
                                            - actual[i]));
        }

        return maximum;
    }

    private static Fixture fixture() {
        Molecule molecule =
                water();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.initialize(
                                new FermiNetParameterLayout(
                                        configuration,
                                        molecule),
                                44017L));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples =
                List.of(
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                1.0,
                                coordinates(0.0)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                2.0,
                                coordinates(.015)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                .75,
                                coordinates(-.021)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                1.25,
                                coordinates(.033)),
                        new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                0.0,
                                coordinates(.2)));

        return new Fixture(
                state,
                samples);
    }

    private static QuantumCoordinates coordinates(
            double shift) {

        double[][] xyz = {
                {.18, .11, .27},
                {-.31, .42, -.16},
                {.57, -.28, .33},
                {-.63, -.37, .21},
                {.24, .71, -.45},
                {-.22, -.15, -.38},
                {.36, -.54, .19},
                {-.48, .26, .51},
                {.69, .18, -.24},
                {-.12, .61, .37}
        };

        List<QuantumCoordinates.ParticleCoordinate> result =
                new ArrayList<>();

        for (int i = 0; i < xyz.length; i++) {
            double signed =
                    i % 2 == 0
                            ? shift
                            : -shift;

            result.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            xyz[i][0] + signed,
                            xyz[i][1] - .5 * signed,
                            xyz[i][2] + .25 * signed,
                            i < 5
                                    ? SpinProjection.ALPHA
                                    : SpinProjection.BETA));
        }

        return new QuantumCoordinates(
                result);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-stored-covariance-test-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0,
                                        0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -.46464225035067114,
                                        1.7340684963325879,
                                        0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }

    private record Fixture(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {
    }
}
