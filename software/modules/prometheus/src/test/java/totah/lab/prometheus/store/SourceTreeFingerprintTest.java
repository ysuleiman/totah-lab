package totah.lab.prometheus.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceTreeFingerprintTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void isStableAcrossCreationOrderAndChangesWithSelectedContent() throws IOException {
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        Files.createDirectories(first);
        Files.createDirectories(second);
        Files.writeString(first.resolve("b.manifest"), "beta");
        Files.writeString(first.resolve("a.manifest"), "alpha");
        Files.writeString(second.resolve("a.manifest"), "alpha");
        Files.writeString(second.resolve("b.manifest"), "beta");
        Files.writeString(first.resolve("ignored.raw"), "one");
        Files.writeString(second.resolve("ignored.raw"), "different");

        String firstHash = SourceTreeFingerprint.calculate(first,
                path -> path.getFileName().toString().endsWith(".manifest"));
        String secondHash = SourceTreeFingerprint.calculate(second,
                path -> path.getFileName().toString().endsWith(".manifest"));
        assertThat(firstHash).isEqualTo(secondHash);

        Files.writeString(second.resolve("b.manifest"), "changed");
        assertThat(SourceTreeFingerprint.calculate(second,
                path -> path.getFileName().toString().endsWith(".manifest")))
                .isNotEqualTo(firstHash);
    }
}
