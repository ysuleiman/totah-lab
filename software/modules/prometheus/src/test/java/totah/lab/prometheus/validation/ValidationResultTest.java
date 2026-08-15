package totah.lab.prometheus.validation;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.candidate.DecisionState;
import totah.lab.prometheus.candidate.ModelDecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationResultTest {

    private static final Instant T0 = ValidationTestData.T0;

    private static FrozenCandidate frozen() {
        return FrozenCandidate.freeze(
                ValidationTestData.candidate("cand-1", 91.0),
                ValidationTestData.plan("holdout-1"));
    }

    private static GateOutcome passedOutcome() {
        return new GateOutcome(ValidationTestData.rmseGate(), 0.8, true,
                "observed RMSE 0.8 <= 1.0 kcal/mol");
    }

    private static GateOutcome failedOutcome() {
        return new GateOutcome(ValidationTestData.rmseGate(), 2.3, false,
                "observed RMSE 2.3 > 1.0 kcal/mol");
    }

    private static ModelDecision decision(DecisionState state, String reason) {
        return new ModelDecision(state, List.of(reason), T0);
    }

    @Test
    void failingGateWithAcceptingDecisionThrows_neverPromoteWhilePreregisteredGatesFail() {
        assertThatThrownBy(() -> ValidationResult.of(
                frozen(),
                List.of(failedOutcome()),
                decision(DecisionState.VALIDATED_FOR_PRODUCTION, "RMSE improved over parent")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("never promote while preregistered gates fail");
    }

    @Test
    void allGatesPassWithFailureDecisionThrows() {
        assertThatThrownBy(() -> ValidationResult.of(
                frozen(),
                List.of(passedOutcome()),
                decision(DecisionState.FAILED_HOLDOUT, "contradictory decision")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an accepting state");
    }

    @Test
    void allGatesPassWithValidatedWithLimitationsIsAccepted() {
        FrozenCandidate frozen = frozen();

        ValidationResult result = ValidationResult.of(
                frozen,
                List.of(passedOutcome()),
                decision(DecisionState.VALIDATED_WITH_LIMITATIONS,
                        "validated for thiol angles; torsions out of scope"));

        assertThat(result.allPassed()).isTrue();
        assertThat(result.planChecksum()).isEqualTo(frozen.plan().planChecksum());
        assertThat(result.freezeChecksum()).isEqualTo(frozen.freezeChecksum());
        assertThat(result.outcomes()).containsExactly(passedOutcome());
        assertThat(result.executedAt()).isNotNull();
    }

    @Test
    void failingGateWithFailedHoldoutDecisionIsAccepted() {
        ValidationResult result = ValidationResult.of(
                frozen(),
                List.of(failedOutcome()),
                decision(DecisionState.FAILED_HOLDOUT,
                        "holdout RMSE 2.3 kcal/mol exceeds tolerance"));

        assertThat(result.allPassed()).isFalse();
        assertThat(result.decision().state()).isEqualTo(DecisionState.FAILED_HOLDOUT);
    }
}
