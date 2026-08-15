package totah.lab.prometheus.ingest.authoritative;

import org.junit.jupiter.api.Test;
import totah.lab.prometheus.recovery.RecoveryClassification;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AmberRespReaderTest {
    private static final Path REAL = Path.of("..", "..", "..", "analysis", "mettl7-phase2",
            "execution-unit-05O", "native-amber-resp3min-hf631gd", "regeneration-A", "all-three");

    @Test
    void reconstructsAcceptedThreeConformerRespFromNativeArtifacts() throws Exception {
        AmberRespResult result = new AmberRespReader().read(REAL);

        assertThat(result.conformerIds().value().orElseThrow()).containsExactly("MIN01", "MIN02", "MIN04");
        assertThat(result.conformerFormalCharges().value().orElseThrow()).containsExactly(0, 0, 0);
        assertThat(result.atomCount().value()).contains(56);
        assertThat(result.stage1RestraintWeight().value()).contains(0.0005);
        assertThat(result.stage2RestraintWeight().value()).contains(0.0010);
        assertThat(result.stage2EquivalenceConstraints().value().orElseThrow())
                .containsEntry(28, 27).containsEntry(29, 27).containsEntry(54, 53).containsEntry(55, 53);
        assertThat(result.serializedCharges().value().orElseThrow()).hasSize(56);
        assertThat(result.serializedCharges().value().orElseThrow().get(25)).isEqualTo(-0.290064);
        assertThat(result.serializedCharges().value().orElseThrow().get(55)).isEqualTo(0.172272);
        assertThat(result.serializedTotalCharge().value().orElseThrow()).isCloseTo(-0.000001,
                org.assertj.core.data.Offset.offset(1e-12));
        assertThat(result.converged().value()).contains(true);
        assertThat(result.espQuantumMethod().classification()).isEqualTo(RecoveryClassification.GENUINELY_UNRECOVERABLE);
        assertThat(result.serializedCharges().provenance().getFirst().locator()).contains("charge-vector");
    }
}
