package totah.lab.prometheus.neural.ferminet.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.force.FermiNetForceEvaluationContext;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * TEST_ID: B6 — "verified" is a conclusion of a check, not a string in a
 * file. A self-asserting checkpoint artifact is unverified
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, Layer B).
 *
 * <p>Oracle: declared checkpoint metadata that claims integrity must NOT
 * verify when (a) the declared payload checksum was never derived from the
 * payload, (b) the payload is altered by one byte — even if the attacker
 * recomputes the declared checksum over the tampered file, and (c) the
 * checksum metadata of a genuinely verified, DIFFERENT checkpoint is copied
 * verbatim onto this artifact.
 *
 * <p>Placed in this package because {@code FermiNetOptimizationCheckpoint}'s
 * constructor and {@code FermiNetV1Configuration.testFixture()} are
 * package-private; the context under test is public.
 */
class AdversarialCheckpointProvenanceAcceptanceTest {

    @TempDir
    Path temporary;

    /**
     * TEST_ID: B6 (control) — an honest checkpoint whose declared metadata was
     * derived from the artifact verifies, and the verification flags are the
     * result of recomputation.
     */
    @Test
    void honestCheckpointVerifies() throws Exception {
        Stage stage = stage(0.25, 7);

        FermiNetForceEvaluationContext.ProvenanceVerification verification =
                stage.context().verifyCheckpoint(stage.checkpointFile());
        assertThat(verification.checkpointCryptographicallyVerified()).isTrue();
        assertThat(verification.checkpointPayloadVerified()).isTrue();

        assertThatCode(() -> stage.context().verified(stage.checkpointFile()))
                .doesNotThrowAnyException();
    }

    /**
     * TEST_ID: B6 (a) — payload checksum absent: the declared checkpoint
     * checksum is a syntactically valid 64-hex string that was never computed
     * from this payload. A syntactically valid checksum is never verification.
     */
    @Test
    void declaredChecksumNotDerivedFromPayloadDoesNotVerify() throws Exception {
        Stage stage = stage(0.25, 7);
        FermiNetForceEvaluationContext selfAsserting = new FermiNetForceEvaluationContext(
                stage.state(),
                stage.parameterChecksum(),
                stage.geometryIdentity(),
                stage.configurationFile(),
                stage.dataset(),
                "ab".repeat(32), // well-formed 64-hex claim, absent from the payload
                stage.rootParameterChecksum());

        assertThatThrownBy(() -> selfAsserting.verifyCheckpoint(stage.checkpointFile()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum");
    }

    /**
     * TEST_ID: B6 (b) — one byte altered deep in the payload. Neither the
     * original declared checksum nor an attacker-recomputed checksum over the
     * tampered file may verify: the artifact's internal payload checksums
     * still convict it.
     */
    @Test
    void oneAlteredPayloadByteNeverVerifies() throws Exception {
        Stage stage = stage(0.25, 7);
        Path tampered = temporary.resolve("checkpoint-tampered.bin");
        byte[] bytes = Files.readAllBytes(stage.checkpointFile());
        bytes[bytes.length - 1] ^= 0x01; // last byte lies inside the RNG-state payload
        Files.write(tampered, bytes);

        // Original metadata against tampered payload.
        assertThatThrownBy(() -> stage.context().verifyCheckpoint(tampered))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum");

        // Attacker updates the declared checksum to cover the tampered file;
        // the embedded payload checksums must still refuse it.
        FermiNetForceEvaluationContext recomputedClaim = new FermiNetForceEvaluationContext(
                stage.state(),
                stage.parameterChecksum(),
                stage.geometryIdentity(),
                stage.configurationFile(),
                stage.dataset(),
                sha256(tampered),
                stage.rootParameterChecksum());
        assertThatThrownBy(() -> recomputedClaim.verifyCheckpoint(tampered))
                .isInstanceOf(IOException.class);
    }

    /**
     * TEST_ID: B6 (c) — the complete, genuinely verified metadata of checkpoint
     * A is copied verbatim onto checkpoint B's artifact. The valid-looking but
     * mismatched checksum must not pass.
     */
    @Test
    void metadataCopiedFromVerifiedCheckpointDoesNotVerify() throws Exception {
        Stage a = stage(0.25, 7);
        // A genuinely verifies.
        assertThat(a.context().verifyCheckpoint(a.checkpointFile())
                .checkpointCryptographicallyVerified()).isTrue();

        Stage b = stage(0.5, 9);
        assertThat(b.checkpointFile()).isNotEqualTo(a.checkpointFile());

        // A's verbatim claims (context A) presented against B's artifact.
        assertThatThrownBy(() -> a.context().verifyCheckpoint(b.checkpointFile()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("checksum");
    }

    private record Stage(
            FermiNetV1State state,
            String parameterChecksum,
            String geometryIdentity,
            Path configurationFile,
            FermiNetCorrelatedFdConfigurationFile.Identity dataset,
            Path checkpointFile,
            FermiNetForceEvaluationContext context,
            String rootParameterChecksum) {
    }

    /**
     * Builds a complete, consistent force-evaluation stage: an H2 FermiNet
     * state, its configuration dataset, and a checkpoint artifact written from
     * the same parameters, plus the context whose declared provenance is
     * honestly derived from all of them.
     */
    private Stage stage(double firstParameter, int completedIterations) throws IOException {
        Molecule molecule = hydrogenMolecule();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetV1State state = new FermiNetV1State(
                molecule,
                configuration,
                FermiNetParameters.initialize(
                        new FermiNetParameterLayout(configuration, molecule), 44017L));
        state = state.withParameter(0, firstParameter);

        double[] parameters = FermiNetStateAccess.parameterSnapshot(state);
        String parameterChecksum = FermiNetOptimizationCheckpoint.parameterChecksum(parameters);
        String geometryIdentity = FermiNetPretrainingQualification.geometryIdentity(molecule);
        String rootParameterChecksum = FermiNetOptimizationCheckpoint.parameterChecksum(
                new double[]{firstParameter, completedIterations});

        QuantumCoordinates electrons = new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0, 0.18, 0.11, 0.27, SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1, -0.31, 0.42, -0.16, SpinProjection.BETA)));

        Path configurationFile = temporary.resolve("configurations-" + completedIterations + ".csv");
        FermiNetCorrelatedFdConfigurationFile.Identity dataset =
                FermiNetCorrelatedFdConfigurationFile.write(
                        configurationFile, List.of(electrons), 1);

        Path checkpointFile = temporary.resolve("checkpoint-" + completedIterations + ".bin");
        new FermiNetOptimizationCheckpoint(
                completedIterations,
                rootParameterChecksum,
                sha256String("sampling:" + completedIterations),
                sha256String("optimizer:" + completedIterations),
                geometryIdentity,
                FermiNetOptimizerType.EXACT_SR,
                parameters,
                List.of(electrons),
                new byte[]{0x11, 0x22, 0x33, 0x44}).write(checkpointFile);

        FermiNetForceEvaluationContext context = new FermiNetForceEvaluationContext(
                state,
                parameterChecksum,
                geometryIdentity,
                configurationFile,
                dataset,
                sha256(checkpointFile),
                rootParameterChecksum);
        return new Stage(state, parameterChecksum, geometryIdentity, configurationFile,
                dataset, checkpointFile, context, rootParameterChecksum);
    }

    private static Molecule hydrogenMolecule() {
        return new Molecule(
                "adversarial-b6-h2",
                List.of(
                        new NuclearCenter(0, "H", new NuclearCharge(1),
                                new CartesianPosition(0.0, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(0.0, 0.0, 1.4, LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static String sha256(Path path) throws IOException {
        return HexFormat.of().formatHex(digest().digest(Files.readAllBytes(path)));
    }

    private static String sha256String(String value) {
        return HexFormat.of().formatHex(
                digest().digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
