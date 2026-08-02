package totah.lab.docking.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import totah.lab.daedalus.docking.importer.LocalArtifactUriResolver;

class LocalArtifactUriResolverTest {

    private final LocalArtifactUriResolver resolver =
            new LocalArtifactUriResolver(Path.of("/artifact-root"));

    @Test
    void resolvesChemflowLocalArtifactUri() {
        Path result = resolver.resolve(URI.create(
                "local://artifact-storage/run-id/pose-id.pdbqt"
        ));

        assertThat(result).isEqualTo(
                Path.of("/artifact-root/run-id/pose-id.pdbqt")
        );
    }

    @Test
    void rejectsPathTraversal() {
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(
                URI.create("local://artifact-storage/../../secret")
        ));
    }

    @Test
    void rejectsUnknownScheme() {
        assertThatIllegalArgumentException().isThrownBy(() -> resolver.resolve(
                URI.create("s3://bucket/pose.pdbqt")
        ));
    }
}
