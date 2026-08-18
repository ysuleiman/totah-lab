package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

final class ReferenceFermiNetPretrainerTest {

    @Test
    void occupiedOrbitalLossGradientMatchesFiniteDifference() {

        FermiNetV1State state =
                state(41L);

        QuantumCoordinates coordinates =
                coordinates();

        HartreeFockOrbitalTarget.OrbitalMatrices target =
                target().evaluate(coordinates);

        var evaluated =
                state.pretrainingEvaluation(
                        coordinates,
                        target.alpha(),
                        target.beta());

        int[] selected = {
                0,
                state.parameterCount() / 2,
                state.parameterCount() - 1
        };

        double step =
                1.0e-6;

        for (int index : selected) {

            double plus =
                    state.withParameter(
                                    index,
                                    state.parameter(index) + step)
                            .pretrainingEvaluation(
                                    coordinates,
                                    target.alpha(),
                                    target.beta())
                            .loss();

            double minus =
                    state.withParameter(
                                    index,
                                    state.parameter(index) - step)
                            .pretrainingEvaluation(
                                    coordinates,
                                    target.alpha(),
                                    target.beta())
                            .loss();

            double finiteDifference =
                    (plus - minus)
                            / (2.0 * step);

            assertEquals(
                    finiteDifference,
                    evaluated.gradient()[index],
                    2.0e-6,
                    "parameter " + index);
        }
    }

    @Test
    void deterministicPretrainingLifecycleIsCompleteAndReproducible() {

        FermiNetV1State initial =
                state(77L);

        HartreeFockOrbitalTarget target =
                target();

        double[] initialParameters =
                initial.parameterArray();

        var configuration =
                new ReferenceFermiNetPretrainer.Configuration(
                        3,
                        4,
                        3.0e-4,
                        0.02,
                        1.0,
                        1.0,
                        991L);

        var first =
                new ReferenceFermiNetPretrainer()
                        .train(
                                initial,
                                target,
                                configuration);

        var second =
                new ReferenceFermiNetPretrainer()
                        .train(
                                initial,
                                target,
                                configuration);

        /*
         * ------------------------------------------------------------
         * Iteration lifecycle
         * ------------------------------------------------------------
         */
        assertEquals(
                3,
                first.lossHistory().size());

        for (double loss : first.lossHistory()) {
            assertTrue(
                    Double.isFinite(loss),
                    "loss must remain finite");
        }

        /*
         * ------------------------------------------------------------
         * Parameters must actually update
         * ------------------------------------------------------------
         */
        double[] finalParameters =
                first.state().parameterArray();

        assertEquals(
                initialParameters.length,
                finalParameters.length);

        boolean anyChanged =
                false;

        for (int i = 0;
             i < initialParameters.length;
             i++) {

            if (Double.doubleToLongBits(
                    initialParameters[i])
                    != Double.doubleToLongBits(
                    finalParameters[i])) {

                anyChanged =
                        true;

                break;
            }
        }

        assertTrue(
                anyChanged,
                "pretraining must update at least one parameter");

        /*
         * ------------------------------------------------------------
         * Walker lifecycle
         * ------------------------------------------------------------
         */
        assertEquals(
                configuration.walkers(),
                first.walkers().size());

        for (QuantumCoordinates walker :
                first.walkers()) {

            assertEquals(
                    2,
                    walker.particles().size());

            assertEquals(
                    SpinProjection.ALPHA,
                    walker.particles()
                            .get(0)
                            .spin());

            assertEquals(
                    SpinProjection.BETA,
                    walker.particles()
                            .get(1)
                            .spin());

            for (var electron :
                    walker.particles()) {

                assertTrue(
                        Double.isFinite(
                                electron.xBohr()));

                assertTrue(
                        Double.isFinite(
                                electron.yBohr()));

                assertTrue(
                        Double.isFinite(
                                electron.zBohr()));
            }
        }

        /*
         * ------------------------------------------------------------
         * Acceptance
         * ------------------------------------------------------------
         */
        assertTrue(
                Double.isFinite(
                        first.acceptance()));

        assertTrue(
                first.acceptance() >= 0.0);

        assertTrue(
                first.acceptance() <= 1.0);

        /*
         * ------------------------------------------------------------
         * Provenance
         * ------------------------------------------------------------
         */
        assertEquals(
                ReferenceFermiNetPretrainer.REFERENCE_COMMIT,
                first.referenceCommit());

        assertEquals(
                "FERMINET_OCCUPIED_ORBITAL_MSE_ADAM_WITH_PER_STEP_RWM",
                first.algorithm());

        /*
         * ------------------------------------------------------------
         * Deterministic replay
         * ------------------------------------------------------------
         */
        assertEquals(
                first.lossHistory(),
                second.lossHistory());

        assertEquals(
                first.acceptance(),
                second.acceptance());

        assertArrayEquals(
                first.state().parameterArray(),
                second.state().parameterArray());

        assertEquals(
                first.walkers().size(),
                second.walkers().size());

        for (int walker = 0;
             walker < first.walkers().size();
             walker++) {

            assertCoordinatesExactlyEqual(
                    first.walkers().get(walker),
                    second.walkers().get(walker));
        }
    }

    @Test
    void pretrainingReducesOccupiedOrbitalLossOnDeterministicFixture() {

        FermiNetV1State initial =
                state(77L);

        QuantumCoordinates coordinates =
                coordinates();

        HartreeFockOrbitalTarget target =
                target();

        var matrices =
                target.evaluate(
                        coordinates);

        double before =
                initial.pretrainingEvaluation(
                                coordinates,
                                matrices.alpha(),
                                matrices.beta())
                        .loss();

        var configuration =
                new ReferenceFermiNetPretrainer.Configuration(
                        40,
                        4,
                        3.0e-4,
                        0.02,
                        1.0,
                        1.0,
                        991L);

        var result =
                new ReferenceFermiNetPretrainer()
                        .train(
                                initial,
                                target,
                                configuration);

        double after =
                result.state()
                        .pretrainingEvaluation(
                                coordinates,
                                matrices.alpha(),
                                matrices.beta())
                        .loss();

        assertTrue(
                after < before,
                "HF occupied-orbital MSE should decrease");

        assertEquals(
                40,
                result.lossHistory().size());

        assertTrue(
                Double.isFinite(
                        result.acceptance()));
    }

    @Test
    void zeroIterationRunLeavesParametersUntouchedAndMakesNoProposals() {

        FermiNetV1State initial =
                state(123L);

        double[] before =
                initial.parameterArray();

        var configuration =
                new ReferenceFermiNetPretrainer.Configuration(
                        0,
                        3,
                        3.0e-4,
                        0.02,
                        1.0,
                        1.0,
                        456L);

        var result =
                new ReferenceFermiNetPretrainer()
                        .train(
                                initial,
                                target(),
                                configuration);

        assertTrue(
                result.lossHistory().isEmpty());

        assertArrayEquals(
                before,
                result.state().parameterArray());

        assertTrue(
                Double.isNaN(
                        result.acceptance()));

        /*
         * Initialization still occurs even though no optimization step runs.
         */
        assertEquals(
                3,
                result.walkers().size());

        for (QuantumCoordinates walker :
                result.walkers()) {

            assertEquals(
                    SpinProjection.ALPHA,
                    walker.particles()
                            .get(0)
                            .spin());

            assertEquals(
                    SpinProjection.BETA,
                    walker.particles()
                            .get(1)
                            .spin());
        }
    }

    private static void assertCoordinatesExactlyEqual(
            QuantumCoordinates expected,
            QuantumCoordinates actual) {

        assertEquals(
                expected.particles().size(),
                actual.particles().size());

        for (int i = 0;
             i < expected.particles().size();
             i++) {

            var e =
                    expected.particles().get(i);

            var a =
                    actual.particles().get(i);

            assertEquals(
                    e.particleIndex(),
                    a.particleIndex());

            assertEquals(
                    e.spin(),
                    a.spin());

            assertEquals(
                    e.xBohr(),
                    a.xBohr());

            assertEquals(
                    e.yBohr(),
                    a.yBohr());

            assertEquals(
                    e.zBohr(),
                    a.zBohr());
        }
    }

    private static HartreeFockOrbitalTarget target() {

        return coordinates -> {

            double[][] alpha = {
                    {
                            bonding(
                                    coordinates.particles()
                                            .get(0))
                    }
            };

            double[][] beta = {
                    {
                            bonding(
                                    coordinates.particles()
                                            .get(1))
                    }
            };

            return new HartreeFockOrbitalTarget.OrbitalMatrices(
                    alpha,
                    beta);
        };
    }

    private static double bonding(
            QuantumCoordinates.ParticleCoordinate electron) {

        return Math.exp(
                -distance(
                        electron,
                        -0.7))
                + Math.exp(
                -distance(
                        electron,
                        0.7));
    }

    private static double distance(
            QuantumCoordinates.ParticleCoordinate electron,
            double z) {

        double dz =
                electron.zBohr()
                        - z;

        return Math.sqrt(
                electron.xBohr()
                        * electron.xBohr()
                        + electron.yBohr()
                        * electron.yBohr()
                        + dz
                        * dz);
    }

    private static FermiNetV1State state(
            long seed) {

        Molecule molecule =
                molecule();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        return new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        layout,
                        seed));
    }

    private static QuantumCoordinates coordinates() {

        return new QuantumCoordinates(
                List.of(
                        new QuantumCoordinates.ParticleCoordinate(
                                0,
                                0.2,
                                -0.1,
                                -0.5,
                                SpinProjection.ALPHA),
                        new QuantumCoordinates.ParticleCoordinate(
                                1,
                                -0.3,
                                0.15,
                                0.45,
                                SpinProjection.BETA)));
    }

    private static Molecule molecule() {

        return new Molecule(
                "h2-pretrain-fixture",
                List.of(
                        new NuclearCenter(
                                0,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        -0.7,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.7,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(2),
                new SpinSector(
                        1,
                        1,
                        1));
    }
}