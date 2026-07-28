package totah.lab.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

final class PipelineRunDirectoryFactory {

    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private PipelineRunDirectoryFactory() {
    }

    static Path create(Path workspace) throws IOException {
        Objects.requireNonNull(workspace, "workspace is null");
        Path runDirectory = workspace.resolve(LocalDateTime.now().format(RUN_ID_FORMAT));
        Files.createDirectories(runDirectory);
        return runDirectory;
    }
}
