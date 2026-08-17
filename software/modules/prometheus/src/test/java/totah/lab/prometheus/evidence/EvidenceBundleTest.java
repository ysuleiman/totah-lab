package totah.lab.prometheus.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

class EvidenceBundleTest {

    @Test
    void replayWithOnlyANewIngestionTimestampIsIdempotent() {
        EvidenceBundle bundle = new EvidenceBundle();
        QuantumEvidence first = evidence(Instant.parse("2025-01-01T00:00:00Z"), Optional.of(-10.0));
        QuantumEvidence replay = evidence(Instant.parse("2026-01-01T00:00:00Z"), Optional.of(-10.0));

        assertThat(bundle.add(first)).isTrue();
        assertThat(bundle.add(replay)).isFalse();
        assertThat(bundle.size()).isOne();
    }

    @Test
    void replayWithDifferentScientificPayloadStillFailsClosed() {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(evidence(Instant.parse("2025-01-01T00:00:00Z"), Optional.of(-10.0)));

        assertThatThrownBy(() -> bundle.add(
                evidence(Instant.parse("2026-01-01T00:00:00Z"), Optional.of(-11.0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("collision");
    }

    private static QuantumEvidence evidence(Instant ingestedAt, Optional<Double> energy) {
        EvidenceIdentity identity = EvidenceFixtures.identity(CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP, TslFixtures.geometryIdentityA());
        EvidenceProvenance provenance = new EvidenceProvenance("/archive/result.json", "abc", ingestedAt,
                List.of(), "fixture");
        return new QuantumEvidence(identity, provenance, ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED, energy, Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), "converged");
    }
}
