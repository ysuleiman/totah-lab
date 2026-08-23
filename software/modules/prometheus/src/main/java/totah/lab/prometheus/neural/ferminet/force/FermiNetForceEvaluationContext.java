package totah.lab.prometheus.neural.ferminet.force;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;

/** Immutable frozen state and immutable electron dataset shared by all estimators. */
public record FermiNetForceEvaluationContext(
        FermiNetV1State state,
        String parameterChecksum,
        String geometryIdentity,
        Path configurationFile,
        FermiNetCorrelatedFdConfigurationFile.Identity dataset,
        String checkpointChecksum,
        String rootParameterChecksum) {

    public FermiNetForceEvaluationContext {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(parameterChecksum, "parameterChecksum");
        Objects.requireNonNull(geometryIdentity, "geometryIdentity");
        configurationFile = Objects.requireNonNull(
                configurationFile, "configurationFile").toAbsolutePath().normalize();
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(checkpointChecksum, "checkpointChecksum");
        Objects.requireNonNull(rootParameterChecksum, "rootParameterChecksum");
        String actualParameters = FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state));
        String actualGeometry = FermiNetPretrainingQualification.geometryIdentity(
                state.molecule());
        if (!parameterChecksum.equals(actualParameters)
                || !geometryIdentity.equals(actualGeometry)) {
            throw new IllegalArgumentException("force context state identity mismatch");
        }
    }

    public void verifyDataset() throws IOException {
        var actual = FermiNetCorrelatedFdConfigurationFile.inspect(
                configurationFile, dataset.walkerCount());
        if (!dataset.equals(actual)) {
            throw new IOException("force configuration dataset identity mismatch");
        }
    }

    /**
     * Cryptographically verifies the declared checkpoint checksum and checks
     * that the checkpoint payload contains the state/root identities declared
     * by this context.  A syntactically valid checksum is never verification.
     */
    public ProvenanceVerification verifyCheckpoint(Path checkpointFile)
            throws IOException {
        Path normalized = Objects.requireNonNull(checkpointFile,
                "checkpointFile").toAbsolutePath().normalize();
        String actualFileChecksum = sha256(normalized);
        if (!checkpointChecksum.equals(actualFileChecksum)) {
            throw new IOException("force checkpoint file checksum mismatch");
        }
        FermiNetOptimizationCheckpoint checkpoint =
                FermiNetOptimizationCheckpoint.read(normalized);
        if (!parameterChecksum.equals(checkpoint.parameterChecksum())) {
            throw new IOException("force checkpoint parameter identity mismatch");
        }
        if (!geometryIdentity.equals(checkpoint.geometryIdentity())) {
            throw new IOException("force checkpoint geometry identity mismatch");
        }
        if (!rootParameterChecksum.equals(checkpoint.rootParameterChecksum())) {
            throw new IOException("force checkpoint root declaration mismatch");
        }
        return new ProvenanceVerification(actualFileChecksum, true, true,
                false, "root identity agrees with checkpoint declaration but "
                + "requires an independent root artifact for cryptographic verification");
    }

    public DeclaredProvenance declaredProvenance() {
        return new DeclaredProvenance(parameterChecksum, geometryIdentity,
                dataset.sha256(), checkpointChecksum, rootParameterChecksum);
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record DeclaredProvenance(String parameterChecksum,
            String geometryIdentity, String datasetChecksum,
            String checkpointChecksum, String rootParameterChecksum) {}

    public record ProvenanceVerification(String actualCheckpointChecksum,
            boolean checkpointCryptographicallyVerified,
            boolean checkpointPayloadVerified,
            boolean rootCryptographicallyVerified,
            String rootVerificationReason) {}
}
