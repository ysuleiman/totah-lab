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
