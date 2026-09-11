package totah.lab.mettl7.recognition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7RecognitionBasinExecutorTest {
    @TempDir Path temporary;
    @Test void executesFrozenGridWithoutLosingMaterializedProvenance() throws Exception {
        Path root=Path.of("../../..").toAbsolutePath().normalize();
        Path output=System.getProperty("mettl7.basin.output")==null?temporary:Path.of(System.getProperty("mettl7.basin.output"));
        var result=Mettl7RecognitionBasinExecutor.execute(root,output);
        assertThat(result.rawPoseCount()).isEqualTo(227);assertThat(result.observationCount()).isEqualTo(227);
        assertThat(result.primary()).containsOnlyKeys("NETARSUDIL_A","NETARSUDIL_B","DCMB_A_R","DCMB_A_S","DCMB_B_R","DCMB_B_S");
        assertThat(Files.readString(result.receipt())).contains("pair_direction=MAXIMUM")
                .contains("NETARSUDIL_A={ADMITTED_ADEQUATE=60}")
                .contains("DCMB_B_S={ADMITTED_ADEQUATE=26}")
                .contains("SURFDIFF=ATTACHED_FROZEN_DIRECTIONAL_MAPS")
                .contains("formal_roles=[UNRESOLVED]")
                .contains("recurrent_edge_evidence_sha256=");
        assertThat(Files.readString(output.resolve("RECURRENT_EDGE_EVIDENCE.csv")))
                .contains("\"DCMB_B_R\",\"199\",\"HALOGEN_BOND\"")
                .contains("\"NETARSUDIL_B\",\"196\",\"PI_CATION\"");
        assertThat(Files.readAllLines(result.basins())).hasSizeGreaterThan(1);
        assertThat(Files.readAllLines(result.pairs())).hasSizeGreaterThan(1);
    }

    @Test void emptyExecutionDoesNotCertifyAbsentCompounds() throws Exception {
        Path output = temporary.resolve("empty");
        var summary = new Mettl7RecognitionBatchMaterializer.Summary(java.util.List.of(),
                temporary.resolve("accounting.csv"), temporary.resolve("materialization.txt"));
        var result = Mettl7RecognitionBasinExecutor.execute(temporary, output,
                new Mettl7RecognitionBatchMaterializer.EvidenceSummary(summary, java.util.List.of()));
        assertThat(result.primary()).isEmpty();
        assertThat(result.observationCount()).isZero();
        assertThat(Files.readString(result.receipt()))
                .contains("materialization_arm_outcome_counts={}", "evaluated_arm_quality_counts={}")
                .doesNotContain("NETARSUDIL", "DCMB", "ADEQUATE");
    }

    @Test void identicalExecutionProducesByteIdenticalScientificArtifacts() throws Exception {
        Path root=Path.of("../../..").toAbsolutePath().normalize();
        var first=Mettl7RecognitionBasinExecutor.execute(root,temporary.resolve("replay-a"));
        var second=Mettl7RecognitionBasinExecutor.execute(root,temporary.resolve("replay-b"));
        assertThat(Files.readAllBytes(first.basins())).isEqualTo(Files.readAllBytes(second.basins()));
        assertThat(Files.readAllBytes(first.pairs())).isEqualTo(Files.readAllBytes(second.pairs()));
        assertThat(Files.readAllBytes(first.receipt())).isEqualTo(Files.readAllBytes(second.receipt()));
    }

    @Test void serializedEvidenceSuppressesNonScientificFloatingPointNoise() {
        assertThat(Mettl7RecognitionBasinExecutor.decimal(1.6211712632247960))
                .isEqualTo(Mettl7RecognitionBasinExecutor.decimal(1.6211712632247963));
        assertThat(Mettl7RecognitionBasinExecutor.decimal(1.6211712632247960))
                .isEqualTo("1.621171263225");
    }
}
