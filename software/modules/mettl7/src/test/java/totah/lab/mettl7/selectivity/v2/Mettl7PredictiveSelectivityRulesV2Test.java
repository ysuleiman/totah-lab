package totah.lab.mettl7.selectivity.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7PredictiveSelectivityRulesV2Test {
    private final Mettl7PredictiveSelectivityRulesV2 rules = new Mettl7PredictiveSelectivityRulesV2();

    @Test void calibratesOpposedNonproductiveAnchors() {
        var dcmb = rules.classify(new MatchedFunctionalEvidence("DCMB", true, false,
                false, true, false, false, false, "manuscript"));
        var netarsudil = rules.classify(new MatchedFunctionalEvidence("Netarsudil", false, true,
                true, false, false, false, false, "Rheem lab 2026-09-04"));
        assertThat(dcmb.selectivity()).isEqualTo(SelectivityClass.A_SELECTIVE);
        assertThat(netarsudil.selectivity()).isEqualTo(SelectivityClass.B_SELECTIVE);
        assertThat(dcmb.productivity()).isEqualTo(ProductivityClass.NONPRODUCTIVE_BINDER);
        assertThat(netarsudil.productivity()).isEqualTo(ProductivityClass.NONPRODUCTIVE_BINDER);
    }

    @Test void keepsSharedProductivitySeparateFromSelectivity() {
        var tsl = rules.classify(new MatchedFunctionalEvidence("TSL", false, false,
                false, false, true, true, false, "manuscript"));
        assertThat(tsl.selectivity()).isEqualTo(SelectivityClass.SHARED);
        assertThat(tsl.productivity()).isEqualTo(ProductivityClass.PRODUCTIVE_SUBSTRATE);
    }

    @Test void doesNotPromoteIncompleteEvidence() {
        var prospective = rules.classify(new MatchedFunctionalEvidence("analog", false, false,
                false, false, false, false, false, "prediction"));
        assertThat(prospective.selectivity()).isEqualTo(SelectivityClass.INDETERMINATE);
        assertThat(prospective.productivity()).isEqualTo(ProductivityClass.INDETERMINATE);
    }
}
