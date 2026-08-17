package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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

final class GaussianHartreeFockOrbitalTargetTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void frozenPyscfOrbitalsAreReproducedByJavaEvaluator()
            throws Exception {

        GaussianHartreeFockOrbitalTarget target =
                GaussianHartreeFockOrbitalTarget.read(
                        artifact(),
                        water());

        var evaluated =
                target.evaluate(
                        coordinates());

        /*
         * ------------------------------------------------------------
         * Frozen scientific provenance
         * ------------------------------------------------------------
         */
        assertEquals(
                -76.02701634703507,
                target.provenance()
                        .scfEnergyHartree(),
                1.0e-10);

        assertEquals(
                ReferenceFermiNetPretrainer.REFERENCE_COMMIT,
                target.provenance()
                        .ferminetCommit());

        assertEquals(
                "cc-pvdz",
                target.provenance()
                        .basis());

        assertFalse(
                target.provenance()
                        .restricted());

        /*
         * ------------------------------------------------------------
         * Independent PySCF orbital-value controls
         * ------------------------------------------------------------
         *
         * These values are the critical numerical acceptance boundary.
         * They prove that Java reproduces the frozen PySCF spherical-GTO
         * evaluation rather than merely accepting the JSON structure.
         */
        assertEquals(
                0.8770182024418767,
                evaluated.alpha()[0][0],
                2.0e-12);

        assertEquals(
                -0.24655525357718072,
                evaluated.alpha()[4][4],
                2.0e-12);

        assertEquals(
                0.3562028720944922,
                evaluated.beta()[0][0],
                2.0e-12);

        assertEquals(
                0.2857340946336463,
                evaluated.beta()[4][4],
                2.0e-12);

        assertEquals(
                5,
                evaluated.alpha().length);

        assertEquals(
                5,
                evaluated.beta().length);

        for (double[] row :
                evaluated.alpha()) {

            assertEquals(
                    5,
                    row.length);

            for (double value :
                    row) {

                assertTrue(
                        Double.isFinite(value));
            }
        }

        for (double[] row :
                evaluated.beta()) {

            assertEquals(
                    5,
                    row.length);

            for (double value :
                    row) {

                assertTrue(
                        Double.isFinite(value));
            }
        }
    }

    @Test
    void electronOrderingMustBeAlphaThenBeta()
            throws Exception {

        GaussianHartreeFockOrbitalTarget target =
                GaussianHartreeFockOrbitalTarget.read(
                        artifact(),
                        water());

        QuantumCoordinates valid =
                coordinates();

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>(
                        valid.particles());

        var first =
                particles.get(0);

        /*
         * Deliberately violate the scientific ordering contract while
         * preserving the electron count.
         */
        particles.set(
                0,
                new QuantumCoordinates.ParticleCoordinate(
                        first.particleIndex(),
                        first.xBohr(),
                        first.yBohr(),
                        first.zBohr(),
                        SpinProjection.BETA));

        QuantumCoordinates invalid =
                new QuantumCoordinates(
                        particles);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> target.evaluate(
                                invalid));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "alpha then beta"));
    }

    @Test
    void wrongBasisIsRejected()
            throws Exception {

        Path modified =
                modifiedArtifact(
                        root ->
                                root.put(
                                        "basis",
                                        "sto-3g"));

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        modified,
                                        water()));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "cc-pVDZ"));
    }

    @Test
    void restrictedArtifactIsRejected()
            throws Exception {

        Path modified =
                modifiedArtifact(
                        root ->
                                root.put(
                                        "restricted",
                                        true));

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        modified,
                                        water()));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "unrestricted"));
    }

    @Test
    void unconvergedArtifactIsRejected()
            throws Exception {

        Path modified =
                modifiedArtifact(
                        root ->
                                root.put(
                                        "converged",
                                        false));

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        modified,
                                        water()));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "not converged"));
    }

    @Test
    void geometryMismatchIsRejected()
            throws Exception {

        Path modified =
                modifiedArtifact(
                        root -> {

                            JsonNode geometry =
                                    root.get(
                                            "molecule_scientific_geometry");

                            /*
                             * Move H1 by 0.01 bohr.
                             */
                            JsonNode xyz =
                                    geometry.get(1)
                                            .get(1);

                            double x =
                                    xyz.get(0)
                                            .asDouble();

                            ((com.fasterxml.jackson.databind.node.ArrayNode) xyz)
                                    .set(
                                            0,
                                            OBJECT_MAPPER
                                                    .getNodeFactory()
                                                    .numberNode(
                                                            x + 0.01));
                        });

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        modified,
                                        water()));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "geometry mismatch"));
    }

    @Test
    void invalidShellAtomIndexIsRejected()
            throws Exception {

        Path modified =
                modifiedArtifact(
                        root -> {

                            JsonNode firstShell =
                                    root.get("shells")
                                            .get(0);

                            ((ObjectNode) firstShell)
                                    .put(
                                            "atom",
                                            99);
                        });

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        modified,
                                        water()));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "invalid atom index"));
    }

    @Test
    void nonPositiveGaussianExponentIsRejected()
            throws Exception {

        Path modified =
                modifiedArtifact(
                        root -> {

                            JsonNode exponents =
                                    root.get("shells")
                                            .get(0)
                                            .get("exponents");

                            ((com.fasterxml.jackson.databind.node.ArrayNode) exponents)
                                    .set(
                                            0,
                                            OBJECT_MAPPER
                                                    .getNodeFactory()
                                                    .numberNode(
                                                            0.0));
                        });

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        modified,
                                        water()));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "positive"));
    }

    @Test
    void electronSpinCountsMustMatchArtifact()
            throws Exception {

        Molecule wrongSpin =
                new Molecule(
                        "ferminet-v1-water-wrong-spin",
                        water().nuclei(),
                        new MolecularCharge(0),
                        new ElectronCount(10),
                        new SpinSector(
                                6,
                                4,
                                3));

        IOException exception =
                assertThrows(
                        IOException.class,
                        () ->
                                GaussianHartreeFockOrbitalTarget.read(
                                        artifact(),
                                        wrongSpin));

        assertTrue(
                exception.getMessage()
                        .contains(
                                "electron/spin mismatch"));
    }

    private Path modifiedArtifact(
            java.util.function.Consumer<ObjectNode> modification)
            throws Exception {

        JsonNode source =
                OBJECT_MAPPER.readTree(
                        artifact()
                                .toFile());

        ObjectNode copy =
                source.deepCopy();

        modification.accept(
                copy);

        Path output =
                temporaryDirectory.resolve(
                        "modified-hf.json");

        Files.writeString(
                output,
                OBJECT_MAPPER
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                copy));

        return output;
    }

    static Path artifact()
            throws URISyntaxException {

        return Path.of(
                GaussianHartreeFockOrbitalTargetTest.class
                        .getResource(
                                "/totah/lab/prometheus/neural/"
                                        + "totah/lab/prometheus/neural/h2o-uhf-ccpvdz.json")
                        .toURI());
    }

    static Molecule water() {

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

    static QuantumCoordinates coordinates() {

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

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();

        for (int i = 0;
             i < xyz.length;
             i++) {

            particles.add(
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
                particles);
    }
}