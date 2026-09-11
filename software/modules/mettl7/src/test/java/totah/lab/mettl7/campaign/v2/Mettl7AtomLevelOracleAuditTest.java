package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7AtomLevelOracleAuditTest {
    @TempDir Path temporary;

    @Test void discrepancyTypesHaveCanonicalOutputOrder() throws Exception {
        Path athena = temporary.resolve("athena.csv");
        Files.writeString(athena, "pose_model,run_id,species_id,receptor_id,athena_refined_interaction_details\n"
                + "1,run,species,A-receptor,\n");
        Path root = temporary.resolve("plip/run");
        Files.createDirectories(root);
        Files.writeString(root.resolve("run.xml"), "<report><bindingsite><hetid>LIG</hetid>"
                + "<hydrogen_bond/><salt_bridge/><pi_cation_interaction/></bindingsite></report>");
        Path output = temporary.resolve("audit.csv");

        Mettl7AtomLevelOracleAudit.audit(athena, temporary.resolve("plip"), output);

        List<String> rows = Files.readAllLines(output);
        assertThat(rows).hasSize(4);
        assertThat(rows.get(1)).contains(",\"hbond\",");
        assertThat(rows.get(2)).contains(",\"salt\",");
        assertThat(rows.get(3)).contains(",\"pi_cation\",");
    }
}
