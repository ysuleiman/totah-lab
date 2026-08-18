package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

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
 * Proves deterministic finite-path parity between sequential VMC and the
 * deterministic parallel implementation used by the canonical H2O SR driver.
 */
final class FermiNetVmcParallelParityTest {

    private record Transition(
            int walker,
            QuantumCoordinates proposal,
            long logUniformBits,
            long currentLogBits,
            long proposalLogBits,
            boolean accepted) {
    }

    @Test
    void parallelSamplerPreservesReferenceTrajectoryAndEnergies() {
        Fixture fixture = fixture();

        FermiNetVmc.Configuration configuration =
                new FermiNetVmc.Configuration(
                        fixture.walkers.size(),
                        4,
                        2,
                        3,
                        0.02,
                        20260824L);

        FermiNetVmc.Result sequential =
                new FermiNetVmc()
                        .sample(
                                fixture.state,
                                configuration,
                                fixture.walkers);

        int parallelism = Math.max(1,
                Math.min(12, Runtime.getRuntime().availableProcessors()));
        FermiNetVmc.Result parallel;
        try (FermiNetVmcParallel sampler = new FermiNetVmcParallel(parallelism)) {
            parallel = sampler.sample(fixture.state, configuration, fixture.walkers);
        }

        assertEquals(
                sequential.acceptance(),
                parallel.acceptance(),
                0.0,
                "acceptance must be bit-identical");

        assertEquals(
                sequential.samples().size(),
                parallel.samples().size());

        for (int sample = 0;
             sample < sequential.samples().size();
             sample++) {

            assertCoordinatesExactlyEqual(
                    sequential.samples().get(sample),
                    parallel.samples().get(sample),
                    sample);

            var expectedEnergy =
                    sequential.localEnergies().get(sample);

            var actualEnergy =
                    parallel.localEnergies().get(sample);

            assertEquals(
                    expectedEnergy.kineticHartree(),
                    actualEnergy.kineticHartree(),
                    0.0,
                    "kinetic mismatch sample=" + sample);

            assertEquals(
                    expectedEnergy.electronNuclearHartree(),
                    actualEnergy.electronNuclearHartree(),
                    0.0,
                    "electron-nuclear mismatch sample=" + sample);

            assertEquals(
                    expectedEnergy.electronElectronHartree(),
                    actualEnergy.electronElectronHartree(),
                    0.0,
                    "electron-electron mismatch sample=" + sample);

            assertEquals(
                    expectedEnergy.nuclearNuclearHartree(),
                    actualEnergy.nuclearNuclearHartree(),
                    0.0,
                    "nuclear-nuclear mismatch sample=" + sample);
        }

        System.out.printf(
                """
                FERMINET_VMC_PARALLEL_PARITY
                  walkers=%d
                  retained_samples=%d
                  parallelism=%d
                  acceptance=%.17g
                  coordinate_parity=BIT_EXACT
                  local_energy_parity=BIT_EXACT

                """,
                fixture.walkers.size(),
                sequential.samples().size(),
                parallelism,
                sequential.acceptance());
    }

    @Test
    void continuationIsBitExactToOneUninterruptedSamplingRun() {
        Fixture fixture = fixture();
        FermiNetVmc.Configuration configuration =
                new FermiNetVmc.Configuration(
                        fixture.walkers.size(),
                        4,
                        4,
                        3,
                        0.02,
                        20260824L);

        List<Transition> uninterruptedTrace = new ArrayList<>();
        List<Transition> splitTrace = new ArrayList<>();

        try (FermiNetVmcParallel sampler = new FermiNetVmcParallel(4)) {
            var uninterrupted = sampler.beginSession(
                    fixture.state,
                    configuration,
                    fixture.walkers);
            var whole = uninterrupted.sample(
                    fixture.state,
                    4,
                    4,
                    3,
                    observer(uninterruptedTrace));

            var split = sampler.beginSession(
                    fixture.state,
                    configuration,
                    fixture.walkers);
            var first = split.sample(
                    fixture.state,
                    4,
                    2,
                    3,
                    observer(splitTrace));
            var second = split.sample(
                    fixture.state,
                    0,
                    2,
                    3,
                    observer(splitTrace));

            assertEquals(whole.proposed(), first.proposed() + second.proposed());
            assertEquals(whole.accepted(), first.accepted() + second.accepted());
            assertEquals(uninterruptedTrace.size(), splitTrace.size());

            for (int transition = 0;
                 transition < uninterruptedTrace.size();
                 transition++) {
                Transition expected = uninterruptedTrace.get(transition);
                Transition actual = splitTrace.get(transition);
                assertEquals(expected.walker(), actual.walker());
                assertCoordinatesExactlyEqual(
                        expected.proposal(),
                        actual.proposal(),
                        transition);
                assertEquals(expected.logUniformBits(), actual.logUniformBits());
                assertEquals(expected.currentLogBits(), actual.currentLogBits());
                assertEquals(expected.proposalLogBits(), actual.proposalLogBits());
                assertEquals(expected.accepted(), actual.accepted());
            }

            List<QuantumCoordinates> combinedSamples = new ArrayList<>();
            combinedSamples.addAll(first.result().samples());
            combinedSamples.addAll(second.result().samples());
            assertCoordinateListsExactlyEqual(
                    whole.result().samples(),
                    combinedSamples);
            assertCoordinateListsExactlyEqual(
                    uninterrupted.currentWalkers(),
                    split.currentWalkers());
        }
    }

    @Test
    void parameterChangeRefreshesWalkerLogsWithoutRestartingRandomStream() {
        Fixture fixture = fixture();
        FermiNetVmc.Configuration configuration =
                new FermiNetVmc.Configuration(
                        fixture.walkers.size(),
                        0,
                        1,
                        1,
                        0.02,
                        20260824L);
        double[] changedValues = fixture.state.parameterArray();
        changedValues[0] += 0.01;
        FermiNetV1State changedState = fixture.state.withParameters(changedValues);

        List<Transition> unchangedTrace = new ArrayList<>();
        List<Transition> changedTrace = new ArrayList<>();
        List<Transition> replayTrace = new ArrayList<>();

        try (FermiNetVmcParallel sampler = new FermiNetVmcParallel(4)) {
            var unchanged = sampler.beginSession(
                    fixture.state,
                    configuration,
                    fixture.walkers);
            var changed = sampler.beginSession(
                    fixture.state,
                    configuration,
                    fixture.walkers);
            var replay = sampler.beginSession(
                    fixture.state,
                    configuration,
                    fixture.walkers);

            unchanged.sample(fixture.state, 0, 1, 1);
            changed.sample(fixture.state, 0, 1, 1);
            replay.sample(fixture.state, 0, 1, 1);

            List<QuantumCoordinates> theta0FinalWalkers = changed.currentWalkers();
            double[] theta0Logs = changed.currentWalkerLogs();
            changed.refreshState(changedState);

            assertCoordinateListsExactlyEqual(
                    theta0FinalWalkers,
                    changed.currentWalkers());
            double[] theta1Logs = changed.currentWalkerLogs();
            boolean anyLogChanged = false;
            for (int walker = 0; walker < theta1Logs.length; walker++) {
                double expected = changedState.samplingEvaluation(
                                theta0FinalWalkers.get(walker))
                        .logAbsoluteWavefunction();
                assertEquals(
                        Double.doubleToRawLongBits(expected),
                        Double.doubleToRawLongBits(theta1Logs[walker]));
                anyLogChanged |= Double.doubleToRawLongBits(theta0Logs[walker])
                        != Double.doubleToRawLongBits(theta1Logs[walker]);
            }
            assertTrue(anyLogChanged, "theta1 refresh must replace stale theta0 logs");

            unchanged.sample(
                    fixture.state,
                    0,
                    1,
                    1,
                    observer(unchangedTrace));
            var continued = changed.sample(
                    changedState,
                    0,
                    1,
                    1,
                    observer(changedTrace));
            replay.refreshState(changedState);
            var replayed = replay.sample(
                    changedState,
                    0,
                    1,
                    1,
                    observer(replayTrace));

            assertEquals(unchangedTrace.size(), changedTrace.size());
            for (int transition = 0;
                 transition < unchangedTrace.size();
                 transition++) {
                Transition expected = unchangedTrace.get(transition);
                Transition actual = changedTrace.get(transition);
                assertEquals(expected.walker(), actual.walker());
                assertCoordinatesExactlyEqual(
                        expected.proposal(),
                        actual.proposal(),
                        transition);
                assertEquals(expected.logUniformBits(), actual.logUniformBits());

                double expectedCurrentLog = changedState.samplingEvaluation(
                                theta0FinalWalkers.get(actual.walker()))
                        .logAbsoluteWavefunction();
                double expectedProposalLog = changedState.samplingEvaluation(
                                actual.proposal())
                        .logAbsoluteWavefunction();
                assertEquals(
                        Double.doubleToRawLongBits(expectedCurrentLog),
                        actual.currentLogBits());
                assertEquals(
                        Double.doubleToRawLongBits(expectedProposalLog),
                        actual.proposalLogBits());
                boolean expectedAccepted =
                        Double.longBitsToDouble(actual.logUniformBits())
                                < Math.min(
                                0.0,
                                2.0 * (expectedProposalLog - expectedCurrentLog));
                assertEquals(expectedAccepted, actual.accepted());
            }
            assertEquals(
                    FermiNetStateIdentity.of(changedState),
                    continued.result().stateIdentity());
            assertEquals(changedTrace, replayTrace);
            assertCoordinateListsExactlyEqual(
                    continued.result().samples(),
                    replayed.result().samples());
            assertCoordinateListsExactlyEqual(
                    changed.currentWalkers(),
                    replay.currentWalkers());
        }
    }

    private static FermiNetVmcParallel.TransitionObserver observer(
            List<Transition> transitions) {

        return (walker, proposal, logUniform, currentLog, proposalLog, accepted) ->
                transitions.add(
                        new Transition(
                                walker,
                                proposal,
                                Double.doubleToRawLongBits(logUniform),
                                Double.doubleToRawLongBits(currentLog),
                                Double.doubleToRawLongBits(proposalLog),
                                accepted));
    }

    private static void assertCoordinateListsExactlyEqual(
            List<QuantumCoordinates> expected,
            List<QuantumCoordinates> actual) {

        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertCoordinatesExactlyEqual(
                    expected.get(index),
                    actual.get(index),
                    index);
        }
    }

    private static void assertCoordinatesExactlyEqual(
            QuantumCoordinates expected,
            QuantumCoordinates actual,
            int sample) {

        assertEquals(
                expected.particles().size(),
                actual.particles().size());

        for (int i = 0;
             i < expected.particles().size();
             i++) {

            var left = expected.particles().get(i);
            var right = actual.particles().get(i);

            assertEquals(left.particleIndex(), right.particleIndex());
            assertEquals(left.spin(), right.spin());

            assertEquals(
                    Double.doubleToRawLongBits(left.xBohr()),
                    Double.doubleToRawLongBits(right.xBohr()),
                    "x mismatch sample=" + sample + " particle=" + i);

            assertEquals(
                    Double.doubleToRawLongBits(left.yBohr()),
                    Double.doubleToRawLongBits(right.yBohr()),
                    "y mismatch sample=" + sample + " particle=" + i);

            assertEquals(
                    Double.doubleToRawLongBits(left.zBohr()),
                    Double.doubleToRawLongBits(right.zBohr()),
                    "z mismatch sample=" + sample + " particle=" + i);
        }
    }

    private static Fixture fixture() {
        Molecule molecule = water();

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.initialize(
                                new FermiNetParameterLayout(
                                        configuration,
                                        molecule),
                                44017L));

        List<QuantumCoordinates> walkers =
                List.of(
                        coordinates(0.000),
                        coordinates(0.011),
                        coordinates(-0.017),
                        coordinates(0.024),
                        coordinates(-0.031),
                        coordinates(0.038),
                        coordinates(-0.044),
                        coordinates(0.051));

        return new Fixture(state, walkers);
    }

    private static QuantumCoordinates coordinates(double shift) {
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

        return new QuantumCoordinates(result);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-vmc-parallel-test-water",
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
            List<QuantumCoordinates> walkers) {
    }
}
