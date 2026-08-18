package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

final class FermiNetKfacOptimizerTest {

    @Test
    void denseFactorsMatchIndependentReferenceForSeveralShapes() {
        verifyFactors(1, 2, 3);
        verifyFactors(4, 3, 2);
        verifyFactors(10, 8, 8);
    }

    @Test
    void densePreconditioningUsesOutputLeftAndInputRightOrientation() {
        int inputs = 2;
        int outputs = 3;
        double[] a = {2.0, 0.0, 0.0, 5.0};
        double[] g = {
                3.0, 0.0, 0.0,
                0.0, 7.0, 0.0,
                0.0, 0.0, 11.0
        };
        double[] gradient = {6.0, 10.0, 14.0, 35.0, 22.0, 55.0};
        double[] actual = FermiNetKfacOptimizer.preconditionDense(
                gradient,
                inputs,
                outputs,
                FermiNetKfacOptimizer.choleskyDamped(a, inputs, 0.0),
                FermiNetKfacOptimizer.choleskyDamped(g, outputs, 0.0));
        assertArrayEquals(new double[]{1.0, 2.0 / 3.0, 1.0, 1.0, 1.0, 1.0},
                actual, 1.0e-14);
    }

    @Test
    void repositoryFixtureMatchesExactGradientAndReplaysDeterministically() {
        Fixture fixture = fixture();
        var exactConfiguration = new FermiNetMatrixFreeSrOptimizer.Configuration(
                0.01, 0.2, 10.0, 2, 128, 50, 1.0e-6, 1.0e-8);
        var kfacConfiguration = configuration(0.0);

        FermiNetMatrixFreeSrOptimizer.Result exact =
                new FermiNetMatrixFreeSrOptimizer().oneIteration(
                        fixture.state(), fixture.samples(), exactConfiguration);
        FermiNetKfacOptimizer firstOptimizer = new FermiNetKfacOptimizer();
        FermiNetKfacOptimizer.Result first = firstOptimizer.oneIteration(
                fixture.state(), fixture.samples(), kfacConfiguration);
        FermiNetKfacOptimizer.Result replay = new FermiNetKfacOptimizer()
                .oneIteration(fixture.state(), fixture.samples(), kfacConfiguration);

        assertArrayEquals(exact.energyGradient(), first.energyGradient());
        assertArrayEquals(first.appliedUpdate(), replay.appliedUpdate());
        assertArrayEquals(first.state().parameterArray(), replay.state().parameterArray());
        assertEquals(5, first.denseKfacBlockCount());
        assertEquals(7, first.diagonalBlockCount());
        assertEquals(fixture.samples().size(), first.statisticsEvaluationCount());
        assertTrue(first.factorDecompositionUpdated());
        assertTrue(Double.isFinite(first.approximateFisherSquaredNorm()));
        for (double value : first.appliedUpdate()) {
            assertTrue(Double.isFinite(value));
        }

        FermiNetKfacOptimizer.Result second = firstOptimizer.oneIteration(
                first.state(), fixture.samples(), kfacConfiguration);
        assertFalse(second.factorDecompositionUpdated());
        assertEquals(2, second.curvatureState().iteration());
    }

    @Test
    void fisherNormConstraintAndGlobalNormAreReportedSeparately() {
        Fixture fixture = fixture();
        FermiNetKfacOptimizer.Result result = new FermiNetKfacOptimizer()
                .oneIteration(fixture.state(), fixture.samples(), configuration(1.0e-12));
        assertTrue(result.fisherNormRescaled());
        assertTrue(result.approximateFisherSquaredNorm() <= 1.0e-12 * (1.0 + 1.0e-12));
        assertFalse(result.maxUpdateRescaled());
    }

    @Test
    void emaUsesBatchDirectlyInitiallyAndConfiguredDecayAfterward() {
        assertArrayEquals(
                new double[]{2.8, 4.8},
                FermiNetKfacOptimizer.ema(
                        new double[]{2.0, 4.0},
                        new double[]{6.0, 8.0},
                        0.8),
                1.0e-15);
    }

    @Test
    void selectableVariationalLoopKeepsKfacStateAcrossIterations() {
        Fixture fixture = fixture();
        List<QuantumCoordinates> walkers = fixture.samples().subList(0, 2).stream()
                .map(FermiNetMatrixFreeSrOptimizer.WeightedSample::coordinates)
                .toList();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2, 1, 2, 1, 0.02, 991L);
        List<FermiNetVariationalOptimizer.OptimizationIterationResult> results;
        try (var optimizer = new FermiNetVariationalOptimizer(2)) {
            results = optimizer.optimize(
                    fixture.state(), walkers, sampling,
                    FermiNetVariationalOptimizer.OptimizationConfiguration.kfac(
                            configuration(0.0)),
                    2);
        }
        assertEquals(2, results.size());
        assertEquals(FermiNetOptimizerType.KFAC, results.get(0).optimizerType());
        assertArrayEquals(
                results.get(0).updatedState().parameterArray(),
                results.get(1).inputState().parameterArray());
        assertEquals(1, results.get(0).kfacResult().curvatureState().iteration());
        assertEquals(2, results.get(1).kfacResult().curvatureState().iteration());
        assertTrue(results.get(0).kfacResult().factorDecompositionUpdated());
        assertFalse(results.get(1).kfacResult().factorDecompositionUpdated());
    }

    @Test
    void canonicalOneIterationSelectsKfacWithoutChangingVmcLifecycle() {
        Fixture fixture = fixture();
        List<QuantumCoordinates> walkers = fixture.samples().subList(0, 2).stream()
                .map(FermiNetMatrixFreeSrOptimizer.WeightedSample::coordinates)
                .toList();
        var sampling = new FermiNetVariationalOptimizer.SamplingConfiguration(
                2, 1, 2, 1, 0.02, 991L);
        FermiNetVariationalOptimizer.OptimizationIterationResult result;
        try (var optimizer = new FermiNetVariationalOptimizer(2)) {
            result = optimizer.oneIteration(
                    0, fixture.state(), walkers, sampling,
                    FermiNetVariationalOptimizer.OptimizationConfiguration.kfac(
                            configuration(0.0)));
        }
        assertEquals(FermiNetOptimizerType.KFAC, result.optimizerType());
        assertEquals(4, result.vmcResult().samples().size());
        assertEquals(2, result.nextWalkers().size());
        assertEquals(4, result.kfacResult().statisticsEvaluationCount());
    }

    private static void verifyFactors(int occurrences, int inputs, int outputs) {
        int samples = 3;
        int length = occurrences * (inputs + outputs);
        var family = new FermiNetStructuredSrStatistics.Family(
                "synthetic", FermiNetStructuredSrStatistics.Kind.DENSE_WEIGHT,
                occurrences, outputs, inputs, 0, length);
        double[] statistics = new double[samples * length];
        for (int sample = 0; sample < samples; sample++) {
            int base = sample * length;
            for (int i = 0; i < occurrences * inputs; i++) {
                statistics[base + i] = 0.1 * (1 + sample + 2 * i);
            }
            for (int i = 0; i < occurrences * outputs; i++) {
                statistics[base + occurrences * inputs + i] =
                        -0.07 * (1 + 3 * sample + i);
            }
        }
        double[] weights = {0.2, 0.3, 0.5};
        assertArrayEquals(
                referenceFactor(statistics, samples, occurrences, inputs,
                        length, 0, weights),
                FermiNetKfacOptimizer.denseFactor(
                        statistics, family, samples, weights, true),
                1.0e-12);
        assertArrayEquals(
                referenceFactor(statistics, samples, occurrences, outputs,
                        length, occurrences * inputs, weights),
                FermiNetKfacOptimizer.denseFactor(
                        statistics, family, samples, weights, false),
                1.0e-12);
    }

    private static double[] referenceFactor(
            double[] data, int samples, int occurrences, int dimension,
            int stride, int offset, double[] weights) {
        double[] result = new double[dimension * dimension];
        for (int row = 0; row < dimension; row++) {
            for (int column = 0; column < dimension; column++) {
                for (int sample = 0; sample < samples; sample++) {
                    for (int occurrence = 0; occurrence < occurrences; occurrence++) {
                        int base = sample * stride + offset + occurrence * dimension;
                        result[row * dimension + column] += weights[sample]
                                * data[base + row] * data[base + column]
                                / occurrences;
                    }
                }
            }
        }
        return result;
    }

    private static FermiNetKfacOptimizer.Configuration configuration(
            double normConstraint) {
        return new FermiNetKfacOptimizer.Configuration(
                0.01, 0.2, 0.95, 5, 10.0, normConstraint);
    }

    private static Fixture fixture() {
        Molecule molecule = hydrogen();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetV1State state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule),
                        44017L));
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples = List.of(
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.0, coordinates(0.0)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(2.0, coordinates(0.02)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(0.5, coordinates(-0.015)),
                new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.5, coordinates(0.035)));
        return new Fixture(state, samples);
    }

    private static Molecule hydrogen() {
        return new Molecule(
                "ferminet-kfac-h2",
                List.of(
                        new NuclearCenter(0, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.7, 0, 0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(0.7, 0, 0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static QuantumCoordinates coordinates(double shift) {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                0, -0.4 + shift, 0.15, -0.1, SpinProjection.ALPHA));
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                1, 0.45 - shift, -0.12, 0.08, SpinProjection.BETA));
        return new QuantumCoordinates(particles);
    }

    private record Fixture(
            FermiNetV1State state,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {}
}
