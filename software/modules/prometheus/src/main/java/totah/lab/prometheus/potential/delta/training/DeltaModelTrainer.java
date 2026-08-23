package totah.lab.prometheus.potential.delta.training;

import java.util.Objects;
import java.io.IOException;
import java.nio.file.Path;
import totah.lab.prometheus.persistence.FitArtifact;
import totah.lab.prometheus.persistence.FitArtifactWriter;

/** Fit entry point enforcing structural authorization before numerical training. */
public final class DeltaModelTrainer {
    public void requirePreflight(BasisPreflightResult preflight){if(!Objects.requireNonNull(preflight).pass())throw new IllegalStateException("BASIS_PREFLIGHT_PASS is false; fitting is prohibited");}
    /** The only SUCCESS reporting seam: persistence and read-back verification happen first. */
    public FitArtifactWriter.Receipt persistSuccessfulFit(Path directory, FitArtifact artifact) throws IOException {
        return new FitArtifactWriter().persistSuccessful(directory, artifact);
    }
}
