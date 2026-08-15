package totah.lab.prometheus.planning;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The scientific content hash: stable, insensitive to specificationId, sensitive
 * to any scientific difference.
 */
class CalculationSpecificationTest {

    private static CalculationSpecification spec(String id, QmProtocol protocol) {
        return new CalculationSpecification(
                id,
                "fixture specification",
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA(),
                0,
                1,
                protocol,
                List.of(),
                CalculationType.SINGLE_POINT,
                List.of("energy"),
                List.of("convergence=CONVERGED", "acceptance=ACCEPTED"),
                DatasetRole.DEVELOPMENT,
                CostEstimate.zero());
    }

    @Test
    void checksumIsStableAcrossInvocationsAndInstances() {
        CalculationSpecification spec = spec("plan-1", EvidenceFixtures.PBE_DEF2_SVP);

        assertThat(spec.checksum()).isEqualTo(spec.checksum());
        assertThat(spec("plan-1", EvidenceFixtures.PBE_DEF2_SVP).checksum())
                .isEqualTo(spec.checksum());
    }

    @Test
    void specsDifferingOnlyInIdShareAChecksum() {
        CalculationSpecification a = spec("plan-1", EvidenceFixtures.PBE_DEF2_SVP);
        CalculationSpecification b = spec("plan-2", EvidenceFixtures.PBE_DEF2_SVP);

        assertThat(a.specificationId()).isNotEqualTo(b.specificationId());
        assertThat(a.checksum()).isEqualTo(b.checksum());
    }

    @Test
    void differentBasisChangesTheChecksum() {
        QmProtocol otherBasis = new QmProtocol(
                "PBE", "def2-TZVP", "D3(BJ)", "none", false, "ORCA", "5.0.4");

        assertThat(spec("plan-1", otherBasis).checksum())
                .isNotEqualTo(spec("plan-1", EvidenceFixtures.PBE_DEF2_SVP).checksum());
    }

    @Test
    void emptyAcceptanceGatesAreRejected() {
        assertThatThrownBy(() -> new CalculationSpecification(
                "plan-1",
                "fixture specification",
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA(),
                0,
                1,
                EvidenceFixtures.PBE_DEF2_SVP,
                List.of(),
                CalculationType.SINGLE_POINT,
                List.of("energy"),
                List.of(),
                DatasetRole.DEVELOPMENT,
                CostEstimate.zero()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("acceptanceGates");
    }
}
