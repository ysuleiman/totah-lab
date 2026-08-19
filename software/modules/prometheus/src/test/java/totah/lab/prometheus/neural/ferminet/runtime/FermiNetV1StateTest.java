package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

final class FermiNetV1StateTest {

    private static final double COORDINATE_FIRST_STEP = 2.0e-4;

    /*
     * Second derivatives are more sensitive to roundoff and high curvature
     * than first derivatives.  A larger displacement is appropriate for the
     * independent five-point wavefunction-Laplacian check.
     */
    private static final double LAPLACIAN_STEP = 1.0e-3;

    private static final double PARAMETER_STEP = 1.0e-5;

    private static final double NUCLEAR_STEP = 1.0e-5;

    @Test
    void geometryCopyPreservesStateAndDisplacementPreservesParameters() {

        Molecule water = water();
        FermiNetV1State state = state(water);
        QuantumCoordinates coordinates = coordinates();

        Molecule copied = moveNucleus(water, 0, 0, 0.0);
        FermiNetV1State copiedState = FermiNetStateAccess.withGeometry(state, copied);

        var expected = state.evaluate(coordinates);
        var actual = copiedState.evaluate(coordinates);

        assertEquals(expected.sign(), actual.sign());
        assertEquals(Double.doubleToRawLongBits(expected.logAbsoluteWavefunction()),
                Double.doubleToRawLongBits(actual.logAbsoluteWavefunction()));
        assertArrayEquals(expected.logCoordinateGradient(), actual.logCoordinateGradient(), 0.0);
        assertEquals(Double.doubleToRawLongBits(expected.laplacianOverWavefunction()),
                Double.doubleToRawLongBits(actual.laplacianOverWavefunction()));
        assertEquals(FermiNetRuntimeSampling.localEnergy(state, coordinates),
                FermiNetRuntimeSampling.localEnergy(copiedState, coordinates));

        String checksum = parameterChecksum(state);
        assertEquals(checksum, parameterChecksum(copiedState));

        FermiNetV1State plus = FermiNetStateAccess.withGeometry(
                state, moveNucleus(water, 1, 0, NUCLEAR_STEP));
        FermiNetV1State minus = FermiNetStateAccess.withGeometry(
                state, moveNucleus(water, 1, 0, -NUCLEAR_STEP));
        assertEquals(checksum, parameterChecksum(plus));
        assertEquals(checksum, parameterChecksum(minus));
        assertNotEquals(water.scientificIdentity(), plus.molecule().scientificIdentity());
        assertNotEquals(water.scientificIdentity(), minus.molecule().scientificIdentity());
    }

    @Test
    void nuclearLogDerivativesMatchFrozenParameterCentralDifferences() {

        Molecule water = water();
        FermiNetV1State state = state(water);
        String checksum = parameterChecksum(state);

        List<QuantumCoordinates> configurations = List.of(
                coordinates(),
                move(coordinates(), 0, 0.073),
                move(move(coordinates(), 8, -0.051), 21, 0.037));

        double maximumAbsoluteError = 0.0;

        for (int configuration = 0;
             configuration < configurations.size();
             configuration++) {

            QuantumCoordinates electrons = configurations.get(configuration);
            double[] analytic = FermiNetStateAccess.nuclear(state, electrons)
                    .logNuclearGradient();
            assertEquals(3 * water.nuclei().size(), analytic.length);

            for (int component = 0;
                 component < analytic.length;
                 component++) {

                int nucleus = component / 3;
                int axis = component % 3;
                FermiNetV1State plus = FermiNetStateAccess.withGeometry(
                        state, moveNucleus(water, nucleus, axis, NUCLEAR_STEP));
                FermiNetV1State minus = FermiNetStateAccess.withGeometry(
                        state, moveNucleus(water, nucleus, axis, -NUCLEAR_STEP));

                assertEquals(checksum, parameterChecksum(plus));
                assertEquals(checksum, parameterChecksum(minus));

                double finiteDifference = (
                        FermiNetStateAccess.sampling(plus, electrons)
                                .logAbsoluteWavefunction()
                                - FermiNetStateAccess.sampling(minus, electrons)
                                .logAbsoluteWavefunction())
                        / (2.0 * NUCLEAR_STEP);

                maximumAbsoluteError = Math.max(
                        maximumAbsoluteError,
                        Math.abs(finiteDifference - analytic[component]));

                assertEquals(finiteDifference, analytic[component], 2.0e-7,
                        "configuration " + configuration + ", component " + component);
            }
        }

        System.out.printf(java.util.Locale.ROOT,
                "FERMINET_NUCLEAR_DERIVATIVE_MAX_ABS_ERROR=%.16e%n",
                maximumAbsoluteError);
    }

    @Test
    void directionalLogAndLaplacianMatchCentralDifferences() {
        Molecule molecule = water();
        FermiNetV1State state = state(molecule);
        List<QuantumCoordinates> configurations = List.of(
                coordinates(),
                move(coordinates(), 2, 0.031),
                move(move(coordinates(), 12, -0.027), 25, 0.019));
        double[][] nuclearDirections = {
                {0.13, -0.07, 0.03, -0.05, 0.11, -0.02, 0.04, -0.09, 0.06},
                new double[9],
                {0.08, 0.02, -0.04, -0.03, 0.07, 0.01, -0.05, -0.02, 0.03}
        };
        double[][] electronDirections = {
                new double[30],
                deterministicDirection(30, 0.017),
                deterministicDirection(30, -0.011)
        };
        double h = 2.0e-5;
        double maximumLogAbsoluteError = 0.0;
        double maximumLogRelativeError = 0.0;
        double maximumLaplacianAbsoluteError = 0.0;
        double maximumLaplacianRelativeError = 0.0;

        for (QuantumCoordinates coordinates : configurations) {
            for (int direction = 0; direction < nuclearDirections.length; direction++) {
                double[] nuclear = nuclearDirections[direction];
                double[] electron = electronDirections[direction];
                var actual = FermiNetStateAccess.directional(
                        state, coordinates,
                        new FermiNetStateAccess.NuclearDirection(nuclear),
                        new FermiNetStateAccess.ElectronDirection(electron));
                FermiNetV1State plusState = FermiNetStateAccess.withGeometry(
                        state, moveNuclei(molecule, nuclear, h));
                FermiNetV1State minusState = FermiNetStateAccess.withGeometry(
                        state, moveNuclei(molecule, nuclear, -h));
                QuantumCoordinates plusCoordinates = moveElectrons(coordinates, electron, h);
                QuantumCoordinates minusCoordinates = moveElectrons(coordinates, electron, -h);
                var plus = FermiNetStateAccess.spatial(plusState, plusCoordinates);
                var minus = FermiNetStateAccess.spatial(minusState, minusCoordinates);
                double expectedLog = (plus.logAbsoluteWavefunction()
                        - minus.logAbsoluteWavefunction()) / (2.0 * h);
                double expectedLaplacian = (plus.laplacianOverWavefunction()
                        - minus.laplacianOverWavefunction()) / (2.0 * h);
                double logError = Math.abs(actual.directionalLogAbsoluteWavefunction()
                        - expectedLog);
                double laplacianError = Math.abs(actual.directionalLaplacianOverWavefunction()
                        - expectedLaplacian);
                maximumLogAbsoluteError = Math.max(maximumLogAbsoluteError, logError);
                maximumLogRelativeError = Math.max(maximumLogRelativeError,
                        relativeError(logError, expectedLog));
                maximumLaplacianAbsoluteError = Math.max(
                        maximumLaplacianAbsoluteError, laplacianError);
                maximumLaplacianRelativeError = Math.max(
                        maximumLaplacianRelativeError,
                        relativeError(laplacianError, expectedLaplacian));
                assertEquals(expectedLog, actual.directionalLogAbsoluteWavefunction(), 2.0e-7);
                assertEquals(expectedLaplacian,
                        actual.directionalLaplacianOverWavefunction(), 2.0e-5);
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "FERMINET_DIRECTIONAL_LOG_MAX_ABS_ERROR=%.16e%n"
                        + "FERMINET_DIRECTIONAL_LOG_MAX_REL_ERROR=%.16e%n"
                        + "FERMINET_DIRECTIONAL_LAPLACIAN_MAX_ABS_ERROR=%.16e%n"
                        + "FERMINET_DIRECTIONAL_LAPLACIAN_MAX_REL_ERROR=%.16e%n",
                maximumLogAbsoluteError, maximumLogRelativeError,
                maximumLaplacianAbsoluteError, maximumLaplacianRelativeError);
    }

    @Test
    void commonTranslationAndPlanarReflectionTransformNuclearDerivatives() {

        Molecule water = water();
        FermiNetV1State state = state(water);
        QuantumCoordinates coordinates = coordinates();
        double[] translation = {0.31, -0.27, 0.19};

        FermiNetV1State translatedState = FermiNetStateAccess.withGeometry(
                state, translate(water, translation));
        QuantumCoordinates translatedCoordinates = translate(coordinates, translation);

        var original = FermiNetStateAccess.nuclear(state, coordinates);
        var translated = FermiNetStateAccess.nuclear(
                translatedState, translatedCoordinates);
        assertEquals(original.logAbsoluteWavefunction(),
                translated.logAbsoluteWavefunction(), 1.0e-12);
        assertArrayEquals(original.logNuclearGradient(),
                translated.logNuclearGradient(), 1.0e-12);

        FermiNetV1State planarState = planarReflectionSymmetricState(water);
        var unreflected = FermiNetStateAccess.nuclear(planarState, coordinates);
        var reflected = FermiNetStateAccess.nuclear(planarState, reflectZ(coordinates));
        assertEquals(unreflected.logAbsoluteWavefunction(),
                reflected.logAbsoluteWavefunction(), 2.0e-13);
        for (int nucleus = 0; nucleus < water.nuclei().size(); nucleus++) {
            assertEquals(unreflected.logNuclearGradient()[3 * nucleus],
                    reflected.logNuclearGradient()[3 * nucleus], 2.0e-12);
            assertEquals(unreflected.logNuclearGradient()[3 * nucleus + 1],
                    reflected.logNuclearGradient()[3 * nucleus + 1], 2.0e-12);
            assertEquals(-unreflected.logNuclearGradient()[3 * nucleus + 2],
                    reflected.logNuclearGradient()[3 * nucleus + 2], 2.0e-12);
        }
    }

    @Test
    void displacedGeometryRejectsChangedMolecularTopology() {

        Molecule water = water();
        FermiNetV1State state = state(water);
        List<NuclearCenter> nuclei = new ArrayList<>(water.nuclei());
        NuclearCenter hydrogen = nuclei.get(1);
        nuclei.set(1, new NuclearCenter(1, "He", new NuclearCharge(2),
                hydrogen.position()));
        Molecule incompatible = new Molecule(water.moleculeId(), nuclei,
                new MolecularCharge(1), water.electrons(), water.spin());

        assertThrows(IllegalArgumentException.class,
                () -> FermiNetStateAccess.withGeometry(state, incompatible));
    }

    @Test
    void reducedNetworkCoordinateAndParameterDerivativesMatchFiniteDifferences() {

        Molecule water = water();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        water);

        var state =
                new FermiNetV1State(
                        water,
                        configuration,
                        FermiNetParameters.initialize(
                                layout,
                                44017L));

        QuantumCoordinates coordinates =
                coordinates();

        var actual =
                state.evaluate(coordinates);

        assertFiniteComplete(
                actual,
                state.parameterCount(),
                30);

        verifyCoordinateGradient(
                state,
                coordinates,
                actual);

        verifyWavefunctionLaplacian(
                state,
                coordinates,
                actual);

        verifyParameterGradient(
                state,
                coordinates,
                actual,
                layout);
    }

    @Test
    void parameterDerivativeRangesReconstructFullVectorBitExactly() {

        Molecule water =
                water();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        water);

        var state =
                new FermiNetV1State(
                        water,
                        configuration,
                        FermiNetParameters.initialize(
                                layout,
                                44017L));

        QuantumCoordinates coordinates =
                coordinates();

        double[] full =
                state.evaluate(coordinates)
                        .parameterLogDerivatives();

        for (int chunkSize :
                new int[]{1, 128, 8192, 127}) {

            double[] reconstructed =
                    new double[full.length];

            for (int start = 0;
                 start < full.length;
                 start += chunkSize) {

                int length =
                        Math.min(
                                chunkSize,
                                full.length - start);

                double[] chunk =
                        new double[length];

                state.parameterLogDerivatives(
                        coordinates,
                        start,
                        length,
                        chunk);

                System.arraycopy(
                        chunk,
                        0,
                        reconstructed,
                        start,
                        length);
            }

            int bitMismatches =
                    0;

            for (int i = 0;
                 i < full.length;
                 i++) {

                if (Double.doubleToLongBits(full[i])
                        != Double.doubleToLongBits(reconstructed[i])) {

                    bitMismatches++;
                }
            }

            assertEquals(
                    0,
                    bitMismatches,
                    "chunk size " + chunkSize);
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> state.parameterLogDerivatives(
                        coordinates,
                        -1,
                        1,
                        new double[1]));

        assertThrows(
                IllegalArgumentException.class,
                () -> state.parameterLogDerivatives(
                        coordinates,
                        0,
                        0,
                        new double[1]));

        assertThrows(
                IllegalArgumentException.class,
                () -> state.parameterLogDerivatives(
                        coordinates,
                        full.length - 1,
                        2,
                        new double[2]));

        assertThrows(
                IllegalArgumentException.class,
                () -> state.parameterLogDerivatives(
                        coordinates,
                        0,
                        2,
                        new double[1]));
    }

    @Test
    void sameSpinExchangeFlipsSignAndPreservesLogMagnitude() {

        Molecule water =
                water();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        water);

        var state =
                new FermiNetV1State(
                        water,
                        configuration,
                        FermiNetParameters.initialize(
                                layout,
                                18L));

        QuantumCoordinates coordinates =
                coordinates();

        var first =
                state.evaluate(coordinates);

        var exchanged =
                state.evaluate(
                        swap(
                                coordinates,
                                0,
                                1));

        assertEquals(
                -first.sign(),
                exchanged.sign());

        assertEquals(
                first.logAbsoluteWavefunction(),
                exchanged.logAbsoluteWavefunction(),
                1.0e-10);
    }

    @Test
    void lockedH2oNetworkReturnsCompleteFiniteDeterministicEvaluation() {

        Molecule water =
                water();

        var configuration =
                FermiNetV1Configuration.locked();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        water);

        var state =
                new FermiNetV1State(
                        water,
                        configuration,
                        FermiNetParameters.initialize(
                                layout,
                                20260815L));

        var first =
                state.evaluate(
                        coordinates());

        var second =
                state.evaluate(
                        coordinates());

        assertFiniteComplete(
                first,
                state.parameterCount(),
                30);

        assertEquals(
                layout.parameterCount(),
                state.parameterCount());

        assertEquals(
                first.sign(),
                second.sign());

        assertEquals(
                first.logAbsoluteWavefunction(),
                second.logAbsoluteWavefunction());

        assertEquals(
                first.laplacianOverWavefunction(),
                second.laplacianOverWavefunction());

        assertArrayEquals(
                first.logCoordinateGradient(),
                second.logCoordinateGradient());

        assertArrayEquals(
                first.parameterLogDerivatives(),
                second.parameterLogDerivatives());
    }

    private static void verifyCoordinateGradient(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetV1State.Evaluation actual) {

        double h =
                COORDINATE_FIRST_STEP;

        for (int axis = 0; axis < 30; axis++) {

            var plus2 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    2.0 * h));

            var plus1 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    h));

            var minus1 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    -h));

            var minus2 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    -2.0 * h));

            /*
             * Keep the entire finite-difference stencil in one nodal sector.
             */
            assertEquals(
                    actual.sign(),
                    plus2.sign(),
                    "coordinate " + axis + " +2h sign");

            assertEquals(
                    actual.sign(),
                    plus1.sign(),
                    "coordinate " + axis + " +h sign");

            assertEquals(
                    actual.sign(),
                    minus1.sign(),
                    "coordinate " + axis + " -h sign");

            assertEquals(
                    actual.sign(),
                    minus2.sign(),
                    "coordinate " + axis + " -2h sign");

            /*
             * Five-point first derivative:
             *
             * [-f(x+2h) + 8 f(x+h)
             *  -8 f(x-h) + f(x-2h)] / 12h
             */
            double finiteDifference =
                    (
                            -plus2.logAbsoluteWavefunction()
                                    + 8.0 * plus1.logAbsoluteWavefunction()
                                    - 8.0 * minus1.logAbsoluteWavefunction()
                                    + minus2.logAbsoluteWavefunction()
                    )
                            / (12.0 * h);

            assertEquals(
                    finiteDifference,
                    actual.logCoordinateGradient()[axis],
                    2.0e-5,
                    "coordinate " + axis);
        }
    }

    private static void verifyWavefunctionLaplacian(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetV1State.Evaluation center) {

        double h =
                LAPLACIAN_STEP;

        double finiteDifferenceLaplacianOverPsi =
                0.0;

        /*
         * Validate nabla^2 Psi / Psi directly.
         *
         * We never materialize Psi itself.
         *
         * Instead:
         *
         * Psi(x+d) / Psi(x)
         *
         *   = sign(x+d)/sign(x)
         *     * exp(log|Psi(x+d)| - log|Psi(x)|)
         *
         * which remains numerically stable.
         */
        for (int axis = 0; axis < 30; axis++) {

            var plus2 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    2.0 * h));

            var plus1 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    h));

            var minus1 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    -h));

            var minus2 =
                    state.evaluate(
                            move(
                                    coordinates,
                                    axis,
                                    -2.0 * h));

            double ratioPlus2 =
                    wavefunctionRatio(
                            plus2,
                            center);

            double ratioPlus1 =
                    wavefunctionRatio(
                            plus1,
                            center);

            double ratioMinus1 =
                    wavefunctionRatio(
                            minus1,
                            center);

            double ratioMinus2 =
                    wavefunctionRatio(
                            minus2,
                            center);

            /*
             * Five-point second derivative divided by Psi(x):
             *
             * [-Psi(x+2h)
             *  +16 Psi(x+h)
             *  -30 Psi(x)
             *  +16 Psi(x-h)
             *  -Psi(x-2h)]
             * / [12 h^2 Psi(x)]
             *
             * Since Psi(x)/Psi(x) == 1, the center coefficient is -30.
             */
            double axisContribution =
                    (
                            -ratioPlus2
                                    + 16.0 * ratioPlus1
                                    - 30.0
                                    + 16.0 * ratioMinus1
                                    - ratioMinus2
                    )
                            / (12.0 * h * h);

            finiteDifferenceLaplacianOverPsi +=
                    axisContribution;
        }

        assertEquals(
                finiteDifferenceLaplacianOverPsi,
                center.laplacianOverWavefunction(),
                3.0e-3,
                "wavefunction Laplacian");
    }

    private static double wavefunctionRatio(
            FermiNetV1State.Evaluation displaced,
            FermiNetV1State.Evaluation center) {

        double signRatio =
                (double) displaced.sign()
                        / center.sign();

        return signRatio
                * Math.exp(
                displaced.logAbsoluteWavefunction()
                        - center.logAbsoluteWavefunction());
    }

    private static void verifyParameterGradient(
            FermiNetV1State state,
            QuantumCoordinates coordinates,
            FermiNetV1State.Evaluation actual,
            FermiNetParameterLayout layout) {

        double h =
                PARAMETER_STEP;

        for (int index : selected(layout)) {

            double center =
                    state.parameter(index);

            double plus2 =
                    state.withParameter(
                                    index,
                                    center + 2.0 * h)
                            .evaluate(coordinates)
                            .logAbsoluteWavefunction();

            double plus1 =
                    state.withParameter(
                                    index,
                                    center + h)
                            .evaluate(coordinates)
                            .logAbsoluteWavefunction();

            double minus1 =
                    state.withParameter(
                                    index,
                                    center - h)
                            .evaluate(coordinates)
                            .logAbsoluteWavefunction();

            double minus2 =
                    state.withParameter(
                                    index,
                                    center - 2.0 * h)
                            .evaluate(coordinates)
                            .logAbsoluteWavefunction();

            double finiteDifference =
                    (
                            -plus2
                                    + 8.0 * plus1
                                    - 8.0 * minus1
                                    + minus2
                    )
                            / (12.0 * h);

            assertEquals(
                    finiteDifference,
                    actual.parameterLogDerivatives()[index],
                    3.0e-5,
                    "parameter " + index);
        }
    }

    private static void assertFiniteComplete(
            FermiNetV1State.Evaluation evaluation,
            int parameters,
            int coordinates) {

        assertTrue(
                evaluation.sign() == 1
                        || evaluation.sign() == -1);

        assertTrue(
                Double.isFinite(
                        evaluation.logAbsoluteWavefunction()));

        assertTrue(
                Double.isFinite(
                        evaluation.laplacianOverWavefunction()));

        assertEquals(
                coordinates,
                evaluation.logCoordinateGradient().length);

        assertEquals(
                parameters,
                evaluation.parameterLogDerivatives().length);

        for (double value :
                evaluation.logCoordinateGradient()) {

            assertTrue(
                    Double.isFinite(value));
        }

        for (double value :
                evaluation.parameterLogDerivatives()) {

            assertTrue(
                    Double.isFinite(value));
        }
    }

    private static Set<Integer> selected(
            FermiNetParameterLayout layout) {

        Set<Integer> result =
                new LinkedHashSet<>();

        for (var block : layout.blocks()) {

            result.add(
                    block.startInclusive());

            result.add(
                    block.endExclusive() - 1);
        }

        return result;
    }

    private static QuantumCoordinates move(
            QuantumCoordinates coordinates,
            int dimension,
            double delta) {

        int electron =
                dimension / 3;

        int axis =
                dimension % 3;

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(
                        coordinates.particles());

        var old =
                particles.get(electron);

        double x =
                old.xBohr();

        double y =
                old.yBohr();

        double z =
                old.zBohr();

        if (axis == 0) {
            x += delta;
        } else if (axis == 1) {
            y += delta;
        } else {
            z += delta;
        }

        particles.set(
                electron,
                new QuantumCoordinates.ParticleCoordinate(
                        electron,
                        x,
                        y,
                        z,
                        old.spin()));

        return new QuantumCoordinates(
                particles);
    }

    private static QuantumCoordinates swap(
            QuantumCoordinates coordinates,
            int left,
            int right) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(
                        coordinates.particles());

        var a =
                particles.get(left);

        var b =
                particles.get(right);

        particles.set(
                left,
                new QuantumCoordinates.ParticleCoordinate(
                        left,
                        b.xBohr(),
                        b.yBohr(),
                        b.zBohr(),
                        a.spin()));

        particles.set(
                right,
                new QuantumCoordinates.ParticleCoordinate(
                        right,
                        a.xBohr(),
                        a.yBohr(),
                        a.zBohr(),
                        b.spin()));

        return new QuantumCoordinates(
                particles);
    }

    private static FermiNetV1State state(
            Molecule molecule) {

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        return new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        layout,
                        44017L));
    }

    private static String parameterChecksum(
            FermiNetV1State state) {

        return FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state));
    }

    private static Molecule moveNucleus(
            Molecule molecule,
            int nucleus,
            int axis,
            double delta) {

        List<NuclearCenter> nuclei =
                new ArrayList<>(molecule.nuclei());

        NuclearCenter old =
                nuclei.get(nucleus);

        CartesianPosition position =
                old.position().inBohr();

        double[] xyz = {
                position.x(),
                position.y(),
                position.z()
        };

        xyz[axis] += delta;

        nuclei.set(
                nucleus,
                new NuclearCenter(
                        old.orderedIndex(),
                        old.element(),
                        old.charge(),
                        new CartesianPosition(
                                xyz[0],
                                xyz[1],
                                xyz[2],
                                LengthUnit.BOHR)));

        return new Molecule(
                molecule.moleculeId(),
                nuclei,
                molecule.charge(),
                molecule.electrons(),
                molecule.spin());
    }

    private static Molecule moveNuclei(
            Molecule molecule, double[] direction, double scale) {
        Molecule result = molecule;
        for (int component = 0; component < direction.length; component++) {
            result = moveNucleus(result, component / 3, component % 3,
                    scale * direction[component]);
        }
        return result;
    }

    private static QuantumCoordinates moveElectrons(
            QuantumCoordinates coordinates, double[] direction, double scale) {
        QuantumCoordinates result = coordinates;
        for (int component = 0; component < direction.length; component++) {
            result = move(result, component, scale * direction[component]);
        }
        return result;
    }

    private static double[] deterministicDirection(int length, double scale) {
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = scale * ((i % 7) - 3);
        }
        return result;
    }

    private static double relativeError(double absoluteError, double expected) {
        return absoluteError / Math.max(1.0e-12, Math.abs(expected));
    }

    private static Molecule translate(
            Molecule molecule,
            double[] translation) {

        Molecule result =
                molecule;

        for (int nucleus = 0;
             nucleus < molecule.nuclei().size();
             nucleus++) {

            for (int axis = 0;
                 axis < 3;
                 axis++) {

                result =
                        moveNucleus(
                                result,
                                nucleus,
                                axis,
                                translation[axis]);
            }
        }

        return result;
    }

    private static QuantumCoordinates translate(
            QuantumCoordinates coordinates,
            double[] translation) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();

        for (var particle : coordinates.particles()) {
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    particle.particleIndex(),
                    particle.xBohr() + translation[0],
                    particle.yBohr() + translation[1],
                    particle.zBohr() + translation[2],
                    particle.spin()));
        }

        return new QuantumCoordinates(particles);
    }

    private static QuantumCoordinates reflectZ(
            QuantumCoordinates coordinates) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();

        for (var particle : coordinates.particles()) {
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    particle.particleIndex(),
                    particle.xBohr(),
                    particle.yBohr(),
                    -particle.zBohr(),
                    particle.spin()));
        }

        return new QuantumCoordinates(particles);
    }

    private static FermiNetV1State planarReflectionSymmetricState(
            Molecule molecule) {

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();
        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(configuration, molecule);
        double[] parameters =
                FermiNetParameters.initialize(layout, 44017L).toArray();

        int oneInput = 4 * molecule.nuclei().size();
        int twoInput = 4;
        for (int layer = 0;
             layer < configuration.interactionLayers();
             layer++) {

            FermiNetParameterLayout.Block one =
                    layout.block("interaction." + layer + ".one.weight");
            int aggregateInput = 3 * oneInput + 2 * twoInput;
            for (int output = 0;
                 output < configuration.oneElectronWidth();
                 output++) {

                int row = one.startInclusive() + output * aggregateInput;
                if (layer == 0) {
                    for (int stream = 0; stream < 3; stream++) {
                        for (int nucleus = 0;
                             nucleus < molecule.nuclei().size();
                             nucleus++) {
                            parameters[row + stream * oneInput
                                    + 4 * nucleus + 3] = 0.0;
                        }
                    }
                }
                parameters[row + 3 * oneInput + 3] = 0.0;
                parameters[row + 3 * oneInput + twoInput + 3] = 0.0;
            }

            boolean transformTwo =
                    layer < configuration.interactionLayers() - 1
                            || configuration.useLastLayer();
            if (transformTwo) {
                FermiNetParameterLayout.Block two =
                        layout.block("interaction." + layer + ".two.weight");
                for (int output = 0;
                     output < configuration.twoElectronWidth();
                     output++) {
                    parameters[two.startInclusive() + output * twoInput + 3] = 0.0;
                }
                twoInput = configuration.twoElectronWidth();
            }
            oneInput = configuration.oneElectronWidth();
        }

        return new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.fromArray(layout, parameters));
    }

    private static QuantumCoordinates coordinates() {

        double[][] xyz = {
                {0.18, 0.11, 0.27},
                {-0.31, 0.42, -0.16},
                {0.57, -0.28, 0.33},
                {-0.63, -0.37, 0.21},
                {0.24, 0.71, -0.45},
                {-0.22, -0.15, -0.38},
                {0.36, -0.54, 0.19},
                {-0.48, 0.26, 0.51},
                {0.69, 0.18, -0.24},
                {-0.12, 0.61, 0.37}
        };

        List<QuantumCoordinates.ParticleCoordinate> result =
                new ArrayList<>();

        for (int i = 0; i < xyz.length; i++) {

            result.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            xyz[i][0],
                            xyz[i][1],
                            xyz[i][2],
                            i < 5
                                    ? SpinProjection.ALPHA
                                    : SpinProjection.BETA));
        }

        return new QuantumCoordinates(
                result);
    }

    private static Molecule water() {

        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -0.46464225035067114,
                                        1.7340684963325879,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(
                        5,
                        5,
                        1));
    }
}
