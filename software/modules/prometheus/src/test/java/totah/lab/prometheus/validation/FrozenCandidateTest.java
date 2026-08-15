package totah.lab.prometheus.validation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.candidate.ParameterCandidate;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenCandidateTest {

    @Test
    void freezeChecksumIsDeterministicForSameCandidateAndPlan() {
        ParameterCandidate candidate = ValidationTestData.candidate("cand-1", 91.0);
        ValidationPlan plan = ValidationTestData.plan("holdout-1");

        FrozenCandidate first = FrozenCandidate.freeze(candidate, plan);
        FrozenCandidate second = FrozenCandidate.freeze(candidate, plan);

        assertThat(first.freezeChecksum()).isEqualTo(second.freezeChecksum());
        assertThat(first.freezeChecksum()).hasSize(64);
        assertThat(first.candidate()).isSameAs(candidate);
        assertThat(first.plan()).isSameAs(plan);
        assertThat(first.frozenAt()).isNotNull();
    }

    @Test
    void differentParameterValueGivesDifferentChecksum() {
        ValidationPlan plan = ValidationTestData.plan("holdout-1");

        FrozenCandidate original = FrozenCandidate.freeze(
                ValidationTestData.candidate("cand-1", 91.0), plan);
        FrozenCandidate modified = FrozenCandidate.freeze(
                ValidationTestData.candidate("cand-1", 92.5), plan);

        assertThat(modified.freezeChecksum()).isNotEqualTo(original.freezeChecksum());
    }

    @Test
    void freezeRequiresPreregisteredPlan_unpreregisteredPlanCannotBeConstructed() {
        // ValidationPlan has no public/protected constructor: the only way to
        // obtain a plan is the preregister factory, so freezing without a
        // preregistered plan is structurally impossible.
        assertThat(Arrays.stream(ValidationPlan.class.getDeclaredConstructors())
                .map(Constructor::getModifiers)
                .allMatch(Modifier::isPrivate))
                .isTrue();

        ValidationPlan plan = ValidationPlan.preregister(
                "plan-tsl-1",
                java.util.List.of(ValidationTestData.rmseGate()),
                "holdout-1");
        assertThat(plan.preregistered()).isTrue();
        assertThat(plan.preregisteredAt()).isNotNull();
        assertThat(plan.planChecksum()).hasSize(64);
    }

    @Test
    void frozenCandidateHasNoRefitPath() {
        // A frozen candidate is terminal: no derivation, no refit, no mutation.
        // A new parameter idea starts a new development cycle with a new candidate.
        assertThat(Arrays.stream(FrozenCandidate.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("derive"))
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("refit"))
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("promote"))
                .noneMatch(name -> name.startsWith("set"));

        // No method hands out a fresh (mutable-child) candidate either: the only
        // candidate exposed is the frozen snapshot itself.
        assertThat(Arrays.stream(FrozenCandidate.class.getDeclaredMethods())
                .filter(m -> !m.getName().equals("candidate"))
                .filter(m -> ParameterCandidate.class.isAssignableFrom(m.getReturnType())))
                .isEmpty();
    }
}
