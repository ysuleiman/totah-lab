package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.recovery.FieldSourceProvenance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class ArtifactLines {
    private final Path path;
    private final String sha256;
    private final List<String> lines;

    private ArtifactLines(Path path, String sha256, List<String> lines) {
        this.path = path;
        this.sha256 = sha256;
        this.lines = lines;
    }

    static ArtifactLines read(Path path) throws IOException {
        return new ArtifactLines(path, ArtifactChecksums.sha256(path), Files.readAllLines(path));
    }

    List<String> lines() { return lines; }

    FieldSourceProvenance line(int zeroBasedLine) {
        return new FieldSourceProvenance(path.toAbsolutePath().normalize().toString(), sha256,
                "line:" + (zeroBasedLine + 1), "authoritative text parse");
    }

    FieldSourceProvenance field(String field) {
        return new FieldSourceProvenance(path.toAbsolutePath().normalize().toString(), sha256,
                field, "authoritative structured-field parse");
    }
}
