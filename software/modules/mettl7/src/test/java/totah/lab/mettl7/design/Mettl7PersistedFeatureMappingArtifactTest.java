package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7PersistedFeatureMappingArtifactTest {
    @Test void persistedOnlyMappingCorpusIsCompleteNonGatingAndChecksumValid() throws Exception {
        Path repository=Path.of(System.getProperty("user.dir")).resolve("../../../").normalize();
        Path campaign=repository.resolve("analysis/mettl7/mettl7b_design_campaign_v1");
        var receipt=new ObjectMapper().readTree(campaign.resolve("METTL7B_FEATURE_MAPPING_RECEIPT.json").toFile());
        assertThat(receipt.path("input_candidates").asInt()).isEqualTo(57);
        assertThat(receipt.path("input_conformers").asInt()).isEqualTo(171);
        assertThat(receipt.path("feature_mapping_complete").asBoolean()).isTrue();
        assertThat(receipt.path("b_template_3d_evaluation_complete").asBoolean()).isTrue();
        assertThat(receipt.path("a_counter_3d_evaluation_complete").asBoolean()).isTrue();
        assertThat(receipt.path("rmsd_hard_cutoff_applied").asBoolean()).isFalse();
        assertThat(receipt.path("molecules_regenerated").asBoolean()).isFalse();
        assertThat(receipt.path("conformers_regenerated").asBoolean()).isFalse();
        assertThat(receipt.path("docking_run").asBoolean()).isFalse();
        assertThat(Files.readAllLines(campaign.resolve("METTL7B_CONFORMER_FEATURE_MAPPING.csv"))).hasSize(172);
        assertThat(Files.readAllLines(campaign.resolve("METTL7B_CANDIDATE_B_A_PREFILTER_MATRIX.csv"))).hasSize(58);
        for(String line:Files.readAllLines(campaign.resolve("METTL7B_FEATURE_MAPPING_SHA256SUMS"))){
            int delimiter=line.indexOf("  ");Path file=campaign.resolve(line.substring(delimiter+2));
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            assertThat(actual).isEqualTo(line.substring(0,delimiter));
        }
    }
}
