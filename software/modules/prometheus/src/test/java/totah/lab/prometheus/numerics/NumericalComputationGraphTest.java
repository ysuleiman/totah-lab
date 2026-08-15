package totah.lab.prometheus.numerics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class NumericalComputationGraphTest {

    @Test
    void mandatorySharedIntermediateIsEvaluatedOncePerState() {
        AtomicInteger calls = new AtomicInteger();
        NumericalNode<Scalar> shared = new NumericalNode<>("shared", List.of(), ReusePolicy.MANDATORY_REUSE,
                100, 8, ignored -> { calls.incrementAndGet(); return new Scalar(2); });
        NumericalNode<Scalar> left = derived("left", "shared");
        NumericalNode<Scalar> right = derived("right", "shared");
        NumericalComputationGraph graph = new NumericalComputationGraph(List.of(shared, left, right));

        var result = graph.evaluate(Set.of("left", "right"), new NumericalStateIdentity("a".repeat(64)),
                1024, Map.of("shared", 1), IntermediateRetentionPolicy.costAware());

        assertThat(calls).hasValue(1);
        assertThat(result.evaluationCounts()).containsEntry("shared", 1);
    }

    @Test
    void cheapLargeIntermediateMayBeRecomputedInsteadOfRetained() {
        AtomicInteger calls = new AtomicInteger();
        NumericalNode<Scalar> shared = new NumericalNode<>("eri", List.of(), ReusePolicy.RECOMPUTE_IF_CHEAPER,
                1, 10_000, ignored -> { calls.incrementAndGet(); return new Scalar(2); });
        NumericalComputationGraph graph = new NumericalComputationGraph(
                List.of(shared, derived("left", "eri"), derived("right", "eri")));

        var result = graph.evaluate(Set.of("left", "right"), new NumericalStateIdentity("b".repeat(64)),
                100, Map.of("eri", 1), IntermediateRetentionPolicy.costAware());

        assertThat(calls).hasValue(2);
        assertThat(result.evaluationCounts()).containsEntry("eri", 2);
        assertThat(result.retainedBytes()).isEqualTo(16); // only the two target scalars
    }

    @Test
    void cacheIfBeneficialHonorsMemoryBudget() {
        AtomicInteger calls = new AtomicInteger();
        NumericalNode<Scalar> shared = new NumericalNode<>("grid", List.of(), ReusePolicy.CACHE_IF_BENEFICIAL,
                50, 80, ignored -> { calls.incrementAndGet(); return new Scalar(2); });
        NumericalComputationGraph graph = new NumericalComputationGraph(
                List.of(shared, derived("left", "grid"), derived("right", "grid")));

        graph.evaluate(Set.of("left", "right"), new NumericalStateIdentity("c".repeat(64)),
                100, Map.of("grid", 1), IntermediateRetentionPolicy.costAware());

        assertThat(calls).hasValue(1);
    }

    private static NumericalNode<Scalar> derived(String id, String dependency) {
        return new NumericalNode<>(id, List.of(dependency), ReusePolicy.MANDATORY_REUSE, 1, 8,
                values -> new Scalar(values.require(dependency, Scalar.class).value() + 1));
    }

    private record Scalar(double value) implements IntermediateValue { }
}
