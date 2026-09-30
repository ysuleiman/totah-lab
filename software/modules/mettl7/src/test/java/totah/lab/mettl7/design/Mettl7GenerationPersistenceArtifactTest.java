package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7GenerationPersistenceArtifactTest {
    @Test void frozenFiftySevenCandidatePersistenceCorpusIsCompleteAndHasValidHashes() throws Exception {
        Path repository=Path.of(System.getProperty("user.dir")).resolve("../../../").normalize();
        Path campaign=repository.resolve("analysis/mettl7/mettl7b_design_campaign_v1");
        var receipt=new ObjectMapper().readTree(campaign.resolve("METTL7B_GENERATION_PERSISTENCE_RECEIPT.json").toFile());
        assertThat(receipt.path("candidate_persistence_complete").asBoolean()).isTrue();
        assertThat(receipt.path("candidate_set_reproduced_exactly").asBoolean()).isTrue();
        assertThat(receipt.path("persisted_candidate_count").asInt()).isEqualTo(57);
        assertThat(receipt.path("persisted_conformer_count").asInt()).isEqualTo(171);
        assertThat(receipt.path("scientific_grammar_changed").asBoolean()).isFalse();
        assertThat(receipt.path("docking_run").asBoolean()).isFalse();
        try(var directories=Files.list(campaign.resolve("persisted_candidates"))){
            assertThat(directories.filter(Files::isDirectory).count()).isEqualTo(57);
        }
        try(var conformers=Files.walk(campaign.resolve("persisted_candidates"))){
            assertThat(conformers.filter(Files::isRegularFile).filter(path->path.getFileName().toString().startsWith("conformer-")).count()).isEqualTo(171);
        }
        for(String line:Files.readAllLines(campaign.resolve("METTL7B_GENERATION_PERSISTENCE_SHA256SUMS"))){
            int delimiter=line.indexOf("  ");Path file=campaign.resolve(line.substring(delimiter+2));
            String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
            assertThat(actual).isEqualTo(line.substring(0,delimiter));
        }
    }
}
