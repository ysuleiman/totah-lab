package totah.lab.prometheus.candidate;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

class ParameterCandidateTest {

    private static final Instant T0 = Instant.parse("2026-08-14T00:00:00Z");

    private static DerivedParameter angleParam() {
        return new DerivedParameter(
                "tsl-angle-9-10-26",
                TslFixtures.TSL,
                List.of(9, 10, 26),
                ParameterKind.ANGLE_BEND,
                "harmonic",
                91.0,
                "kcal/mol/rad^2",
                new ParameterProvenance(
                        "modified-Seminario",
                        List.of("abc123"),
                        "dev-1",
                        "prometheus-0.1",
                        "none",
                        "line-1",
                        ValidationStatus.UNVALIDATED));
    }

    private static ParameterCandidate root(EvidenceClass evidenceClass) {
        return new ParameterCandidate(
                "cand-root",
                TslFixtures.TSL,
                TslFixtures.forceFieldMapGaff2(),
                List.of(angleParam()),
                null,
                0,
                evidenceClass,
                T0);
    }

    @Test
    void deriveChildIncrementsGenerationAndLinksParent() {
        ParameterCandidate parent = root(EvidenceClass.EVIDENCE);

        ParameterCandidate child = parent.deriveChild("cand-child", List.of(angleParam()));

        assertThat(child.candidateId()).isEqualTo("cand-child");
        assertThat(child.parentCandidateId()).isEqualTo("cand-root");
        assertThat(child.generation()).isEqualTo(1);
        assertThat(child.evidenceClass()).isEqualTo(EvidenceClass.EVIDENCE);
        assertThat(child.molecule()).isEqualTo(parent.molecule());

        ParameterCandidate grandchild = child.deriveChild("cand-grandchild", List.of(angleParam()));
        assertThat(grandchild.parentCandidateId()).isEqualTo("cand-child");
        assertThat(grandchild.generation()).isEqualTo(2);
    }

    @Test
    void candidateCannotBeConstructedAsProductionModel() {
        assertThatThrownBy(() -> root(EvidenceClass.PRODUCTION_MODEL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRODUCTION_MODEL");
    }

    @Test
    void failedCandidateCannotBePromoted() {
        ParameterCandidate failed = root(EvidenceClass.FAILED_CANDIDATE);
        ModelDecision accepting = new ModelDecision(
                DecisionState.VALIDATED_FOR_PRODUCTION,
                List.of("passed holdout"),
                T0);

        assertThatThrownBy(() -> failed.promoteToProduction(accepting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed candidate never promoted");
    }

    @Test
    void failedHoldoutDecisionCannotPromote() {
        ParameterCandidate candidate = root(EvidenceClass.EVIDENCE);
        ModelDecision rejected = new ModelDecision(
                DecisionState.FAILED_HOLDOUT,
                List.of("holdout RMSE 3.2 kcal/mol exceeds tolerance"),
                T0);

        assertThatThrownBy(() -> candidate.promoteToProduction(rejected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FAILED_HOLDOUT");
    }

    @Test
    void validatedWithLimitationsDecisionPromotesAndCarriesProvenance() {
        ParameterCandidate candidate = root(EvidenceClass.VALIDATED_DIAGNOSTIC);
        ModelDecision accepting = new ModelDecision(
                DecisionState.VALIDATED_WITH_LIMITATIONS,
                List.of("validated for thiol angles; torsions out of scope"),
                T0);

        ParameterCandidate production = candidate.promoteToProduction(accepting);

        assertThat(production.evidenceClass()).isEqualTo(EvidenceClass.PRODUCTION_MODEL);
        assertThat(production.candidateId()).isEqualTo(candidate.candidateId());
        assertThat(production.parameters()).isEqualTo(candidate.parameters());
        assertThat(production.parameters().getFirst().provenance().derivationMethod())
                .isEqualTo("modified-Seminario");
        assertThat(production.parameters().getFirst().provenance().sourceEvidenceHashes())
                .containsExactly("abc123");
    }
}
