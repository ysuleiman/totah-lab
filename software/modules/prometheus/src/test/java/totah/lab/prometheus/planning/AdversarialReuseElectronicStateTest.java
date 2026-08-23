package totah.lab.prometheus.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.comparability.EnergyTarget;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

/**
 * TEST_ID: B4 — DERIVE_FROM_EXISTING (and REUSE_EXISTING) may only draw from
 * evidence at the same electronic state. Evidence computed at
 * (charge, multiplicity) != the requirement's is different science
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, A7/A8 at the planner level).
 *
 * <p>Fixture: requirement (molecule TSL, charge 0, mult 1, protocol
 * PBE/def2-SVP, type HESSIAN, derivation rule from HESSIAN); the store holds
 * accepted converged evidence identical in every field EXCEPT charge +1 /
 * mult 2. The requirement must land in the missing-evidence plan.
 */
class AdversarialReuseElectronicStateTest {

    private static final List<String> CONSTRAINTS = List.of("freeze_dihedral=60");
    private static final List<String> OUTPUTS = List.of("energy", "hessian");
    private static final List<String> GATES = List.of("convergence=CONVERGED");

    private final StrategyEvidenceMatcher matcher = new StrategyEvidenceMatcher(
            new ProtocolComparability(),
            new HeuristicCostModel(Map.of(CalculationType.HESSIAN, 10.0), 0.01));

    /**
     * TEST_ID: B4 — a cation-doublet Hessian must neither satisfy nor be
     * derived into the neutral-singlet Hessian requirement.
     */
    @Test
    void differentElectronicStateIsNeitherReusedNorDerived() {
        QuantumEvidence cationDoublet = acceptedHessian(1, 2);
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(cationDoublet);

        MissingEvidencePlan plan = matcher.match(requirementSet(), bundle, Set.of());

        assertThat(plan.resolutions()).hasSize(1);
        StrategyRequirementResolution resolution = plan.resolutions().get(0);
        assertThat(resolution.decision())
                .as("evidence at (charge +1, mult 2) is different science for a "
                        + "(charge 0, mult 1) requirement")
                .isNotIn(EvidenceReuseDecision.REUSE_EXISTING,
                        EvidenceReuseDecision.DERIVE_FROM_EXISTING,
                        EvidenceReuseDecision.RESERVE_AS_HOLDOUT);
        assertThat(resolution.decision()).isEqualTo(EvidenceReuseDecision.GENERATE_NEW);
        assertThat(resolution.evidenceHashes()).isEmpty();

        // The requirement lands in the missing-evidence plan, at the
        // requirement's own electronic state.
        assertThat(plan.newCalculations()).hasSize(1);
        CalculationSpecification specification = plan.newCalculations().get(0);
        assertThat(specification.formalCharge()).isEqualTo(0);
        assertThat(specification.multiplicity()).isEqualTo(1);
        assertThat(specification.calculationType()).isEqualTo(CalculationType.HESSIAN);
    }

    /**
     * TEST_ID: B4 (control) — the same derivation from the SAME electronic
     * state is accepted, proving the discriminator is the electronic state and
     * not some incidental fixture difference.
     */
    @Test
    void sameElectronicStateDerivationIsAcceptedAsControl() {
        QuantumEvidence neutralSinglet = acceptedHessian(0, 1);
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(neutralSinglet);

        MissingEvidencePlan plan = matcher.match(requirementSet(), bundle, Set.of());

        assertThat(plan.resolutions().get(0).decision())
                .isIn(EvidenceReuseDecision.REUSE_EXISTING,
                        EvidenceReuseDecision.DERIVE_FROM_EXISTING);
        assertThat(plan.newCalculations()).isEmpty();
    }

    private static EvidenceRequirementSet requirementSet() {
        EvidenceRequirement requirement = new EvidenceRequirement(
                CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP,
                EnergyTarget.FORCE_CONSTANT,
                "bonded curvature",
                DatasetRole.DEVELOPMENT,
                true,
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA());
        ScientificEvidenceRequirement scientific = new ScientificEvidenceRequirement(
                requirement, 0, 1, CONSTRAINTS, OUTPUTS, GATES);
        StrategyEvidenceRequirement strategyRequirement = new StrategyEvidenceRequirement(
                "hessian",
                scientific,
                true,
                true,
                Optional.of(new DerivationRule(
                        CalculationType.HESSIAN, "force constants", "harmonic projection")),
                List.of("harmonic model"),
                List.of("bond and angle constants"),
                List.of(),
                List.of());
        return new EvidenceRequirementSet("b4-electronic-state", List.of(strategyRequirement));
    }

    /** Accepted, converged Hessian evidence at the given electronic state. */
    private static QuantumEvidence acceptedHessian(int formalCharge, int multiplicity) {
        EvidenceIdentity identity = new EvidenceIdentity(
                TslFixtures.TSL,
                TslFixtures.canonicalMap().canonicalHash(),
                TslFixtures.geometryIdentityA(),
                formalCharge,
                multiplicity,
                CalculationType.HESSIAN,
                EvidenceFixtures.PBE_DEF2_SVP,
                CONSTRAINTS,
                OUTPUTS);
        return EvidenceFixtures.acceptedQuantum(identity, -100.0);
    }
}
