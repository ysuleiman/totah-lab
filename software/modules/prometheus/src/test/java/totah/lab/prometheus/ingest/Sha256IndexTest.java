package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sha256IndexTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesEntriesAndMatchesByRelativePath() throws Exception {
        Path sums = tempDir.resolve("SHA256SUMS");
        Files.writeString(sums, """
                e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  empty.txt
                aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999  dir/result.json
                """);

        Sha256Index index = Sha256Index.parse(sums);

        assertThat(index.size()).isEqualTo(2);
        assertThat(index.expectedHash("empty.txt")).contains(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertThat(index.expectedHash("dir/result.json")).contains(
                "aaaabbbbccccddddeeeeffff0000111122223333444455556666777788889999");
        assertThat(index.expectedHash("missing.txt")).isEmpty();
    }

    @Test
    void suffixMatchesDifferentlyAnchoredEntries() throws Exception {
        Path sums = tempDir.resolve("SHA256SUMS");
        Files.writeString(sums,
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
                        + "  /tmp/staging-area/01_POINT/result.json\n");

        Sha256Index index = Sha256Index.parse(sums);

        assertThat(index.expectedHash("01_POINT/result.json")).contains(
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");
    }

    @Test
    void hashFileStreamsContent() throws Exception {
        Path file = tempDir.resolve("data.bin");
        Files.write(file, new byte[200_000]);

        String original = Sha256Index.hashFile(file);
        assertThat(original).hasSize(64);
        assertThat(Sha256Index.hashFile(file)).isEqualTo(original);

        Files.writeString(file, "tampered");
        assertThat(Sha256Index.hashFile(file)).isNotEqualTo(original);
    }

    @Test
    void detectsMatchAndMismatchAgainstRealHashes() throws Exception {
        Path file = tempDir.resolve("artifact.txt");
        Files.writeString(file, "original content");
        String actual = Sha256Index.hashFile(file);

        Path sums = tempDir.resolve("SHA256SUMS");
        Files.writeString(sums, actual + "  artifact.txt\n");

        Sha256Index index = Sha256Index.parse(sums);
        assertThat(index.expectedHash("artifact.txt")).contains(actual);

        Files.writeString(file, "tampered content");
        assertThat(Sha256Index.hashFile(file)).isNotEqualTo(index.expectedHash("artifact.txt").orElseThrow());
    }
}
