package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

final class FermiNetV1ConfigurationTest {

    @Test
    void productionArchitectureIsLockedToReferenceDimensions() {

        var configuration =
                FermiNetV1Configuration.locked();

        assertEquals(
                3,
                configuration.spatialDimensions());

        assertEquals(
                4,
                configuration.interactionLayers());

        assertEquals(
                256,
                configuration.oneElectronWidth());

        assertEquals(
                32,
                configuration.twoElectronWidth());

        assertEquals(
                16,
                configuration.determinants());

        /*
         * Locked DeepMind reference semantics.
         */
        assertTrue(
                configuration.fullDeterminants());

        assertTrue(
                configuration.isotropicNuclearEnvelope());

        assertFalse(
                configuration.jastrowEnabled());

        assertFalse(
                configuration.biasOrbitals());

        assertFalse(
                configuration.useLastLayer());

        assertFalse(
                configuration.separateSpinChannels());
    }

    @Test
    void incompatibleArchitectureCannotMasqueradeAsV1() {

        /*
         * Wrong spatial dimension.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        2,
                        4,
                        256,
                        32,
                        16,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false));

        /*
         * Spin-factored determinants are not the locked reference.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        3,
                        4,
                        256,
                        32,
                        16,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false));

        /*
         * Non-isotropic envelope is not the locked reference.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        3,
                        4,
                        256,
                        32,
                        16,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false));

        /*
         * Jastrow is disabled in the locked reference configuration.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        3,
                        4,
                        256,
                        32,
                        16,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false));

        /*
         * Orbital biases are disabled.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        3,
                        4,
                        256,
                        32,
                        16,
                        true,
                        true,
                        false,
                        true,
                        false,
                        false));

        /*
         * useLastLayer must be false.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        3,
                        4,
                        256,
                        32,
                        16,
                        true,
                        true,
                        false,
                        false,
                        true,
                        false));

        /*
         * Separate pair-stream parameters by spin channel are disabled.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> new FermiNetV1Configuration(
                        3,
                        4,
                        256,
                        32,
                        16,
                        true,
                        true,
                        false,
                        false,
                        false,
                        true));
    }

    @Test
    void waterLayoutMatchesReferenceFullDeterminantArchitecture() {

        var layout =
                new FermiNetParameterLayout(
                        FermiNetV1Configuration.locked(),
                        water());

        /*
         * Interaction blocks:
         *
         * layers 0,1,2:
         *   one.weight
         *   one.bias
         *   two.weight
         *   two.bias
         *
         * layer 3:
         *   one.weight
         *   one.bias
         *
         * => 14 blocks
         *
         * Orbital/envelope blocks:
         *
         * alpha:
         *   orbital.weight
         *   envelope.pi
         *   envelope.sigma
         *
         * beta:
         *   same
         *
         * => 6 blocks
         *
         * Total = 20.
         */
        assertEquals(
                20,
                layout.blocks().size());

        /*
         * Reference-aligned H2O parameter count:
         *
         * interaction stack = 653,536
         * orbital weights   =  81,920
         * envelopes         =   1,920
         *
         * total             = 737,376
         */
        assertEquals(
                737_376,
                layout.parameterCount());

        /*
         * H2O:
         * three nuclei -> initial one-electron width = 12.
         *
         * symmetric input:
         * 3*12 + 2*4 = 44.
         */
        assertEquals(
                256 * 44,
                layout.block(
                                "interaction.0.one.weight")
                        .size());

        /*
         * Subsequent symmetric input:
         *
         * 3*256 + 2*32 = 832.
         */
        assertEquals(
                256 * 832,
                layout.block(
                                "interaction.1.one.weight")
                        .size());

        assertEquals(
                256 * 832,
                layout.block(
                                "interaction.2.one.weight")
                        .size());

        assertEquals(
                256 * 832,
                layout.block(
                                "interaction.3.one.weight")
                        .size());

        /*
         * Pair-stream transformation exists only for layers 0-2.
         */
        assertEquals(
                32 * 4,
                layout.block(
                                "interaction.0.two.weight")
                        .size());

        assertEquals(
                32 * 32,
                layout.block(
                                "interaction.1.two.weight")
                        .size());

        assertEquals(
                32 * 32,
                layout.block(
                                "interaction.2.two.weight")
                        .size());

        assertThrows(
                IllegalArgumentException.class,
                () -> layout.block(
                        "interaction.3.two.weight"));

        /*
         * Full determinant:
         *
         * each active spin head emits N=10 orbital columns
         * for each of 16 determinants.
         */
        assertEquals(
                16 * 10 * 256,
                layout.block(
                                "orbital.alpha.weight")
                        .size());

        assertEquals(
                16 * 10 * 256,
                layout.block(
                                "orbital.beta.weight")
                        .size());

        /*
         * No orbital bias in reference configuration.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> layout.block(
                        "orbital.alpha.bias"));

        assertThrows(
                IllegalArgumentException.class,
                () -> layout.block(
                        "orbital.beta.bias"));

        /*
         * 16 determinants × 10 orbitals × 3 nuclei.
         */
        assertEquals(
                16 * 10 * 3,
                layout.block(
                                "envelope.alpha.pi")
                        .size());

        assertEquals(
                16 * 10 * 3,
                layout.block(
                                "envelope.beta.sigma")
                        .size());

        /*
         * No trainable determinant coefficients.
         */
        assertThrows(
                IllegalArgumentException.class,
                () -> layout.block(
                        "determinant.coefficient"));

        /*
         * Parameter blocks must form one contiguous immutable vector.
         */
        int cursor = 0;

        for (var block : layout.blocks()) {

            assertEquals(
                    cursor,
                    block.startInclusive());

            cursor =
                    block.endExclusive();
        }

        assertEquals(
                layout.parameterCount(),
                cursor);
    }

    @Test
    void deterministicInitializationMatchesReferenceEnvelopeDefaults() {

        var layout =
                new FermiNetParameterLayout(
                        FermiNetV1Configuration.locked(),
                        water());

        var first =
                FermiNetParameters.initialize(
                        layout,
                        9127L);

        var second =
                FermiNetParameters.initialize(
                        layout,
                        9127L);

        assertEquals(
                first,
                second);

        assertEquals(
                layout.parameterCount(),
                first.size());

        /*
         * Reference isotropic envelope initialization:
         *
         * pi = 1
         * sigma = 1
         */
        var alphaPi =
                layout.block(
                        "envelope.alpha.pi");

        for (int i = alphaPi.startInclusive();
             i < alphaPi.endExclusive();
             i++) {

            assertEquals(
                    1.0,
                    first.get(i));
        }

        var betaSigma =
                layout.block(
                        "envelope.beta.sigma");

        for (int i = betaSigma.startInclusive();
             i < betaSigma.endExclusive();
             i++) {

            assertEquals(
                    1.0,
                    first.get(i));
        }

        /*
         * Interaction biases must no longer silently remain zero.
         *
         * The reference initializes them from a Gaussian.
         *
         * We do not assert an exact numerical sequence against JAX here,
         * because Java Random and JAX PRNG are intentionally different.
         */
        var bias =
                layout.block(
                        "interaction.0.one.bias");

        boolean anyNonZero = false;

        for (int i = bias.startInclusive();
             i < bias.endExclusive();
             i++) {

            if (first.get(i) != 0.0) {
                anyNonZero = true;
                break;
            }
        }

        assertTrue(
                anyNonZero);
    }

    @Test
    void geometricInputsContainReferenceVectorsNormsAndIgnoreAbsoluteOrigin() {

        Molecule molecule =
                water();

        var coordinates =
                coordinates(0.0);

        var first =
                FermiNetGeometricFeatures.build(
                        molecule,
                        coordinates);

        Molecule shifted =
                shift(
                        molecule,
                        2.0,
                        -3.0,
                        0.5);

        var second =
                FermiNetGeometricFeatures.build(
                        shifted,
                        shift(
                                coordinates,
                                2.0,
                                -3.0,
                                0.5));

        assertEquals(
                12,
                first.oneElectron()[0].length);

        assertEquals(
                4,
                first.twoElectron()[0][1].length);

        /*
         * Reference order:
         *
         * [distance, dx, dy, dz]
         *
         * Self pair feature is identically zero.
         */
        assertArrayEquals(
                new double[] {
                        0.0,
                        0.0,
                        0.0,
                        0.0
                },
                first.twoElectron()[0][0],
                0.0);

        /*
         * Pair distance is symmetric.
         */
        assertEquals(
                first.twoElectron()[0][1][0],
                first.twoElectron()[1][0][0],
                1e-14);

        /*
         * Pair vector reverses sign under electron exchange.
         */
        assertEquals(
                -first.twoElectron()[1][0][1],
                first.twoElectron()[0][1][1],
                1e-14);

        assertEquals(
                -first.twoElectron()[1][0][2],
                first.twoElectron()[0][1][2],
                1e-14);

        assertEquals(
                -first.twoElectron()[1][0][3],
                first.twoElectron()[0][1][3],
                1e-14);

        /*
         * Translation invariance.
         */
        for (int i = 0; i < 10; i++) {

            assertArrayEquals(
                    first.oneElectron()[i],
                    second.oneElectron()[i],
                    1e-14);

            for (int j = 0; j < 10; j++) {

                assertArrayEquals(
                        first.twoElectron()[i][j],
                        second.twoElectron()[i][j],
                        1e-14);
            }
        }
    }

    private static Molecule water() {

        return new Molecule(
                "ferminet-water",
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
                                        1.8,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -0.46,
                                        1.73,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(
                        5,
                        5,
                        1));
    }

    private static QuantumCoordinates coordinates(
            double shift) {

        var particles =
                new java.util.ArrayList<
                        QuantumCoordinates.ParticleCoordinate>();

        for (int i = 0; i < 10; i++) {

            particles.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            shift + 0.2 * i,
                            shift - 0.1 * i,
                            shift + 0.05 * i,
                            i < 5
                                    ? SpinProjection.ALPHA
                                    : SpinProjection.BETA));
        }

        return new QuantumCoordinates(
                particles);
    }

    private static Molecule shift(
            Molecule molecule,
            double x,
            double y,
            double z) {

        return new Molecule(
                molecule.moleculeId(),
                molecule.nuclei()
                        .stream()
                        .map(nucleus -> {

                            var position =
                                    nucleus.position()
                                            .inBohr();

                            return new NuclearCenter(
                                    nucleus.orderedIndex(),
                                    nucleus.element(),
                                    nucleus.charge(),
                                    new CartesianPosition(
                                            position.x() + x,
                                            position.y() + y,
                                            position.z() + z,
                                            LengthUnit.BOHR));
                        })
                        .toList(),
                molecule.charge(),
                molecule.electrons(),
                molecule.spin());
    }

    private static QuantumCoordinates shift(
            QuantumCoordinates coordinates,
            double x,
            double y,
            double z) {

        return new QuantumCoordinates(
                coordinates.particles()
                        .stream()
                        .map(particle ->
                                new QuantumCoordinates.ParticleCoordinate(
                                        particle.particleIndex(),
                                        particle.xBohr() + x,
                                        particle.yBohr() + y,
                                        particle.zBohr() + z,
                                        particle.spin()))
                        .toList());
    }
}