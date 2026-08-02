package totah.lab.pocket.visualization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.reader.StructureReader;

import java.util.List;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PocketDatasetTest {
    @Test
    void defensivelyCopiesPockets() {
        Protein protein = new Protein(
                "target", null, "target", null, null, null,
                new Structure(List.of()));

        PocketDataset dataset = new PocketDataset(protein, List.of());

        assertThat(dataset.protein()).isSameAs(protein);
        assertThat(dataset.pockets()).isEmpty();
        assertThatThrownBy(() -> dataset.pockets().add((Pocket) null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loaderReportsMissingStructureAsCheckedIo(@TempDir Path directory) {
        PocketDatasetLoader loader = new PocketDatasetLoader(
                new StructureReader() {
                    @Override
                    public Structure read(Path path) {
                        throw new AssertionError("Reader must not be called");
                    }

                    @Override
                    public boolean supports(Path path) {
                        return true;
                    }
                });

        assertThatThrownBy(() -> loader.load(directory))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("No PDB, CIF, or mmCIF structure");
    }
}
