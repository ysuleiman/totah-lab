package totah.lab.athena.ligand.screening;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreeningGatesTest {

    @Test
    void canonicalGateReportsIndependentGeometryFailures() {
        CanonicalPocketGate.Result result = new CanonicalPocketGate().evaluate(
                new CanonicalPocketGate.Evidence(false, true, true, true));

        assertThat(result.reasons()).hasSize(4);
    }

    @Test
    void isolatedPoseFailsDedicatedReproducibilityGate() {
        PoseReproducibilityGate.Result result = new PoseReproducibilityGate().evaluate(
                new PoseReproducibilityGate.Evidence(3, 1, false));

        assertThat(result.accepted()).isFalse();
        assertThat(result.reasons()).containsExactly(
                "canonical orientation is an isolated stochastic pose");
    }

    @Test
    void ruleOfFiveAndExplicitRuleOfThreeRemainAdvisoryEvidence() {
        DrugLikenessAssessment.Result result = new DrugLikenessAssessment().assess(
                new PhysicochemicalGate.Descriptors(
                        320.0, 0, 1, 4, 3, 65.0, 3.2, 2, 24, 0.3),
                DrugLikenessAssessment.Pool.COMPACT_FRAGMENT);

        assertThat(result.lipinski().violations()).isEmpty();
        assertThat(result.fragmentRuleOf3().applicable()).isTrue();
        assertThat(result.fragmentRuleOf3().passes()).isFalse();
        assertThat(result.fragmentRuleOf3().violations())
                .extracting(DrugLikenessAssessment.Violation::property)
                .containsExactly("molecularWeight", "clogP", "hBondAcceptors", "tpsa");
    }

    @Test
    void unresolvedTmt1aIsOnlyProvisionalEvidence() {
        TslInterferenceClassifier.Comparison result =
                new TslInterferenceClassifier().compare(
                        new TslInterferenceClassifier.Evidence(3, 2, true),
                        new TslInterferenceClassifier.Evidence(0, 0, false));

        assertThat(result.stronglySupportsTmt1bSelectivity()).isFalse();
        assertThat(result.provisionallySupportsTmt1bSelectivity()).isTrue();
    }
}
