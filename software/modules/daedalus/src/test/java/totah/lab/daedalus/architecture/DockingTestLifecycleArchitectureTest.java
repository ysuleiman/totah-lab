package totah.lab.daedalus.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DockingTestLifecycleArchitectureTest {
    private static final List<Path> EXTERNAL_DOCKING_TESTS = List.of(
            Path.of("src/test/java/totah/lab/daedalus/docking/LigandDockingAcceptanceTest.java"),
            Path.of("src/test/java/totah/lab/daedalus/docking/Brics0049RedockVerificationTest.java"),
            Path.of("src/test/java/totah/lab/daedalus/cli/DaedalusCliVinaAcceptanceTest.java"));

    @Test
    void externalDockingTestsAreTaggedAndExcludedFromDefaultSurefire() throws Exception {
        for (Path test : EXTERNAL_DOCKING_TESTS) {
            assertTrue(Files.readString(test).contains("@Tag(\"docking-integration\")"),
                    () -> test + " must be integration-tagged");
        }
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<excludedGroups>docking-integration</excludedGroups>"));
        assertTrue(pom.contains("<id>docking-integration</id>"));
        assertTrue(pom.contains("<goal>integration-test</goal>"));
        assertTrue(pom.contains("<groups>docking-integration</groups>"));
    }
}
