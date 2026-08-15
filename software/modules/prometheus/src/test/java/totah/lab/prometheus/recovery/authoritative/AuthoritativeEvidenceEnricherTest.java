package totah.lab.prometheus.recovery.authoritative;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.ingest.LegacyPhase2ArchiveIngester;
import totah.lab.prometheus.inventory.EvidenceInventoryService;

class AuthoritativeEvidenceEnricherTest {

    @Test
    void enrichesOnlyAuthoritativelyRecoveredProtocolFieldsAndPreservesAllRecords() throws Exception {
        Path archive = repositoryRoot().resolve("analysis/mettl7-phase2");
        EvidenceBundle original = new LegacyPhase2ArchiveIngester().ingest(archive).bundle();
        EvidenceBundle enriched = new AuthoritativeEvidenceEnricher().enrich(archive, original);

        assertThat(original.quantum()).hasSize(100);
        assertThat(original.classical()).hasSize(22);
        assertThat(enriched.quantum()).hasSize(100);
        assertThat(enriched.classical()).hasSize(22);
        assertThat(new EvidenceInventoryService(original).snapshot().provenanceGaps()).hasSize(197);
        assertThat(new EvidenceInventoryService(enriched).snapshot().provenanceGaps()).hasSize(67);

        Map<String, QuantumEvidence> originalBySource = original.quantum().stream().collect(Collectors.toMap(
                item -> item.provenance().sourcePath() + "\0" + item.identity().calculationType()
                        + "\0" + item.identity().geometry().sha256(),
                Function.identity(), (left, right) -> left));
        enriched.quantum().forEach(item -> {
            QuantumEvidence before = originalBySource.get(item.provenance().sourcePath() + "\0"
                    + item.identity().calculationType() + "\0" + item.identity().geometry().sha256());
            assertThat(before).isNotNull();
            if (!before.identity().protocol().equals(item.identity().protocol())) {
                assertThat(item.provenance().note()).contains("authoritative_protocol_recovery[");
                assertThat(item.provenance().note()).contains("#");
            }
        });

        // A legacy point with no calculation-linked software artifact remains unknown:
        // absence is never replaced from directory naming or a narrative report.
        assertThat(enriched.quantum()).anySatisfy(item -> {
            if (item.provenance().sourcePath().contains("execution-unit-05D/full-molecule-dft")) {
                assertThat(item.identity().protocol().software()).isEqualToIgnoringCase("unknown");
            }
        });
    }

    private static Path repositoryRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return working.endsWith(Path.of("software/modules/prometheus"))
                ? working.resolve("../../..").normalize()
                : working;
    }
}
