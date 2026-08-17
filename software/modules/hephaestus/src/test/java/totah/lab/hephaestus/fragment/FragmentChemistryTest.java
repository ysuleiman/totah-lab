package totah.lab.hephaestus.fragment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FragmentChemistryTest {
    @Test
    void assessesRuleOfThreeWithoutCombiningItWithValidity() {
        var descriptors = new FragmentDescriptors(7, 121.1, 1.2, 1, 2, 1, 0, 6);
        var assessment = RuleOfThreeAssessment.assess(descriptors);
        var chemistry = new FragmentChemistry("F001", "c1ccncc1", true, Optional.empty(), descriptors,
                assessment, List.of(new FragmentAttachmentHandle(2, "C", 1, true,
                        Set.of(FragmentGrowthMode.DIRECT_SUBSTITUTION), "one replaceable hydrogen")));

        assertTrue(chemistry.chemicallyValid());
        assertTrue(chemistry.ruleOfThree().satisfies());
        assertEquals(2, chemistry.attachmentHandles().getFirst().atomIndex());
    }

    @Test
    void reportsEachFailedRuleOfThreeCriterion() {
        var result = RuleOfThreeAssessment.assess(new FragmentDescriptors(20, 320, 4, 4, 5, 6, 0, 0));
        assertFalse(result.satisfies());
        assertEquals(5, result.failedCriteria().size());
    }
}
