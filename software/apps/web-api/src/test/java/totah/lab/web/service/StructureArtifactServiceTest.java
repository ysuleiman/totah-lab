package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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
    void discoversRepositoryArtifactRootFromParentWorkingDirectory()
            throws IOException {
        Path repository = Files.createDirectories(artifactRoot.resolve("repo"));
        Path expected = Files.createDirectories(repository.resolve(
                "resources/shared-resources/src/main/resources"));
        Path workingDirectory = Files.createDirectories(repository.resolve(
                "software/apps"));

        assertThat(StructureArtifactService.discoverArtifactRoot(
                workingDirectory)).isEqualTo(expected);
    }

    @Test
    void rejectsAbsoluteAndEscapingLocations() {
        StructureArtifactService service =
                new StructureArtifactService(artifactRoot.toString());

        assertThatThrownBy(() -> service.resolveStorageLocation(
                artifactRoot.resolve("structure.pdb").toString()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("outside the allowed roots");
        assertThatThrownBy(() -> service.resolveStorageLocation(
                "../outside.pdb"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("escapes configured root");
    }

    @Test
    void loadsCanonicalMettl7bArtifactWithoutChangingResidueOrder()
            throws IOException {
        Path targetDirectory = Files.createDirectories(
                artifactRoot.resolve("Q6UX53"));
        try (var input = getClass().getResourceAsStream(
                "/Q6UX53/Q6UX53_TMT1B_HUMAN.pdb")) {
            Files.copy(input, targetDirectory.resolve(
                    "Q6UX53_TMT1B_HUMAN.pdb"));
        }
        StructureArtifactService service =
                new StructureArtifactService(artifactRoot.toString());

        var structure = service.load(
                6L, "Q6UX53/Q6UX53_TMT1B_HUMAN.pdb");

        assertThat(structure.getResidueCount()).isEqualTo(244);
        assertThat(structure.getChains().getFirst().residues())
                .extracting(residue -> residue.getNumber())
                .containsExactlyElementsOf(
                        java.util.stream.IntStream.rangeClosed(1, 244)
                                .boxed()
                                .toList());
    }
}
