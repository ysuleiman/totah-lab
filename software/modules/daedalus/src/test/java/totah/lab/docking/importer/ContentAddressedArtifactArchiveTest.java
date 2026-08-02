package totah.lab.docking.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.daedalus.docking.importer.ArchivedArtifact;
import totah.lab.daedalus.docking.importer.ContentAddressedArtifactArchive;

class ContentAddressedArtifactArchiveTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void archivesIdenticalContentOnlyOnce() throws Exception {
        Path firstSource = temporaryDirectory.resolve("first.pdbqt");
        Path secondSource = temporaryDirectory.resolve("second.pdbqt");
        Files.writeString(firstSource, "MODEL 1\nENDMDL\n");
        Files.writeString(secondSource, "MODEL 1\nENDMDL\n");
        ContentAddressedArtifactArchive archive =
                new ContentAddressedArtifactArchive(
                        temporaryDirectory.resolve("archive")
                );

        ArchivedArtifact first = archive.archive(firstSource, "pdbqt");
        ArchivedArtifact second = archive.archive(secondSource, "pdbqt");

        assertThat(second).isEqualTo(first);
        assertThat(Files.readString(first.path()))
                .isEqualTo("MODEL 1\nENDMDL\n");
    }
}
