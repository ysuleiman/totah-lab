package totah.lab.prometheus.evidence;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class QuantumEvidenceTest {

    private static QuantumEvidence build(ConvergenceStatus convergence, EvidenceAcceptanceState acceptance) {
        return new QuantumEvidence(
                EvidenceFixtures.identity(
                        CalculationType.SINGLE_POINT,
                        EvidenceFixtures.PBE_DEF2_SVP,
                        TslFixtures.geometryIdentityA()),
                EvidenceFixtures.provenance("/archive/tsl/sp.log"),
                convergence,
                acceptance,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "");
    }

    @Test
    void failedOrNotConvergedEvidenceCannotBeAccepted() {
        for (ConvergenceStatus status : List.of(
                ConvergenceStatus.FAILED,
                ConvergenceStatus.NOT_CONVERGED,
                ConvergenceStatus.EMPTY_OUTPUT,
                ConvergenceStatus.UNKNOWN)) {
            assertThatThrownBy(() -> build(status, EvidenceAcceptanceState.ACCEPTED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be ACCEPTED");
        }
    }

    @Test
    void failedEvidenceWithNonAcceptedStateIsAllowed() {
        QuantumEvidence evidence = build(
                ConvergenceStatus.FAILED, EvidenceAcceptanceState.FAILED_NUMERICALLY);
        assertThat(evidence.convergence()).isEqualTo(ConvergenceStatus.FAILED);
        assertThat(evidence.acceptance()).isEqualTo(EvidenceAcceptanceState.FAILED_NUMERICALLY);
    }

    @Test
    void convergedEvidenceCanBeAccepted() {
        QuantumEvidence evidence = build(ConvergenceStatus.CONVERGED, EvidenceAcceptanceState.ACCEPTED);
        assertThat(evidence.acceptance()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
    }

    @Test
    void listContentsInsideOptionalsAreCopiedDefensively() {
        List<Double> gradient = new ArrayList<>(List.of(0.1, 0.2, 0.3));
        QuantumEvidence evidence = new QuantumEvidence(
                EvidenceFixtures.identity(
                        CalculationType.SINGLE_POINT,
                        EvidenceFixtures.PBE_DEF2_SVP,
                        TslFixtures.geometryIdentityA()),
                EvidenceFixtures.provenance("/archive/tsl/sp.log"),
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.ACCEPTED,
                Optional.of(-400.5),
                Optional.of(gradient),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "");

        gradient.add(9.9);
        assertThat(evidence.gradientHartreePerBohr()).isPresent();
        assertThat(evidence.gradientHartreePerBohr().orElseThrow())
                .containsExactly(0.1, 0.2, 0.3);
        assertThatThrownBy(() -> evidence.gradientHartreePerBohr().orElseThrow().add(1.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
