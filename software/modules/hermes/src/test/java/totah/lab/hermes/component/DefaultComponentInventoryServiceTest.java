package totah.lab.hermes.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hermes.ccd.CcdDownloader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultComponentInventoryServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void downloadsOnlyMeaningfulComponentsAndReturnsStructuredSummary() throws Exception {
        Path structures = temporaryDirectory.resolve("structures");
        Files.createDirectories(structures);
        Files.writeString(structures.resolve("1ABC.cif"), cif());
        List<String> requested = new ArrayList<>();
        var service = new DefaultComponentInventoryService(
                new LigandInventoryBuilder(), (componentId, root) -> {
                    requested.add(componentId);
                    Path directory = root.resolve(componentId);
                    Files.createDirectories(directory);
                    Path cif = Files.writeString(directory.resolve(componentId + ".cif"),
                            "data_" + componentId + "\n");
                    Path sdf = Files.writeString(directory.resolve(
                            componentId + "_ideal.sdf"), "V2000\nM  END\n");
                    return new CcdDownloader.ComponentDownload(componentId,
                            CcdDownloader.FetchStatus.DOWNLOADED,
                            CcdDownloader.FetchStatus.DOWNLOADED, cif, sdf);
                });

        ComponentInventoryResult result = service.build(new ComponentInventoryRequest(
                structures, temporaryDirectory.resolve("output"), true, false));

        assertThat(requested).containsExactly("SAM");
        assertThat(result.summary().totalOccurrences()).isEqualTo(2);
        assertThat(result.summary().distinctComponents()).isEqualTo(2);
        assertThat(result.summary().sam().occurrences()).isEqualTo(1);
        assertThat(result.summary().ccdCifOutcomes())
                .containsEntry(CcdDownloader.FetchStatus.DOWNLOADED, 1);
        assertThat(result.inventory().components().get("SAM").ccdCif()).exists();
        assertThat(result.inventory().components().get("HOH").ccdCif()).isNull();
    }

    @Test
    void dryRunHasNoDownloadSideEffects() throws Exception {
        Path structures = temporaryDirectory.resolve("dry-structures");
        Files.createDirectories(structures);
        Files.writeString(structures.resolve("1ABC.cif"), cif());
        var service = new DefaultComponentInventoryService(
                new LigandInventoryBuilder(), (componentId, root) -> {
                    throw new AssertionError("dry run must not call CCD client");
                });

        ComponentInventoryResult result = service.build(new ComponentInventoryRequest(
                structures, temporaryDirectory.resolve("dry-output"), true, true));

        assertThat(result.downloads()).isEmpty();
        assertThat(temporaryDirectory.resolve("dry-output")).doesNotExist();
    }

    private String cif() {
        return """
                data_1ABC
                loop_
                _atom_site.group_PDB
                _atom_site.id
                _atom_site.type_symbol
                _atom_site.label_atom_id
                _atom_site.label_alt_id
                _atom_site.label_comp_id
                _atom_site.label_asym_id
                _atom_site.label_seq_id
                _atom_site.Cartn_x
                _atom_site.Cartn_y
                _atom_site.Cartn_z
                _atom_site.occupancy
                _atom_site.auth_seq_id
                _atom_site.auth_asym_id
                _atom_site.pdbx_PDB_model_num
                HETATM 1 C C1 . SAM B . 1.0 2.0 3.0 1.0 501 A 1
                HETATM 2 O O  . HOH W . 4.0 5.0 6.0 1.0 601 A 1
                #
                """;
    }
}
