package totah.lab.prometheus.planning;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Planner behavior on the TSL fixture: reuse of equivalent evidence, protocol
 * incompatibility, regeneration after failure, and cost estimation before any
 * new QM is launched.
 */
class EvidencePlannerTest {

    private final EvidencePlanner planner = new EvidencePlanner(
            new ProtocolComparability(),
            new HeuristicCostModel(Map.of(
                    CalculationType.HESSIAN, 64.0,
                    CalculationType.SINGLE_POINT, 16.0),
                    0.02));

    private static EvidenceRequirement requirement(
            CalculationType type,
            totah.lab.prometheus.evidence.QmProtocol protocol,
            boolean required,
            totah.lab.prometheus.identity.GeometryIdentity geometry) {

        return new EvidenceRequirement(
                type,
                protocol,
                EnergyTarget.of(type),
                "test requirement",
                DatasetRole.DEVELOPMENT,
                required,
                TslFixtures.TSL,
                geometry);
    }

    @Test
    void acceptedConvergedHessianIsReusedAndNeverRecalculated() {
        EvidenceIdentity identity = EvidenceFixtures.identity(
                CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        QuantumEvidence hessian = EvidenceFixtures.acceptedQuantum(identity, -100.0);
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(hessian);

        EvidenceRequirement requirement = requirement(
                CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP,
                true,
                TslFixtures.geometryIdentityA());

        EvidenceGenerationPlan plan = planner.plan("plan-hessian", List.of(requirement), bundle);

        assertThat(plan.resolutions()).hasSize(1);
        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.REUSE_EXISTING);
        assertThat(resolution.reusableEvidenceHashes()).containsExactly(identity.evidenceHash());
        assertThat(plan.newCalculations()).isEmpty();
        assertThat(plan.totalCost()).isEqualTo(CostEstimate.zero());
    }

    @Test
    void sameGeometryUnderDifferentMethodIsIncompatibleExisting() {
        EvidenceIdentity identity = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(EvidenceFixtures.acceptedQuantum(identity, -100.0));

        EvidenceRequirement requirement = requirement(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                true,
                TslFixtures.geometryIdentityA());

        EvidenceGenerationPlan plan = planner.plan("plan-method", List.of(requirement), bundle);

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.INCOMPATIBLE_EXISTING);
        assertThat(resolution.reason()).contains("SAME_GEOMETRY_DIFFERENT_METHOD");
        assertThat(resolution.reusableEvidenceHashes()).isEmpty();
        assertThat(plan.newCalculations()).isEmpty();
    }

    @Test
    void missingEvidenceGeneratesNewSpecificationWithGatesAndCost() {
        EvidenceBundle bundle = new EvidenceBundle();

        EvidenceRequirement requirement = requirement(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                true,
                TslFixtures.geometryIdentityA());

        EvidenceGenerationPlan plan = planner.plan("plan-new", List.of(requirement), bundle);

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        assertThat(plan.newCalculations()).hasSize(1);
        CalculationSpecification spec = plan.newCalculations().get(0);
        assertThat(spec.specificationId()).isEqualTo("plan-new-1");
        assertThat(spec.acceptanceGates()).isNotEmpty();
        assertThat(spec.estimatedCost()).isNotNull();
        // the cost plan exists before any new QM is launched
        assertThat(plan.totalCost().estimatedRemoteCostUsd()).isGreaterThan(0.0);
        assertThat(plan.totalCost().jobCount()).isEqualTo(1);
    }

    @Test
    void failedQmIsNotReusableButDoesNotBlockRegeneration() {
        EvidenceIdentity identity = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        QuantumEvidence failed = new QuantumEvidence(
                identity,
                EvidenceFixtures.provenance("/archive/tsl/failed.log"),
                ConvergenceStatus.FAILED,
                EvidenceAcceptanceState.FAILED_NUMERICALLY,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "SCF failed to converge");
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(failed);

        EvidenceRequirement requirement = requirement(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                true,
                TslFixtures.geometryIdentityA());

        EvidenceGenerationPlan plan = planner.plan("plan-failed", List.of(requirement), bundle);

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        assertThat(resolution.reason()).contains("failed");
        assertThat(resolution.reason()).contains(identity.evidenceHash());
        assertThat(resolution.reusableEvidenceHashes()).isEmpty();
        assertThat(plan.newCalculations()).hasSize(1);
    }

    @Test
    void requiredRequirementWithoutGeometryIsBlocked() {
        EvidenceRequirement requirement = requirement(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                true,
                null);

        EvidenceGenerationPlan plan =
                planner.plan("plan-blocked", List.of(requirement), new EvidenceBundle());

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.BLOCKED);
        assertThat(resolution.reason()).contains("geometry");
        assertThat(plan.newCalculations()).isEmpty();
    }

    @Test
    void notRequiredRequirementIsNotPlanned() {
        EvidenceRequirement requirement = requirement(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                false,
                null);

        EvidenceGenerationPlan plan =
                planner.plan("plan-optional", List.of(requirement), new EvidenceBundle());

        RequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision()).isEqualTo(PlanDecision.NOT_REQUIRED);
        assertThat(plan.newCalculations()).isEmpty();
        assertThat(plan.totalCost()).isEqualTo(CostEstimate.zero());
    }

    @Test
    void explicitAnionicStateDoesNotReuseNeutralEvidence() {
        EvidenceIdentity neutralIdentity = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(EvidenceFixtures.acceptedQuantum(neutralIdentity, -100.0));
        EvidenceRequirement base = requirement(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                true,
                TslFixtures.geometryIdentityA());
        ScientificEvidenceRequirement anion = new ScientificEvidenceRequirement(
                base,
                -1,
                1,
                List.of("dihedral=60"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));

        EvidenceGenerationPlan plan = planner.planScientific("anion", List.of(anion), bundle);

        assertThat(plan.resolutions().get(0).decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        CalculationSpecification specification = plan.newCalculations().get(0);
        assertThat(specification.formalCharge()).isEqualTo(-1);
        assertThat(specification.multiplicity()).isEqualTo(1);
        assertThat(specification.constraints()).containsExactly("dihedral=60");
        assertThat(specification.requiredOutputs()).containsExactly("energy", "gradient");
        assertThat(specification.acceptanceGates()).contains("gradient_norm<=1e-5");
    }

    @Test
    void sameProtocolWithDifferentRequestedOutputsIsNotReused() {
        EvidenceIdentity energyOnly = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(EvidenceFixtures.acceptedQuantum(energyOnly, -100.0));
        ScientificEvidenceRequirement energyAndGradient = new ScientificEvidenceRequirement(
                requirement(CalculationType.SINGLE_POINT, EvidenceFixtures.PBE_DEF2_SVP,
                        true, TslFixtures.geometryIdentityA()),
                0,
                1,
                List.of(),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED"));

        EvidenceGenerationPlan plan = planner.planScientific(
                "gradient", List.of(energyAndGradient), bundle);

        assertThat(plan.resolutions().get(0).decision()).isEqualTo(PlanDecision.GENERATE_NEW);
        assertThat(plan.resolutions().get(0).reason()).contains("different constraints or requested outputs");
    }
}
