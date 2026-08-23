package totah.lab.prometheus.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

/**
 * TEST_ID: B8 (case 4) — a registered evidence record cannot be altered
 * through any accessor: list-bearing accessors must reject mutation or make it
 * invisible (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, B8).
 */
class AdversarialEvidenceImmutabilityAcceptanceTest {

    /**
     * TEST_ID: B8 — {@code QuantumEvidence.gradientHartreePerBohr()} must not
     * hand out a mutable list.
     */
    @Test
    void gradientListRejectsMutation() {
        QuantumEvidence evidence = evidence(new ArrayList<>(List.of(0.13, -0.27, 0.41)));

        assertThatThrownBy(() -> evidence.gradientHartreePerBohr().orElseThrow().add(0.0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> evidence.gradientHartreePerBohr().orElseThrow().set(0, 999.0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(evidence.gradientHartreePerBohr().orElseThrow())
                .containsExactly(0.13, -0.27, 0.41);
    }

    /**
     * TEST_ID: B8 (constructor side) — mutating the caller's list after
     * construction must not alter the record.
     */
    @Test
    void mutatingCallerListAfterConstructionIsInvisible() {
        List<Double> gradient = new ArrayList<>(List.of(0.13, -0.27, 0.41));
        QuantumEvidence evidence = evidence(gradient);

        gradient.add(999.0);
        gradient.set(0, -999.0);

        assertThat(evidence.gradientHartreePerBohr().orElseThrow())
                .containsExactly(0.13, -0.27, 0.41);
    }

    private static QuantumEvidence evidence(List<Double> gradient) {
        return new QuantumEvidence(
                EvidenceFixtures.identity(
                        CalculationType.SINGLE_POINT,
                        EvidenceFixtures.PBE_DEF2_SVP,
                        TslFixtures.geometryIdentityA()),
                EvidenceFixtures.provenance("/archive/tsl/b8.log"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-100.5),
                Optional.of(gradient),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "converged normally");
    }
}
