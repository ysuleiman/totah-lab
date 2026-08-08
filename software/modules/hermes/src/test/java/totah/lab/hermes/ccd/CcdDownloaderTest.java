package totah.lab.hermes.ccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class CcdDownloaderTest {

    @TempDir
    Path tempDir;

    @Test
    void skipsExistingNonEmptyFilesWithoutFetching() throws IOException {
        Path componentDir = tempDir.resolve("SAM");
        Files.createDirectories(componentDir);
        Files.writeString(componentDir.resolve("SAM.cif"), "data_SAM\n");
        Files.writeString(componentDir.resolve("SAM_ideal.sdf"), """
                SAM
                  test

                  1  0  0  0  0  0            999 V2000
                    0.0000    0.0000    0.0000 C   0  0  0  0  0  0
                M  END
                $$$$
                """);

        CcdDownloader downloader = new CcdDownloader((url, target) ->
                fail("No HTTP fetch expected for already-present files, got: "
                        + url));

        CcdDownloader.ComponentDownload result =
                downloader.downloadComponent("SAM", tempDir);

        assertThat(result.ccdCifStatus())
                .isEqualTo(CcdDownloader.FetchStatus.ALREADY_PRESENT);
        assertThat(result.idealSdfStatus())
                .isEqualTo(CcdDownloader.FetchStatus.ALREADY_PRESENT);
        assertThat(result.ccdCif())
                .isEqualTo(componentDir.resolve("SAM.cif"));
        assertThat(result.idealSdf())
                .isEqualTo(componentDir.resolve("SAM_ideal.sdf"));
    }

    @Test
    void downloadsMissingFilesViaInjectedFetcher() throws IOException {
        List<String> fetchedUrls = new ArrayList<>();
        CcdDownloader downloader = new CcdDownloader((url, target) -> {
            fetchedUrls.add(url);
            Files.writeString(target, "content of " + url);
            return CcdDownloader.FetchStatus.DOWNLOADED;
        });

        CcdDownloader.ComponentDownload result =
                downloader.downloadComponent("SAH", tempDir);

        assertThat(fetchedUrls).containsExactly(
                "https://files.rcsb.org/ligands/view/SAH.cif",
                "https://files.rcsb.org/ligands/view/SAH_ideal.sdf");
        assertThat(result.ccdCifStatus())
                .isEqualTo(CcdDownloader.FetchStatus.DOWNLOADED);
        assertThat(result.idealSdfStatus())
                .isEqualTo(CcdDownloader.FetchStatus.DOWNLOADED);
        assertThat(tempDir.resolve("SAH/SAH.cif")).hasContent(
                "content of https://files.rcsb.org/ligands/view/SAH.cif");
        assertThat(tempDir.resolve("SAH/SAH_ideal.sdf")).exists();
    }

    @Test
    void propagatesNotFoundAndLeavesNoPath() throws IOException {
        CcdDownloader downloader = new CcdDownloader((url, target) ->
                CcdDownloader.FetchStatus.NOT_FOUND);

        CcdDownloader.ComponentDownload result =
                downloader.downloadComponent("ZZZ", tempDir);

        assertThat(result.ccdCifStatus())
                .isEqualTo(CcdDownloader.FetchStatus.NOT_FOUND);
        assertThat(result.idealSdfStatus())
                .isEqualTo(CcdDownloader.FetchStatus.NOT_FOUND);
        assertThat(result.ccdCif()).isNull();
        assertThat(result.idealSdf()).isNull();
    }

    @Test
    void refetchesZeroByteFiles() throws IOException {
        Path componentDir = tempDir.resolve("GOL");
        Files.createDirectories(componentDir);
        Files.writeString(componentDir.resolve("GOL.cif"), "");

        List<String> fetchedUrls = new ArrayList<>();
        CcdDownloader downloader = new CcdDownloader((url, target) -> {
            fetchedUrls.add(url);
            Files.writeString(target, "recovered");
            return CcdDownloader.FetchStatus.DOWNLOADED;
        });

        CcdDownloader.ComponentDownload result =
                downloader.downloadComponent("GOL", tempDir);

        assertThat(fetchedUrls).contains(
                "https://files.rcsb.org/ligands/view/GOL.cif");
        assertThat(result.ccdCifStatus())
                .isEqualTo(CcdDownloader.FetchStatus.DOWNLOADED);
    }
}
