package totah.lab.prometheus.neural.ferminet.force;

import java.io.IOException;
import java.nio.file.Path;
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
}
