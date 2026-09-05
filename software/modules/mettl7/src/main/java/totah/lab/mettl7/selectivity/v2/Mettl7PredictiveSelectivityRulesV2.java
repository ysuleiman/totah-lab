package totah.lab.mettl7.selectivity.v2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Frozen V2 opposed-anchor rules. This classifier does not calculate affinity or merge
 * chemistry, geometry, contacts, and productivity into a master score.
 */
public final class Mettl7PredictiveSelectivityRulesV2 {
    public static final String VERSION = "METTL7_PREDICTIVE_SELECTIVITY_RULESET_V2";

    public SelectivityAssessment classify(MatchedFunctionalEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        List<String> reasons = new ArrayList<>();
        SelectivityClass selectivity = selectivity(evidence, reasons);
        ProductivityClass productivity = productivity(evidence, reasons);
        return new SelectivityAssessment(evidence.identifier(), VERSION, selectivity,
                productivity, reasons, evidence.provenance());
    }

    private static SelectivityClass selectivity(MatchedFunctionalEvidence e, List<String> reasons) {
        if (e.mettl7aEffect() && e.mettl7bRetained() && !e.mettl7bEffect()) {
            reasons.add("matched functional evidence shows an A effect with B retained");
            return SelectivityClass.A_SELECTIVE;
        }
        if (e.mettl7bEffect() && e.mettl7aRetained() && !e.mettl7aEffect()) {
            reasons.add("matched functional evidence shows a B effect with A retained");
            return SelectivityClass.B_SELECTIVE;
        }
        if ((e.productiveInA() && e.productiveInB())
                || (e.mettl7aEffect() && e.mettl7bEffect())) {
            reasons.add("matched evidence supports both paralogs without a directional difference");
            return SelectivityClass.SHARED;
        }
        if (!e.mettl7aEffect() && !e.mettl7bEffect() && !e.productiveInA()
                && !e.productiveInB() && e.directBindingEstablished()) {
            reasons.add("matched evidence establishes no relevant functional response in either paralog");
            return SelectivityClass.NONSELECTIVE_NONBINDER;
        }
        reasons.add("matched directional evidence is incomplete or unresolved");
        return SelectivityClass.INDETERMINATE;
    }

    private static ProductivityClass productivity(MatchedFunctionalEvidence e, List<String> reasons) {
        if (e.productiveInA() || e.productiveInB()) {
            reasons.add("a methylated product is established in at least one paralog");
            return ProductivityClass.PRODUCTIVE_SUBSTRATE;
        }
        if (e.mettl7aEffect() || e.mettl7bEffect() || e.directBindingEstablished()) {
            reasons.add("binding/function exists without an established methylated product");
            return ProductivityClass.NONPRODUCTIVE_BINDER;
        }
        reasons.add("productivity is unresolved");
        return ProductivityClass.INDETERMINATE;
    }
}
