package totah.lab.pocket.visualization;

import java.nio.file.Path;

public record ExportResult(
        Path topProjection,
        Path sideProjection,
        Path endProjection,
        Path crossSection) {
}