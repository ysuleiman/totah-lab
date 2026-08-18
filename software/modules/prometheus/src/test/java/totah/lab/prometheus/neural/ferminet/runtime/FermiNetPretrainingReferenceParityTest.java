package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Numerical parity for the full-determinant Hartree-Fock pretraining objective.
 *
 * <p>The frozen fixture is evaluated from the exact objective used by
 * google-deepmind/ferminet at commit
 * c4312c315dda1c5728994ba89629744f71c6eb66:
 *
 * <ul>
 *   <li>full_det = true</li>
 *   <li>block-diagonal alpha/beta HF target</li>
 *   <li>mean squared orbital error over determinants and the full matrix</li>
 * </ul>
 *
 * <p>This test intentionally injects an identical fixed parameter vector into
 * Java and the external reference. It does not compare PRNG implementations.
 */
final class FermiNetPretrainingReferenceParityTest {

    private static final String REFERENCE_COMMIT =
            "c4312c315dda1c5728994ba89629744f71c6eb66";

    private static final String FIXTURE_RESOURCE =
            "/totah/lab/prometheus/neural/"
                    + "ferminet-pretraining-reference-parity-v1.json";

    private static final double LOSS_TOLERANCE =
            2.0e-12;

    private static final double GRADIENT_TOLERANCE =
            2.0e-10;

    private static final double ADAM_TOLERANCE =
            2.0e-12;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    @Test
    void javaPretrainingLossAndFullGradientMatchDeepMindReference()
            throws IOException {

        Fixture fixture =
                loadFixture();

        assertEquals(
                REFERENCE_COMMIT,
                fixture.referenceCommit());

        assertEquals(
                "google-deepmind/ferminet",
                fixture.referenceRepository());

        assertTrue(
                fixture.jaxX64Enabled());

        Molecule molecule =
                water();

        QuantumCoordinates coordinates =
                coordinates();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        assertEquals(
                layout.parameterCount(),
                fixture.parameters().length);

        assertEquals(
                1_204,
                fixture.parameters().length);

        var parameters =
                FermiNetParameters.fromArray(
                        layout,
                        fixture.parameters());

        var state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        parameters);

        var actual =
                state.pretrainingEvaluation(
                        coordinates,
                        fixture.targetAlpha(),
                        fixture.targetBeta());

        assertEquals(
                fixture.loss(),
                actual.loss(),
                LOSS_TOLERANCE,
                "full-det pretraining loss");

        assertEquals(
                fixture.gradient().length,
                actual.gradient().length);

        /*
         * Compare the complete gradient vector, not a few selected blocks.
         * This is the training signal that Adam receives.
         */
        assertArrayEquals(
                fixture.gradient(),
                actual.gradient(),
                GRADIENT_TOLERANCE,
                "full 1204-entry pretraining gradient");
    }

    @Test
    void firstAdamStepFromReferenceGradientMatchesFrozenFixture()
            throws IOException {

        Fixture fixture =
                loadFixture();

        Molecule molecule =
                water();

        var configuration =
                FermiNetV1Configuration.testFixture();

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        var state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.fromArray(
                                layout,
                                fixture.parameters()));

        var evaluation =
                state.pretrainingEvaluation(
                        coordinates(),
                        fixture.targetAlpha(),
                        fixture.targetBeta());

        AdamFixture adam =
                fixture.adam();

        assertEquals(
                1,
                adam.step());

        /*
         * First Adam step from m0=v0=0:
         *
         * m1 = (1-beta1) g
         * v1 = (1-beta2) g^2
         *
         * bias correction gives:
         *
         * mhat = g
         * vhat = g^2
         *
         * theta1 = theta0
         *          - lr * g / (sqrt(g^2) + eps)
         *
         * This is exactly the standard Optax Adam first-step algebra.
         */
        double[] actualAfter =
                firstAdamStep(
                        fixture.parameters(),
                        evaluation.gradient(),
                        adam.learningRate(),
                        adam.beta1(),
                        adam.beta2(),
                        adam.epsilon());

        assertArrayEquals(
                adam.parametersAfterOneStep(),
                actualAfter,
                ADAM_TOLERANCE,
                "first Adam parameter update");
    }

    private static double[] firstAdamStep(
            double[] parameters,
            double[] gradient,
            double learningRate,
            double beta1,
            double beta2,
            double epsilon) {

        assertEquals(
                parameters.length,
                gradient.length);

        double[] result =
                parameters.clone();

        for (int i = 0;
                i < result.length;
                i++) {

            double g =
                    gradient[i];

            double m =
                    (1.0 - beta1)
                            * g;

            double v =
                    (1.0 - beta2)
                            * g
                            * g;

            double mHat =
                    m
                            / (1.0 - beta1);

            double vHat =
                    v
                            / (1.0 - beta2);

            result[i] -=
                    learningRate
                            * mHat
                            / (Math.sqrt(vHat)
                                    + epsilon);
        }

        return result;
    }

    private static Fixture loadFixture()
            throws IOException {

        try (InputStream input =
                FermiNetPretrainingReferenceParityTest.class
                        .getResourceAsStream(
                                FIXTURE_RESOURCE)) {

            assertNotNull(
                    input,
                    "missing FermiNet pretraining reference fixture: "
                            + FIXTURE_RESOURCE);

            Fixture fixture =
                    OBJECT_MAPPER.readValue(
                            input,
                            Fixture.class);

            assertNotNull(
                    fixture.parameters());

            assertNotNull(
                    fixture.targetAlpha());

            assertNotNull(
                    fixture.targetBeta());

            assertNotNull(
                    fixture.gradient());

            assertNotNull(
                    fixture.adam());

            assertNotNull(
                    fixture.adam()
                            .parametersAfterOneStep());

            return fixture;
        }
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

        for (int i = 0;
                i < xyz.length;
                i++) {

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
                "ferminet-pretraining-reference-parity",
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

    record Fixture(
            String referenceRepository,
            String referenceCommit,
            boolean jaxX64Enabled,
            String objective,
            double[] parameters,
            double[][] targetAlpha,
            double[][] targetBeta,
            double loss,
            double[] gradient,
            AdamFixture adam) {
    }

    record AdamFixture(
            double learningRate,
            double beta1,
            double beta2,
            double epsilon,
            int step,
            double[] parametersAfterOneStep) {
    }
}
