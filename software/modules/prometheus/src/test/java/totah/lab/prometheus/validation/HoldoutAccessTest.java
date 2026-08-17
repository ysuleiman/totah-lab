package totah.lab.prometheus.validation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.candidate.ParameterCandidate;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HoldoutAccessTest {

    private static QuantumEvidence holdoutEvidence() {
        return ValidationTestData.accepted(
                CalculationType.HESSIAN, TslFixtures.geometryIdentityA());
    }

    @Test
    void holdoutExposesNoHashAccessor() {
        // The public surface is intentionally limited to id/size/description/containsHash:
        // no declared method returns the hash set or any collection of hashes.
        Set<String> methodNames = methodNames(HoldoutDataset.class.getDeclaredMethods());
        assertThat(methodNames).doesNotContain("hashes", "evidenceHashes", "getHashes");

        assertThat(Arrays.stream(HoldoutDataset.class.getDeclaredMethods())
                .filter(m -> Set.class.isAssignableFrom(m.getReturnType())))
                .isEmpty();

        assertThatThrownBy(() -> HoldoutDataset.class.getMethod("hashes"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void revealToNullFrozenThrows() {
        QuantumEvidence hold = holdoutEvidence();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(hold);
        HoldoutDataset holdout = new HoldoutDataset(
                "holdout-1", new LinkedHashSet<>(List.of(hold.identity().evidenceHash())), "");

        assertThatThrownBy(() -> holdout.revealTo(null, bundle))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen candidate");
    }

    @Test
    void holdoutRevealedOnlyAfterFreeze() {
        QuantumEvidence hold = holdoutEvidence();
        QuantumEvidence nonAccepted = ValidationTestData.withStates(
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.GEOMETRY_INVALID,
                TslFixtures.geometryIdentityB());
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(hold);
        bundle.add(nonAccepted);

        HoldoutDataset holdout = new HoldoutDataset(
                "holdout-1",
                new LinkedHashSet<>(List.of(
                        hold.identity().evidenceHash(),
                        nonAccepted.identity().evidenceHash())),
                "");

        // Pre-freeze there is no API path to the holdout values; after freezing a
        // candidate against a preregistered plan, exactly the ACCEPTED holdout
        // evidence is revealed for validation (geometry-invalid members never are).
        ParameterCandidate candidate = ValidationTestData.candidate("cand-1", 91.0);
        FrozenCandidate frozen = FrozenCandidate.freeze(candidate, ValidationTestData.plan("holdout-1"));

        List<QuantumEvidence> revealed = holdout.revealTo(frozen, bundle);

        assertThat(revealed).containsExactly(hold);
    }

    @Test
    void frozenCandidateCannotRevealADifferentHoldout() {
        HoldoutDataset holdout = new HoldoutDataset("holdout-2", new LinkedHashSet<>(), "");
        FrozenCandidate frozen = FrozenCandidate.freeze(
                ValidationTestData.candidate("cand-1", 91.0), ValidationTestData.plan("holdout-1"));

        assertThatThrownBy(() -> holdout.revealTo(frozen, new EvidenceBundle()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holdout-1")
                .hasMessageContaining("holdout-2");
    }

    private static Set<String> methodNames(Method[] methods) {
        return Arrays.stream(methods)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
    }
}
