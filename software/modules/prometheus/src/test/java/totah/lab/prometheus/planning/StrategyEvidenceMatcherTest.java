package totah.lab.prometheus.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.diagnosis.DiagnosisReport;
import totah.lab.prometheus.diagnosis.FunctionalFormClassification;
import totah.lab.prometheus.diagnosis.FunctionalFormDiagnostic;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

class StrategyEvidenceMatcherTest {
    private final StrategyEvidenceMatcher matcher = new StrategyEvidenceMatcher(
            new ProtocolComparability(),
            new HeuristicCostModel(Map.of(CalculationType.HESSIAN, 64.0,
                    CalculationType.SINGLE_POINT, 10.0), 0.02));

    @Test
    void compatibleExistingHessianReturnsReuseExisting() {
        EvidenceBundle bundle = withAccepted(CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP);
        MissingEvidencePlan plan = match(requirement("hessian", CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of()), bundle);
        assertThat(plan.resolutions().get(0).decision()).isEqualTo(EvidenceReuseDecision.REUSE_EXISTING);
        assertThat(plan.newCalculations()).isEmpty();
    }

    @Test
    void incompatibleMethodHessianDoesNotReuse() {
        EvidenceBundle bundle = withAccepted(CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP);
        MissingEvidencePlan plan = match(requirement("hessian", CalculationType.HESSIAN,
                EvidenceFixtures.PBE0_DEF2_TZVP, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of()), bundle);
        assertThat(plan.resolutions().get(0).decision())
                .isEqualTo(EvidenceReuseDecision.INCOMPATIBLE_EXISTING);
    }

    @Test
    void derivableValueNeverCreatesCalculation() {
        EvidenceBundle bundle = withAccepted(CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP);
        StrategyEvidenceRequirement derived = requirement("seminario", CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT,
                Optional.of(new DerivationRule(CalculationType.HESSIAN,
                        "bond and angle force constants", "Modified Seminario")), List.of(), List.of());
        MissingEvidencePlan plan = match(derived, bundle);
        assertThat(plan.resolutions().get(0).decision())
                .isEqualTo(EvidenceReuseDecision.DERIVE_FROM_EXISTING);
        assertThat(plan.newCalculations()).isEmpty();
    }

    @Test
    void unavailableSoftwareIsInfrastructureBlockedNotScientificInvalid() {
        StrategyEvidenceRequirement needed = requirement("density", CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT, Optional.empty(),
                List.of("Chargemol"), List.of());
        MissingEvidencePlan plan = match(needed, new EvidenceBundle());
        assertThat(plan.resolutions().get(0).decision())
                .isEqualTo(EvidenceReuseDecision.BLOCKED_BY_INFRASTRUCTURE);
        assertThat(plan.resolutions().get(0).scientificReason()).contains("feasible");
    }

    @Test
    void functionalFormIncompatibilityDowngradesStrategy() {
        MissingEvidencePlan plan = holdoutOnlyPlan();
        StrategyScientificScope harmonicOnly = new StrategyScientificScope(
                Set.of(FunctionalFormRequirement.HARMONIC_ANGLES), true, false, true, true);
        DiagnosisReport diagnosis = diagnosis(FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT);
        StrategyRecommendationResult result = new StrategyAssessor().assess(harmonicOnly, plan, diagnosis);
        assertThat(result.recommendation()).isEqualTo(StrategyRecommendation.FUNCTIONAL_FORM_INCOMPATIBLE);
    }

    @Test
    void sameEvidenceCannotBeDevelopmentAndHoldout() {
        EvidenceBundle bundle = withAccepted(CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP);
        StrategyEvidenceRequirement development = requirement("dev", CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of());
        StrategyEvidenceRequirement holdout = requirement("hold", CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.HOLDOUT, Optional.empty(), List.of(), List.of());
        EvidenceRequirementSet set = new EvidenceRequirementSet("strategy", List.of(development, holdout));
        assertThatIllegalArgumentException().isThrownBy(() -> matcher.match(set, bundle, Set.of()))
                .withMessageContaining("holdout evidence");
    }

    @Test
    void duplicateRequirementsProduceExactlyOneCalculation() {
        StrategyEvidenceRequirement one = requirement("one", CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of());
        StrategyEvidenceRequirement duplicate = requirement("two", CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of());
        EvidenceRequirementSet set = EvidenceRequirementSet.deduplicated("strategy", List.of(one, duplicate));
        MissingEvidencePlan plan = matcher.match(set, new EvidenceBundle(), Set.of());
        assertThat(plan.resolutions()).hasSize(1);
        assertThat(plan.newCalculations()).hasSize(1);
    }

    @Test
    void missingRequirementProducesOneSpecificationAndCostFromDeduplicatedPlan() {
        MissingEvidencePlan plan = match(requirement("missing", CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of()),
                new EvidenceBundle());
        assertThat(plan.newCalculations()).hasSize(1);
        StrategyCostEstimate cost = StrategyCostEstimate.from(plan);
        assertThat(cost.newQmJobs()).isEqualTo(1);
        assertThat(cost.total().jobCount()).isEqualTo(1);
    }

    @Test
    void nonbondedCapabilityRequiresAParameterizedNonbondedTerm() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StrategyScientificScope(
                Set.of(FunctionalFormRequirement.HARMONIC_ANGLES), false, true, true, true))
                .withMessageContaining("nonbonded");
    }

    @Test
    void unresolvedMetadataBlocksReuseOnlyWhenDeclaredScientificallyRequired() {
        QmProtocol unknownVersion = new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false,
                "PySCF", "unknown");
        EvidenceBundle bundle = withAccepted(CalculationType.HESSIAN, unknownVersion);
        StrategyEvidenceRequirement permissive = requirement("permissive", CalculationType.HESSIAN,
                unknownVersion, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of());
        assertThat(match(permissive, bundle).resolutions().get(0).decision())
                .isEqualTo(EvidenceReuseDecision.REUSE_EXISTING);
        StrategyEvidenceRequirement strict = requirement("strict", CalculationType.HESSIAN,
                unknownVersion, DatasetRole.DEVELOPMENT, Optional.empty(), List.of(), List.of("softwareVersion"));
        assertThat(match(strict, bundle).resolutions().get(0).decision())
                .isEqualTo(EvidenceReuseDecision.INSUFFICIENT_METADATA);
    }

    private MissingEvidencePlan holdoutOnlyPlan() {
        EvidenceBundle bundle = withAccepted(CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP);
        return match(requirement("holdout", CalculationType.HESSIAN, EvidenceFixtures.PBE_DEF2_SVP,
                DatasetRole.HOLDOUT, Optional.empty(), List.of(), List.of()), bundle);
    }

    private MissingEvidencePlan match(StrategyEvidenceRequirement requirement, EvidenceBundle bundle) {
        return matcher.match(new EvidenceRequirementSet("strategy", List.of(requirement)), bundle, Set.of());
    }

    private static StrategyEvidenceRequirement requirement(String id, CalculationType type, QmProtocol protocol,
            DatasetRole role, Optional<DerivationRule> derivation, List<String> dependencies,
            List<String> requiredMetadata) {
        EvidenceRequirement base = new EvidenceRequirement(type, protocol, EnergyTarget.of(type), "scientific target",
                role, true, TslFixtures.TSL, TslFixtures.geometryIdentityA());
        return new StrategyEvidenceRequirement(id, ScientificEvidenceRequirement.neutralSinglet(base), true, true,
                derivation, List.of(), List.of("parameters"), dependencies, requiredMetadata);
    }

    private static EvidenceBundle withAccepted(CalculationType type, QmProtocol protocol) {
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(EvidenceFixtures.acceptedQuantum(
                EvidenceFixtures.identity(type, protocol, TslFixtures.geometryIdentityA()), -100.0));
        return bundle;
    }

    private static DiagnosisReport diagnosis(FunctionalFormClassification classification) {
        return new DiagnosisReport(TslFixtures.TSL,
                List.of(new FunctionalFormDiagnostic(classification, List.of("verified diagnosis"),
                        List.of("evidence-hash"), "test-1")), Instant.EPOCH);
    }
}
