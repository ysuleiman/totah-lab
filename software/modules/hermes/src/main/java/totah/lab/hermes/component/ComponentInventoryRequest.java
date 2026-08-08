package totah.lab.hermes.component;

import java.nio.file.Path;
import java.util.Objects;

/** Inputs and side-effect policy for one component inventory run. */
public record ComponentInventoryRequest(
        Path structuresDirectory,
        Path outputDirectory,
        boolean downloadCcd,
        boolean dryRun
) {
    public ComponentInventoryRequest {
        Objects.requireNonNull(structuresDirectory, "structuresDirectory");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
    }
}
