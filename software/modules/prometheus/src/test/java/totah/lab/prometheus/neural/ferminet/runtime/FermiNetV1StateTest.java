package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
