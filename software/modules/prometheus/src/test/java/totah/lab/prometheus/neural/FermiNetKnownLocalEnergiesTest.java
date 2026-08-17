package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.ArrayList;
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

final class FermiNetKnownLocalEnergiesTest {

    @Test
    void knownEnergyPathIsExactlyIdenticalToComputedEnergyPath() throws IOException {
        Fixture fixture = fixture();
        FermiNetKnownLocalEnergies known = known(fixture.state(), fixture.coordinates());

        try (FermiNetStructuredSrObservationFile computed =
                     FermiNetStructuredSrObservationFile.buildParallel(
                             fixture.state(), fixture.samples(), 2);
             FermiNetStructuredSrObservationFile reused =
                     FermiNetStructuredSrObservationFile.buildParallel(
                             fixture.state(), fixture.samples(), known, 2)) {
            assertEquals(0, computed.reusedLocalEnergyCount());
            assertEquals(fixture.samples().size(), reused.reusedLocalEnergyCount());
            for (int i = 0; i < fixture.samples().size(); i++) {
                assertBits(computed.weight(i), reused.weight(i));
                assertBits(computed.localEnergyHartree(i), reused.localEnergyHartree(i));
            }
            for (FermiNetStructuredSrStatistics.Family family
                    : computed.schema().families()) {
                assertArrayBits(computed.readFamily(family), reused.readFamily(family));
            }
            assertArrayBits(q(computed), q(reused));

            var computedSolve = new FermiNetStructuredSampleSpaceSrSolver()
                    .solve(computed, 1.0);
            var reusedSolve = new FermiNetStructuredSampleSpaceSrSolver()
                    .solve(reused, 1.0);
            assertBits(computedSolve.meanEnergyHartree(), reusedSolve.meanEnergyHartree());
            assertArrayBits(computedSolve.centeredDampedGram(),
                    reusedSolve.centeredDampedGram());
            assertArrayBits(computedSolve.energyGradient(), reusedSolve.energyGradient());
            assertArrayBits(computedSolve.delta(), reusedSolve.delta());
            assertBits(computedSolve.absoluteSampleSpaceResidual(),
                    reusedSolve.absoluteSampleSpaceResidual());
            assertBits(computedSolve.relativeSampleSpaceResidual(),
                    reusedSolve.relativeSampleSpaceResidual());
        }

        var configuration = new FermiNetMatrixFreeSrOptimizer.Configuration(
                0.01, 1.0, 0.05, 2, 128, 50, 1.0e-6, 1.0e-8);
        var computed = new FermiNetMatrixFreeSrOptimizer()
                .oneIteration(fixture.state(), fixture.samples(), configuration);
        var reused = new FermiNetMatrixFreeSrOptimizer()
                .oneIteration(fixture.state(), fixture.samples(), known, configuration);
        assertBits(computed.initialEnergyHartree(), reused.initialEnergyHartree());
        assertArrayBits(computed.energyGradient(), reused.energyGradient());
        assertBits(computed.gradientNorm(), reused.gradientNorm());
        assertBits(computed.rawUpdateNorm(), reused.rawUpdateNorm());
        assertBits(computed.appliedUpdateNorm(), reused.appliedUpdateNorm());
        assertBits(computed.relativeTrueResidual(), reused.relativeTrueResidual());
        assertArrayBits(computed.state().parameterArray(), reused.state().parameterArray());
    }

    @Test
    void knownEnergiesFailClosedOnEveryIdentityMismatch() {
        Fixture fixture = fixture();
        FermiNetKnownLocalEnergies known = known(fixture.state(), fixture.coordinates());

        assertThrows(IllegalArgumentException.class,
                () -> known.validate(fixture.state(), fixture.samples().subList(0, 3)));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> reordered =
                new ArrayList<>(fixture.samples());
        var first = reordered.get(0);
        reordered.set(0, reordered.get(1));
        reordered.set(1, first);
        assertThrows(IllegalArgumentException.class,
                () -> known.validate(fixture.state(), reordered));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> oneBitChanged =
                new ArrayList<>(fixture.samples());
        oneBitChanged.set(0, new FermiNetMatrixFreeSrOptimizer.WeightedSample(
                1.0, changeOneRawBit(fixture.coordinates().get(0))));
        assertThrows(IllegalArgumentException.class,
                () -> known.validate(fixture.state(), oneBitChanged));

        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> duplicate =
                new ArrayList<>(fixture.samples());
        duplicate.set(1, duplicate.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> known.validate(fixture.state(), duplicate));

        FermiNetV1State theta1 = fixture.state().withParameter(
                0, Math.nextUp(fixture.state().parameter(0)));
        assertThrows(IllegalArgumentException.class,
                () -> known.validate(theta1, fixture.samples()));

        FermiNetVmc.Result wrongIdentity = new FermiNetVmc.Result(
                fixture.coordinates(), 0.5, energies(fixture.state(), fixture.coordinates()),
                FermiNetStateIdentity.of(theta1));
        assertThrows(IllegalArgumentException.class,
                () -> FermiNetKnownLocalEnergies.from(fixture.state(), wrongIdentity));

        assertThrows(IllegalArgumentException.class,
                () -> new LocalEnergyComponents(Double.NaN, 0.0, 0.0, 0.0));
    }

    private static FermiNetKnownLocalEnergies known(
            FermiNetV1State state,
            List<QuantumCoordinates> coordinates) {
        return FermiNetKnownLocalEnergies.from(state, new FermiNetVmc.Result(
                coordinates, 0.5, energies(state, coordinates),
                FermiNetStateIdentity.of(state)));
    }

    private static List<LocalEnergyComponents> energies(
            FermiNetV1State state,
            List<QuantumCoordinates> coordinates) {
        return coordinates.stream().map(c -> FermiNetVmc.localEnergy(state, c)).toList();
    }

    private static QuantumCoordinates changeOneRawBit(QuantumCoordinates coordinates) {
        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(coordinates.particles());
        var first = particles.get(0);
        particles.set(0, new QuantumCoordinates.ParticleCoordinate(
                first.particleIndex(), Math.nextUp(first.xBohr()), first.yBohr(),
                first.zBohr(), first.spin()));
        return new QuantumCoordinates(particles);
    }

    private static void assertBits(double expected, double actual) {
        assertEquals(Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(actual));
    }

    private static void assertArrayBits(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) assertBits(expected[i], actual[i]);
    }

    private static double[] q(FermiNetStructuredSrObservationFile observations) {
        double weightSum = 0.0;
        double weightedEnergy = 0.0;
        for (int i = 0; i < observations.sampleCount(); i++) {
            weightSum += observations.weight(i);
            weightedEnergy += observations.weight(i) * observations.localEnergyHartree(i);
        }
        double mean = weightedEnergy / weightSum;
        double[] result = new double[observations.sampleCount()];
        for (int i = 0; i < result.length; i++) {
            double normalized = observations.weight(i) / weightSum;
            result[i] = 2.0 * Math.sqrt(normalized)
                    * (observations.localEnergyHartree(i) - mean);
        }
        return result;
    }

    private static Fixture fixture() {
        Molecule molecule = water();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetV1State state = new FermiNetV1State(
                molecule, configuration, FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule), 44017L));
        List<QuantumCoordinates> coordinates = List.of(
                coordinates(0.0), coordinates(0.011), coordinates(-0.017), coordinates(0.024));
        List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples = coordinates.stream()
                .map(c -> new FermiNetMatrixFreeSrOptimizer.WeightedSample(1.0, c))
                .toList();
        return new Fixture(state, coordinates, samples);
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {{.18,.11,.27},{-.31,.42,-.16},{.57,-.28,.33},{-.63,-.37,.21},
                {.24,.71,-.45},{-.22,-.15,-.38},{.36,-.54,.19},{-.48,.26,.51},
                {.69,.18,-.24},{-.12,.61,.37}};
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            double signed = i % 2 == 0 ? shift : -shift;
            particles.add(new QuantumCoordinates.ParticleCoordinate(i,
                    xyz[i][0] + signed, xyz[i][1] - .5 * signed,
                    xyz[i][2] + .25 * signed,
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(particles);
    }

    private static Molecule water() {
        return new Molecule("known-energy-test-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8),
                        new CartesianPosition(0, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(1.7952398191849366, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(2, "H", new NuclearCharge(1),
                        new CartesianPosition(-.46464225035067114, 1.7340684963325879,
                                0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }

    private record Fixture(
            FermiNetV1State state,
            List<QuantumCoordinates> coordinates,
            List<FermiNetMatrixFreeSrOptimizer.WeightedSample> samples) {}
}
