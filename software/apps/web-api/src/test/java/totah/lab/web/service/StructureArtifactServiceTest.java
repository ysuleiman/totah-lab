package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructureArtifactServiceTest {

    @TempDir
    Path artifactRoot;

    @Test
    void resolvesPortableArtifactKeyAgainstConfiguredRoot() throws Exception {
        StructureArtifactService service =
                new StructureArtifactService(artifactRoot.toString());

        assertThat(service.resolveStorageLocation(
                "Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"))
                .isEqualTo(artifactRoot.resolve(
                        "Q6UX53/Q6UX53_TMT1B_HUMAN.pdb"));
    }

    @Test
    void rejectsAbsoluteAndEscapingLocations() {
        StructureArtifactService service =
                new StructureArtifactService(artifactRoot.toString());

        assertThatThrownBy(() -> service.resolveStorageLocation(
                artifactRoot.resolve("structure.pdb").toString()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("must be relative");
        assertThatThrownBy(() -> service.resolveStorageLocation(
                "../outside.pdb"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("escapes configured root");
    }
}
