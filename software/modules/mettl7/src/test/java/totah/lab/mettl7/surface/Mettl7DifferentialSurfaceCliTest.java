package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7DifferentialSurfaceCliTest {
    private static final Path ROOT = Path.of("../../..",
            "research/mettl7-netarsudil-sam-mechanism/local-flexibility/prepared");

    @Test
    void writesBothDirectionsIncludingUnmatchedSurfaceNeighborhoods()
            throws Exception {
        Path output = Files.createTempDirectory("mettl7-surfdiff-");
        Mettl7DifferentialSurfaceCli.run(
                ROOT.resolve("METTL7A_protein_only.pdb"),
                ROOT.resolve("METTL7B_protein_only.pdb"), output);

        assertThat(output.resolve("METTL7_AB_CORRESPONDENCE_V1.csv"))
                .content().hasLineCount(245);
        assertThat(output.resolve("METTL7B_VS_METTL7A_residue_scores.csv"))
                .exists().isNotEmptyFile();
        assertThat(output.resolve("METTL7A_VS_METTL7B_residue_scores.csv"))
                .exists().isNotEmptyFile();
        assertThat(output.resolve("METTL7B_VS_METTL7A_neighborhood_evidence.csv"))
                .content().contains(",QUERY_ONLY");
        assertThat(output.resolve("analysis_receipt.txt"))
                .content().contains("coverage=1.0");
    }
}
