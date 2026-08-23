package totah.lab.prometheus.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

/**
 * TEST_ID: B3 (specification level) — scientific identity is a function of
 * scientific content, not of Java collection iteration/encounter order.
 *
 * <p>Invariant (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, B3, with the
 * 2026-08-23 identity-finding correction): two requests with the same
 * constraints, required outputs and acceptance gates listed in different
 * orders are the SAME request and must hash identically; two requests
 * differing in any of them are DIFFERENT and must hash differently. Both
 * directions are asserted. The test encodes the invariant as specified and
 * records the implementation's actual behavior rather than adapting the
 * invariant to it.
 */
class AdversarialIdentityCanonicalizationTest {

    /**
     * TEST_ID: B3 (equality direction) — same elements, different order:
     * constraints [c1,c2] vs [c2,c1], requiredOutputs [o1,o2] vs [o2,o1],
     * gates [g1,g2] vs [g2,g1]. These are identical scientific requests and
     * MUST produce identical checksums.
     */
    @Test
    void reorderedSetLikeFieldsProduceIdenticalChecksum() {
        CalculationSpecification x = specification(
                "spec-x",
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));
        CalculationSpecification y = specification(
                "spec-y",
                List.of("freeze_bond=1-2", "freeze_dihedral=60"),
                List.of("gradient", "energy"),
                List.of("gradient_norm<=1e-5", "convergence=CONVERGED"));

        // specificationId is excluded from the checksum by contract, so the
        // only remaining difference between x and y is list order.
        assertThat(x.checksum())
                .as("identical science in different list order is the same request")
                .isEqualTo(y.checksum());
    }

    /**
     * TEST_ID: B3 (difference direction) — removing an acceptance gate or
     * changing a required output is different science and MUST change the
     * checksum.
     */
    @Test
    void removingAGateOrChangingAnOutputChangesChecksum() {
        CalculationSpecification x = specification(
                "spec-x",
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));
        CalculationSpecification gateRemoved = specification(
                "spec-z",
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED"));
        CalculationSpecification outputChanged = specification(
                "spec-w",
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "hessian"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));

        assertThat(x.checksum())
                .as("dropping an acceptance gate is different science")
                .isNotEqualTo(gateRemoved.checksum());
        assertThat(x.checksum())
                .as("changing a required output is different science")
                .isNotEqualTo(outputChanged.checksum());
    }

    private static CalculationSpecification specification(
            String id,
            List<String> constraints,
            List<String> requiredOutputs,
            List<String> acceptanceGates) {
        return new CalculationSpecification(
                id,
                "adversarial identity probe",
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA(),
                0,
                1,
                EvidenceFixtures.PBE_DEF2_SVP,
                constraints,
                CalculationType.SINGLE_POINT,
                requiredOutputs,
                acceptanceGates,
                DatasetRole.DEVELOPMENT,
                CostEstimate.zero());
    }
}
