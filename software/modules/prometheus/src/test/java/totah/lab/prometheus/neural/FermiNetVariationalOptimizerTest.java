package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetVariationalOptimizerTest {

    private static final int PARALLELISM = 2;

    @Test
    void oneIterationMatchesManualCompositionExactly() {
        Fixture fixture = fixture();
        var sampling = sampling(2, 2, 811L);
        var srConfiguration = srConfiguration();

        FermiNetVariationalOptimizer.IterationResult actual;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            actual = optimizer.oneIteration(
                    0,
                    fixture.state(),
                    fixture.walkers(),
                    sampling,
                    srConfiguration);
        }

        FermiNetVmc.Result manualVmc;
        try (var vmc = new FermiNetVmcParallel(PARALLELISM)) {
            manualVmc = vmc.sample(
                    fixture.state(),
                    new FermiNetVmc.Configuration(
                            sampling.walkers(),
                            sampling.warmupSweeps(),
                            sampling.retainedPerWalker(),
                            sampling.sweepsBetweenRetained(),
                            sampling.stepSizeBohr(),
                            sampling.baseSeed()),
                    fixture.walkers());
        }
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> manualSamples =
                manualVmc.samples().stream()
                        .map(coordinates ->
                                new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                                        1.0,
                                        coordinates))
                        .toList();
        FermiNetMatrixFreeSrOptimizer.Result manualSr =
                new FermiNetMatrixFreeSrOptimizer().oneIteration(
                        fixture.state(),
                        manualSamples,
                        srConfiguration);

        assertSameBits(manualVmc.acceptance(), actual.vmcResult().acceptance());
        assertCoordinatesExactly(manualVmc.samples(), actual.vmcResult().samples());
        assertLocalEnergiesExactly(
                manualVmc.localEnergies(),
                actual.vmcResult().localEnergies());
        assertSameBits(manualSr.initialEnergyHartree(), actual.srResult().initialEnergyHartree());
        assertSameBits(manualSr.gradientNorm(), actual.srResult().gradientNorm());
        assertSameBits(manualSr.rawUpdateNorm(), actual.srResult().rawUpdateNorm());
        assertSameBits(manualSr.appliedUpdateNorm(), actual.srResult().appliedUpdateNorm());
        assertSameBits(
                manualSr.relativeTrueResidual(),
                actual.srResult().relativeTrueResidual());
        assertArrayEquals(
                manualSr.state().parameterArray(),
                actual.updatedState().parameterArray());

        int start = manualVmc.samples().size() - sampling.walkers();
        assertCoordinatesExactly(
                manualVmc.samples().subList(start, manualVmc.samples().size()),
                actual.nextWalkers());
    }

    @Test
    void optimizePropagatesUpdatedStateAndAdvancesSeedsForThreeSteps() {
        Fixture fixture = fixture();
        List<FermiNetVariationalOptimizer.IterationResult> results;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            results = optimizer.optimize(
                    fixture.state(),
                    fixture.walkers(),
                    sampling(2, 2, 1200L),
                    srConfiguration(),
                    3);
        }

        assertEquals(3, results.size());
        assertEquals(1200L, results.get(0).seed());
        assertEquals(1201L, results.get(1).seed());
        assertEquals(1202L, results.get(2).seed());
        assertArrayEquals(
                fixture.state().parameterArray(),
                results.get(0).inputState().parameterArray());
        assertArrayEquals(
                results.get(0).updatedState().parameterArray(),
                results.get(1).inputState().parameterArray());
        assertArrayEquals(
                results.get(1).updatedState().parameterArray(),
                results.get(2).inputState().parameterArray());
        for (FermiNetVariationalOptimizer.IterationResult result : results) {
            assertFalse(Arrays.equals(
                    result.inputState().parameterArray(),
                    result.updatedState().parameterArray()));
        }
    }

    @Test
    void allRetainedSamplesFeedSrButOnlyLastRetentionSeedsNextIteration() {
        Fixture fixture = fixture();
        var sampling = sampling(2, 3, 2026L);
        FermiNetVariationalOptimizer.IterationResult result;
        try (var optimizer = new FermiNetVariationalOptimizer(PARALLELISM)) {
            result = optimizer.oneIteration(
                    0,
                    fixture.state(),
                    fixture.walkers(),
                    sampling,
                    srConfiguration());
        }

        int expectedSamples = sampling.walkers() * sampling.retainedPerWalker();
        assertEquals(expectedSamples, result.vmcResult().samples().size());
        assertEquals(expectedSamples, result.srResult().sampleEvaluations());
        assertEquals(sampling.walkers(), result.nextWalkers().size());
        assertCoordinatesExactly(
                result.vmcResult().samples().subList(
                        expectedSamples - sampling.walkers(),
                        expectedSamples),
                result.nextWalkers());
    }

    private static FermiNetVariationalOptimizer.SamplingConfiguration sampling(
            int walkers,
            int retainedPerWalker,
            long seed) {
        return new FermiNetVariationalOptimizer.SamplingConfiguration(
                walkers,
                1,
                retainedPerWalker,
                1,
                0.02,
                seed);
    }

    private static FermiNetMatrixFreeSrOptimizer.Configuration srConfiguration() {
        return new FermiNetMatrixFreeSrOptimizer.Configuration(
                0.01,
                1.0,
                10.0,
                2,
                128,
                50,
                1.0e-6,
                1.0e-8);
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
        return new Fixture(
                state,
                List.of(coordinates(0.0), coordinates(0.025)));
    }

    private static Molecule hydrogen() {
        return new Molecule(
                "ferminet-variational-optimizer-h2",
                List.of(
                        new NuclearCenter(
                                0,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(-0.7, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(0.7, 0.0, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static QuantumCoordinates coordinates(double shift) {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                0,
                -0.4 + shift,
                0.15,
                -0.1,
                SpinProjection.ALPHA));
        particles.add(new QuantumCoordinates.ParticleCoordinate(
                1,
                0.45 - shift,
                -0.12,
                0.08,
                SpinProjection.BETA));
        return new QuantumCoordinates(particles);
    }

    private static void assertLocalEnergiesExactly(
            List<LocalEnergyComponents> expected,
            List<LocalEnergyComponents> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            LocalEnergyComponents left = expected.get(index);
            LocalEnergyComponents right = actual.get(index);
            assertSameBits(left.kineticHartree(), right.kineticHartree());
            assertSameBits(left.electronNuclearHartree(), right.electronNuclearHartree());
            assertSameBits(left.electronElectronHartree(), right.electronElectronHartree());
            assertSameBits(left.nuclearNuclearHartree(), right.nuclearNuclearHartree());
            assertSameBits(left.totalHartree(), right.totalHartree());
        }
    }

    private static void assertCoordinatesExactly(
            List<QuantumCoordinates> expected,
            List<QuantumCoordinates> actual) {
        assertEquals(expected.size(), actual.size());
        for (int sample = 0; sample < expected.size(); sample++) {
            List<QuantumCoordinates.ParticleCoordinate> left =
                    expected.get(sample).particles();
            List<QuantumCoordinates.ParticleCoordinate> right =
                    actual.get(sample).particles();
            assertEquals(left.size(), right.size());
            for (int particle = 0; particle < left.size(); particle++) {
                assertEquals(left.get(particle).particleIndex(), right.get(particle).particleIndex());
                assertEquals(left.get(particle).spin(), right.get(particle).spin());
                assertSameBits(left.get(particle).xBohr(), right.get(particle).xBohr());
                assertSameBits(left.get(particle).yBohr(), right.get(particle).yBohr());
                assertSameBits(left.get(particle).zBohr(), right.get(particle).zBohr());
            }
        }
    }

    private static void assertSameBits(double expected, double actual) {
        assertEquals(
                Double.doubleToRawLongBits(expected),
                Double.doubleToRawLongBits(actual));
    }

    private record Fixture(
            FermiNetV1State state,
            List<QuantumCoordinates> walkers) {
    }
}
