package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.web.persistence.ArtifactRepository;
import totah.lab.web.persistence.PipelineRunRepository;
import totah.lab.web.persistence.PocketAlphaSphereRepository;
import totah.lab.web.persistence.PocketRepository;
import totah.lab.web.persistence.PocketShapeDescriptorRepository;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.persistence.ResidueRepository;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.persistence.TargetRepository;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verifies the parse-validate phase the bulk import runner uses for its
 * dry run: it is database-free (all repositories are mocked and never
 * touched) and performs the exact same validation as the import.
 */
class AlphaFoldPocketImportServiceDryRunTest {

    @TempDir
    Path tempDir;

    private final AlphaFoldPocketImportService service =
            new AlphaFoldPocketImportService(
                    mock(ReceptorRepository.class),
                    mock(StructureRepository.class),
                    mock(ResidueRepository.class),
                    mock(PocketRepository.class),
                    mock(ArtifactRepository.class),
                    mock(TargetRepository.class),
                    mock(PipelineRunRepository.class),
                    new PocketShapeDescriptorService(
                            mock(PocketRepository.class),
                            mock(PocketAlphaSphereRepository.class),
                            mock(PocketShapeDescriptorRepository.class)
                    ),
                    8
            );

    @Test
    void parsesValidFixtureWithoutDatabase() throws Exception {
        AlphaFoldPocketImportService.ParsedImport parsed =
                service.parseAndValidate(
                        fixturePath(
                                "import/AF-P99901-F1-model_v4.pdb.gz"),
                        fixturePath(
                                "import/AF-P99901-F1-model_v4_out")
                );

        assertThat(parsed.pockets()).hasSize(2);
        assertThat(parsed.structure().getChains()).hasSize(1);
    }

    @Test
    void rejectsCorruptGzipBeforeAnyDatabaseAccess() throws IOException {
        Path corrupt = tempDir.resolve("AF-P99902-F1-model_v4.pdb.gz");
        Files.writeString(corrupt, "this is not gzip data");

        assertThatThrownBy(() -> service.parseAndValidate(
                corrupt,
                fixturePath("import/AF-P99901-F1-model_v4_out")
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Cannot decompress");
    }

    @Test
    void rejectsMissingFpocketOutput() {
        assertThatThrownBy(() -> service.parseAndValidate(
                fixturePath("import/AF-P99901-F1-model_v4.pdb.gz"),
                tempDir
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No fpocket info file found");
    }

    private Path fixturePath(String resource) {
        try {
            return Path.of(getClass()
                    .getClassLoader()
                    .getResource(resource)
                    .toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
