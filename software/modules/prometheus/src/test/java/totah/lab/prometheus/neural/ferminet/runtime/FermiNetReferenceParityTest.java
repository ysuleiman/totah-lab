package totah.lab.prometheus.neural.ferminet.runtime;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
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
 * Numerical parity test against the frozen official DeepMind FermiNet
 * implementation.
 *
 * <p>The fixture must be generated independently by:
 *
 * <pre>
 * google-deepmind/ferminet
 * commit c4312c315dda1c5728994ba89629744f71c6eb66
 * </pre>
 *
 * <p>The Java implementation is never permitted to generate or update its own
 * expected values. The reference fixture is an external scientific oracle.
 */
final class FermiNetReferenceParityTest {

    private static final String REFERENCE_COMMIT =
            "c4312c315dda1c5728994ba89629744f71c6eb66";

    private static final String FIXTURE_RESOURCE =
            "/totah/lab/prometheus/neural/"
                    + "ferminet-reference-parity-v1.json";

    /*
     * Fixture generation must run JAX in x64 mode.
     *
     * Tight tolerances are intentional. This is architecture parity,
     * not an approximate scientific regression test.
     */
    private static final double FEATURE_TOLERANCE =
            1.0e-12;

    private static final double LAYER_TOLERANCE =
            2.0e-10;

    private static final double ORBITAL_TOLERANCE =
            5.0e-10;

    private static final double LOG_DETERMINANT_TOLERANCE =
            1.0e-9;

    private static final double LOG_WAVEFUNCTION_TOLERANCE =
            1.0e-9;

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    @Test
    void javaForwardPassMatchesLockedDeepMindReference() throws IOException {

        Fixture fixture =
                loadFixture();

        /*
         * ------------------------------------------------------------
         * Provenance lock
         * ------------------------------------------------------------
         */
        assertEquals(
                REFERENCE_COMMIT,
                fixture.referenceCommit());

        assertTrue(
                fixture.jaxX64Enabled(),
                "reference fixture must be generated with JAX x64 enabled");

        assertEquals(
                "google-deepmind/ferminet",
                fixture.referenceRepository());

        /*
         * ------------------------------------------------------------
         * Configuration lock
         * ------------------------------------------------------------
         */
        var configuration =
                FermiNetV1Configuration.testFixture();

        assertEquals(
                configuration.spatialDimensions(),
                fixture.configuration().spatialDimensions());

        assertEquals(
                configuration.interactionLayers(),
                fixture.configuration().interactionLayers());

        assertEquals(
                configuration.oneElectronWidth(),
                fixture.configuration().oneElectronWidth());

        assertEquals(
                configuration.twoElectronWidth(),
                fixture.configuration().twoElectronWidth());

        assertEquals(
                configuration.determinants(),
                fixture.configuration().determinants());

        assertEquals(
                configuration.fullDeterminants(),
                fixture.configuration().fullDeterminants());

        assertEquals(
                configuration.useLastLayer(),
                fixture.configuration().useLastLayer());

        assertEquals(
                configuration.biasOrbitals(),
                fixture.configuration().biasOrbitals());

        assertEquals(
                configuration.separateSpinChannels(),
                fixture.configuration().separateSpinChannels());

        assertEquals(
                configuration.jastrowEnabled(),
                fixture.configuration().jastrowEnabled());

        assertTrue(
                configuration.fullDeterminants());

        assertFalse(
                configuration.useLastLayer());

        assertFalse(
                configuration.biasOrbitals());

        assertFalse(
                configuration.separateSpinChannels());

        /*
         * ------------------------------------------------------------
         * Exact common input
         * ------------------------------------------------------------
         */
        Molecule molecule =
                molecule(fixture);

        QuantumCoordinates coordinates =
                coordinates(fixture);

        var layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        assertEquals(
                layout.parameterCount(),
                fixture.parameters().length,
                "reference parameter vector length");

        /*
         * IMPORTANT:
         *
         * We inject the exact same numeric parameter vector used by JAX.
         * We do not compare Java Random with JAX PRNG.
         */
        FermiNetParameters parameters =
                FermiNetParameters.fromArray(
                        layout,
                        fixture.parameters());

        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        parameters);

        FermiNetV1State.ReferenceSnapshot actual =
                state.referenceSnapshot(
                        coordinates);

        /*
         * ------------------------------------------------------------
         * 1. Raw electron-nuclear features
         * ------------------------------------------------------------
         */
        assertMatrixEquals(
                fixture.oneElectronFeatures(),
                actual.oneElectronFeatures(),
                FEATURE_TOLERANCE,
                "one-electron input features");

        /*
         * ------------------------------------------------------------
         * 2. Raw electron-electron features
         * ------------------------------------------------------------
         */
        assertTensorEquals(
                fixture.twoElectronFeatures(),
                actual.twoElectronFeatures(),
                FEATURE_TOLERANCE,
                "two-electron input features");

        /*
         * ------------------------------------------------------------
         * 3. Every interaction layer
         * ------------------------------------------------------------
         */
        assertEquals(
                fixture.layers().size(),
                actual.layers().size(),
                "interaction layer count");

        for (int layer = 0;
             layer < fixture.layers().size();
             layer++) {

            LayerFixture expected =
                    fixture.layers().get(layer);

            FermiNetV1State.LayerReferenceSnapshot observed =
                    actual.layers().get(layer);

            assertEquals(
                    expected.layer(),
                    observed.layer(),
                    "layer index");

            assertEquals(
                    expected.transformedTwoElectronStream(),
                    observed.transformedTwoElectronStream(),
                    "layer "
                            + layer
                            + " pair-stream transformation status");

            assertMatrixEquals(
                    expected.aggregateInput(),
                    observed.aggregateInput(),
                    LAYER_TOLERANCE,
                    "layer "
                            + layer
                            + " aggregate input");

            assertMatrixEquals(
                    expected.oneElectronOutput(),
                    observed.oneElectronOutput(),
                    LAYER_TOLERANCE,
                    "layer "
                            + layer
                            + " one-electron output");

            assertTensorEquals(
                    expected.twoElectronOutput(),
                    observed.twoElectronOutput(),
                    LAYER_TOLERANCE,
                    "layer "
                            + layer
                            + " two-electron output");
        }

        /*
         * ------------------------------------------------------------
         * 4. Dense full-determinant orbital matrices
         * ------------------------------------------------------------
         */
        assertEquals(
                fixture.determinants().size(),
                actual.determinants().size(),
                "determinant count");

        for (int determinant = 0;
             determinant < fixture.determinants().size();
             determinant++) {

            DeterminantFixture expected =
                    fixture.determinants().get(determinant);

            FermiNetV1State.DeterminantReferenceSnapshot observed =
                    actual.determinants().get(determinant);

            assertEquals(
                    expected.determinant(),
                    observed.determinant());

            assertMatrixEquals(
                    expected.orbitalMatrix(),
                    observed.orbitalMatrix(),
                    ORBITAL_TOLERANCE,
                    "determinant "
                            + determinant
                            + " orbital matrix");

            /*
             * --------------------------------------------------------
             * 5. Individual determinant sign and log magnitude
             * --------------------------------------------------------
             */
            assertEquals(
                    expected.sign(),
                    observed.sign(),
                    "determinant "
                            + determinant
                            + " sign");

            assertEquals(
                    expected.logMagnitude(),
                    observed.logMagnitude(),
                    LOG_DETERMINANT_TOLERANCE,
                    "determinant "
                            + determinant
                            + " log magnitude");
        }

        /*
         * ------------------------------------------------------------
         * 6. Final wavefunction sign
         * ------------------------------------------------------------
         */
        assertEquals(
                fixture.sign(),
                actual.sign(),
                "final wavefunction sign");

        /*
         * ------------------------------------------------------------
         * 7. Final log|Psi|
         * ------------------------------------------------------------
         */
        assertEquals(
                fixture.logAbsoluteWavefunction(),
                actual.logAbsoluteWavefunction(),
                LOG_WAVEFUNCTION_TOLERANCE,
                "final log|Psi|");
    }

    private static Fixture loadFixture() throws IOException {

        try (InputStream input =
                     FermiNetReferenceParityTest.class
                             .getResourceAsStream(
                                     FIXTURE_RESOURCE)) {

            assertNotNull(
                    input,
                    "missing FermiNet reference fixture: "
                            + FIXTURE_RESOURCE);

            Fixture fixture =
                    OBJECT_MAPPER.readValue(
                            input,
                            Fixture.class);

            assertNotNull(
                    fixture.parameters());

            assertNotNull(
                    fixture.oneElectronFeatures());

            assertNotNull(
                    fixture.twoElectronFeatures());

            assertNotNull(
                    fixture.layers());

            assertNotNull(
                    fixture.determinants());

            return fixture;
        }
    }

    /*
     * =====================================================================
     * Fixture -> Prometheus scientific objects
     * =====================================================================
     */

    private static Molecule molecule(
            Fixture fixture) {

        List<NuclearCenter> nuclei =
                fixture.nuclei()
                        .stream()
                        .map(nucleus ->
                                new NuclearCenter(
                                        nucleus.orderedIndex(),
                                        nucleus.element(),
                                        new NuclearCharge(
                                                nucleus.nuclearCharge()),
                                        new CartesianPosition(
                                                nucleus.xBohr(),
                                                nucleus.yBohr(),
                                                nucleus.zBohr(),
                                                LengthUnit.BOHR)))
                        .toList();

        return new Molecule(
                "ferminet-reference-parity",
                nuclei,
                new MolecularCharge(
                        fixture.molecularCharge()),
                new ElectronCount(
                        fixture.electrons().size()),
                new SpinSector(
                        fixture.alphaElectrons(),
                        fixture.betaElectrons(),
                        fixture.spinMultiplicity()));
    }

    private static QuantumCoordinates coordinates(
            Fixture fixture) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                fixture.electrons()
                        .stream()
                        .map(electron ->
                                new QuantumCoordinates.ParticleCoordinate(
                                        electron.particleIndex(),
                                        electron.xBohr(),
                                        electron.yBohr(),
                                        electron.zBohr(),
                                        parseSpin(
                                                electron.spin())))
                        .toList();

        return new QuantumCoordinates(
                particles);
    }

    private static SpinProjection parseSpin(
            String value) {

        return switch (value) {
            case "ALPHA" -> SpinProjection.ALPHA;
            case "BETA" -> SpinProjection.BETA;
            default -> throw new IllegalArgumentException(
                    "unknown fixture spin: " + value);
        };
    }

    /*
     * =====================================================================
     * Array comparisons with useful failure locations
     * =====================================================================
     */

    private static void assertMatrixEquals(
            double[][] expected,
            double[][] actual,
            double tolerance,
            String label) {

        assertEquals(
                expected.length,
                actual.length,
                label + " row count");

        for (int row = 0;
             row < expected.length;
             row++) {

            assertArrayEquals(
                    expected[row],
                    actual[row],
                    tolerance,
                    label + " row " + row);
        }
    }

    private static void assertTensorEquals(
            double[][][] expected,
            double[][][] actual,
            double tolerance,
            String label) {

        assertEquals(
                expected.length,
                actual.length,
                label + " outer dimension");

        for (int i = 0;
             i < expected.length;
             i++) {

            assertEquals(
                    expected[i].length,
                    actual[i].length,
                    label + " dimension 1 at " + i);

            for (int j = 0;
                 j < expected[i].length;
                 j++) {

                assertArrayEquals(
                        expected[i][j],
                        actual[i][j],
                        tolerance,
                        label
                                + " ["
                                + i
                                + "]["
                                + j
                                + "]");
            }
        }
    }

    /*
     * =====================================================================
     * Frozen JSON schema
     * =====================================================================
     */

    record Fixture(
            String referenceRepository,
            String referenceCommit,
            boolean jaxX64Enabled,
            ConfigurationFixture configuration,
            int molecularCharge,
            int alphaElectrons,
            int betaElectrons,
            int spinMultiplicity,
            List<NucleusFixture> nuclei,
            List<ElectronFixture> electrons,
            double[] parameters,
            double[][] oneElectronFeatures,
            double[][][] twoElectronFeatures,
            List<LayerFixture> layers,
            List<DeterminantFixture> determinants,
            int sign,
            double logAbsoluteWavefunction) {
    }

    record ConfigurationFixture(
            int spatialDimensions,
            int interactionLayers,
            int oneElectronWidth,
            int twoElectronWidth,
            int determinants,
            boolean fullDeterminants,
            boolean isotropicNuclearEnvelope,
            boolean jastrowEnabled,
            boolean biasOrbitals,
            boolean useLastLayer,
            boolean separateSpinChannels) {
    }

    record NucleusFixture(
            int orderedIndex,
            String element,
            int nuclearCharge,
            double xBohr,
            double yBohr,
            double zBohr) {
    }

    record ElectronFixture(
            int particleIndex,
            double xBohr,
            double yBohr,
            double zBohr,
            String spin) {
    }

    record LayerFixture(
            int layer,
            double[][] aggregateInput,
            double[][] oneElectronOutput,
            double[][][] twoElectronOutput,
            boolean transformedTwoElectronStream) {
    }

    record DeterminantFixture(
            int determinant,
            double[][] orbitalMatrix,
            int sign,
            double logMagnitude) {
    }
}