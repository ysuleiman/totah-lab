package totah.lab.athena.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AthenaDependencyTest {
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "totah.lab.mettl7",
            "totah.lab.hermes",
            "totah.lab.hephaestus",
            "totah.lab.daedalus",
            "totah.lab.argus",
            "totah.lab.atlas",
            "totah.lab.protein",
            "totah.lab.pocket.",
            "totah.lab.ligand");

    @Test
    void productionSourcesDependOnlyOnGaiaAndJava() throws IOException {
        Path sources = Path.of(System.getProperty("basedir"))
                .resolve("src/main/java");
        try (Stream<Path> paths = Files.walk(sources)) {
            List<String> violations = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> forbiddenImports(path).stream())
                    .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void genericDesignCannotImportLegacyTargetPolicyOrEmbedKnownTargetConcepts() throws IOException {
        var root = Path.of(System.getProperty("basedir")).resolve("src/main/java/totah/lab/athena/design");
        var forbidden = List.of("mettl7", "netarsudil", "dcmb", "totah.lab.daedalus", "enrichmentgate");
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                var source = Files.readString(path).toLowerCase(java.util.Locale.ROOT);
                for (var token : forbidden) assertThat(source).as(path + " contains " + token).doesNotContain(token);
            }
        }
    }

    private static List<String> forbiddenImports(Path source) {
        try {
            String text = Files.readString(source);
            return FORBIDDEN_IMPORTS.stream()
                    .filter(text::contains)
                    .map(value -> source + " imports " + value)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
