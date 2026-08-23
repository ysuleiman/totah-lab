package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial acceptance tests for electronic-state and requested-output
 * identity — A7, A8 and A9 of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md. Evidence
 * computed at a different (charge, multiplicity) or delivering different
 * outputs is different science: it must neither share an identity with the
 * requirement nor satisfy it by reuse, and the refusal must be loud.
 */
class AdversarialElectronicStateAcceptanceTest {

    private final EvidencePlanner planner = new EvidencePlanner(
            new ProtocolComparability(),
            new HeuristicCostModel(Map.of(CalculationType.SINGLE_POINT, 16.0), 0.02));

    private static CalculationSpecification spec(
            int formalCharge, int multiplicity, List<String> requiredOutputs) {
        return new CalculationSpecification(
                "adversarial-1",
                "adversarial electronic-state fixture",
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA(),
                formalCharge,
                multiplicity,
                EvidenceFixtures.PBE_DEF2_SVP,
                List.of(),
                CalculationType.SINGLE_POINT,
                requiredOutputs,
                List.of("convergence=CONVERGED", "acceptance=ACCEPTED"),
                DatasetRole.DEVELOPMENT,
                CostEstimate.zero());
    }

    private static ScientificEvidenceRequirement requirement(
            int formalCharge, int multiplicity, List<String> requestedOutputs) {
        return new ScientificEvidenceRequirement(
                new EvidenceRequirement(
                        CalculationType.SINGLE_POINT,
                        EvidenceFixtures.PBE_DEF2_SVP,
                        EnergyTarget.of(CalculationType.SINGLE_POINT),
                        "adversarial requirement",
                        DatasetRole.DEVELOPMENT,
                        true,
                        TslFixtures.TSL,
                        TslFixtures.geometryIdentityA()),
                formalCharge,
                multiplicity,
                List.of(),
                requestedOutputs,
                List.of("convergence=CONVERGED", "acceptance=ACCEPTED"));
    }

    /** Accepted, converged evidence at the given electronic state and outputs. */
    private static EvidenceBundle bundleWith(
            int formalCharge, int multiplicity, List<String> requestedOutputs) {
        EvidenceIdentity identity = new EvidenceIdentity(
                TslFixtures.TSL,
                TslFixtures.canonicalMap().canonicalHash(),
                TslFixtures.geometryIdentityA(),
                formalCharge,
                multiplicity,
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                List.of(),
                requestedOutputs);
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(EvidenceFixtures.acceptedQuantum(identity, -100.0));
        return bundle;
    }

    /**
     * TEST_ID: A7 — formal charge is scientific identity: specs differing only
     * in formalCharge (0 vs +1) hash differently; identical specs hash equally;
     * a cation's accepted evidence never satisfies the neutral requirement.
     */
    @Test
    void a7_formalChargeChangesIdentityAndBlocksReuse() {
        assertThat(spec(0, 1, List.of("energy")).checksum())
                .isEqualTo(spec(0, 1, List.of("energy")).checksum());
        assertThat(spec(1, 1, List.of("energy")).checksum())
                .isNotEqualTo(spec(0, 1, List.of("energy")).checksum());

        EvidenceGenerationPlan plan = planner.planScientific("a7",
                List.of(requirement(0, 1, List.of("energy"))),
                bundleWith(1, 1, List.of("energy")));

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        assertThat(resolution.reusableEvidenceHashes()).isEmpty();
        assertThat(resolution.reason()).isNotBlank();   // loud refusal, never a silent empty match
        assertThat(plan.newCalculations()).hasSize(1);
        assertThat(plan.newCalculations().get(0).formalCharge()).isEqualTo(0);
    }

    /**
     * TEST_ID: A8 — multiplicity is scientific identity: specs differing only
     * in multiplicity (1 vs 3) hash differently; triplet evidence never
     * satisfies the singlet requirement.
     */
    @Test
    void a8_multiplicityChangesIdentityAndBlocksReuse() {
        assertThat(spec(0, 3, List.of("energy")).checksum())
                .isNotEqualTo(spec(0, 1, List.of("energy")).checksum());
        assertThat(spec(0, 1, List.of("energy")).checksum())
                .isEqualTo(spec(0, 1, List.of("energy")).checksum());

        EvidenceGenerationPlan plan = planner.planScientific("a8",
                List.of(requirement(0, 1, List.of("energy"))),
                bundleWith(0, 3, List.of("energy")));

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        assertThat(resolution.reusableEvidenceHashes()).isEmpty();
        assertThat(resolution.reason()).isNotBlank();
        assertThat(plan.newCalculations()).hasSize(1);
        assertThat(plan.newCalculations().get(0).multiplicity()).isEqualTo(1);
    }

    /**
     * TEST_ID: A9 — evidence delivers the observables it was required to
     * deliver: requiredOutputs are part of the checksum, and energy+gradient
     * evidence never satisfies a requirement that also asks for the Hessian.
     */
    @Test
    void a9_gradientOnlyEvidenceDoesNotSatisfyHessianOutput() {
        assertThat(spec(0, 1, List.of("energy", "gradient", "hessian")).checksum())
                .isNotEqualTo(spec(0, 1, List.of("energy", "gradient")).checksum());
        assertThat(spec(0, 1, List.of("energy", "gradient")).checksum())
                .isEqualTo(spec(0, 1, List.of("energy", "gradient")).checksum());

        EvidenceGenerationPlan plan = planner.planScientific("a9",
                List.of(requirement(0, 1, List.of("energy", "gradient", "hessian"))),
                bundleWith(0, 1, List.of("energy", "gradient")));

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        assertThat(resolution.reason()).contains("requested outputs");
        assertThat(resolution.reusableEvidenceHashes()).isEmpty();
        assertThat(plan.newCalculations()).hasSize(1);
        assertThat(plan.newCalculations().get(0).requiredOutputs())
                .containsExactly("energy", "gradient", "hessian");
    }
}
