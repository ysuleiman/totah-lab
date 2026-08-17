package totah.lab.athena.tmt;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnsembleNacAnalyzerTest {
    @Test
    void noFramesRemainUnevaluated() {
        EnsembleNacSummary summary = new EnsembleNacAnalyzer().summarize("state", List.of());
        assertEquals(EnsembleEvidenceStatus.NO_FRAMES, summary.status());
        assertTrue(summary.meanNacFraction().isEmpty());
    }

    @Test
    void oneReplicaCannotProduceBetweenReplicaEvidence() {
        EnsembleNacSummary summary = new EnsembleNacAnalyzer().summarize("state", List.of(
                frame(1, 0, 0, false), frame(1, 1, 2, true)));
        assertEquals(EnsembleEvidenceStatus.INSUFFICIENT_REPLICAS, summary.status());
        assertTrue(summary.meanNacFraction().isEmpty());
    }

    @Test
    void reportsReplicaPopulationTransitionsAndVariabilitySeparately() {
        EnsembleNacSummary summary = new EnsembleNacAnalyzer().summarize("state", List.of(
                frame(1, 0, 0, false), frame(1, 1, 2, true), frame(1, 2, 4, true), frame(1, 3, 6, false),
                frame(2, 0, 0, true), frame(2, 1, 2, true), frame(2, 2, 4, true), frame(2, 3, 6, true)));

        assertEquals(EnsembleEvidenceStatus.EVALUATED, summary.status());
        assertEquals(0.75, summary.meanNacFraction().orElseThrow(), 1.0e-9);
        assertEquals(1, summary.replicas().getFirst().transitionsIntoNac());
        assertEquals(1, summary.replicas().getFirst().transitionsOutOfNac());
        assertTrue(summary.betweenReplicaStandardDeviation().orElseThrow() > 0.0);
    }

    private static EnsembleFrame frame(int replica, long index, double time, boolean nac) {
        NearAttackClassification classification = nac
                ? NearAttackClassification.GEOMETRICALLY_NEAR_PRODUCTIVE
                : NearAttackClassification.CLEARLY_NONPRODUCTIVE;
        NearAttackAssessment assessment = new NearAttackAssessment(
                classification, nac, true, false, false, "test", "test provenance");
        return new EnsembleFrame("state", replica, index, time, assessment,
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.empty(), true, true);
    }
}
