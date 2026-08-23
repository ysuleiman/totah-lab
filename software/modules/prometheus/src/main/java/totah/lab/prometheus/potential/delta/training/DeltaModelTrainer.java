package totah.lab.prometheus.potential.delta.training;

import java.util.Objects;
import java.io.IOException;
import java.nio.file.Path;
import totah.lab.prometheus.persistence.FitArtifact;
import totah.lab.prometheus.persistence.FitArtifactWriter;

/**
 * Preflight and persistence boundary for future Delta fitting.
 *
 * <p>No production fitter is currently implemented or wired through this class. The first
 * implementation that performs a fit must make a checksum-verified
 * {@link FitArtifactWriter.Receipt} part of its success contract. It must not return or report a
 * successful fitted result before {@link #persistSuccessfulFit(Path, FitArtifact)} completes.
 */
public final class DeltaModelTrainer {
    public void requirePreflight(BasisPreflightResult preflight){if(!Objects.requireNonNull(preflight).pass())throw new IllegalStateException("BASIS_PREFLIGHT_PASS is false; fitting is prohibited");}
    /** The only SUCCESS reporting seam: persistence and read-back verification happen first. */
    public FitArtifactWriter.Receipt persistSuccessfulFit(Path directory, FitArtifact artifact) throws IOException {
        return new FitArtifactWriter().persistSuccessful(directory, artifact);
    }
}
