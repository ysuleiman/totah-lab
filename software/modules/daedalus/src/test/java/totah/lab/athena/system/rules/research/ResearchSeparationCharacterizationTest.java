package totah.lab.athena.system.rules.research;

import org.junit.jupiter.api.Test;
import totah.lab.athena.system.rules.RuleManifest;
import static org.junit.jupiter.api.Assertions.*;

/** Permanent /1 behavior; does not qualify the newly approved policy. */
class ResearchSeparationCharacterizationTest {
    @Test void implementationOnlyVersionChangeChangesHistoricalScientificDigest()throws Exception {
        var f=ResearchGateAcceptanceTest.fixture("valid");
        var changed=ResearchCodec.JSON.valueToTree(f.manifest());
        ((com.fasterxml.jackson.databind.node.ObjectNode)changed).put("implementationVersion","implementation-only-test");
        var m=ResearchCodec.JSON.treeToValue(changed,RuleManifest.class);
        assertNotEquals(ResearchCodec.definition(f.manifest()),ResearchCodec.definition(m));
    }
    @Test void historicalPolicyRequiresImplementationInvalidation()throws Exception {
        var f=ResearchGateAcceptanceTest.fixture("valid");
        assertTrue(f.policy().invalidatingChangeKinds().contains("implementation version"));
        assertTrue(f.run().eligible());
    }
    @Test void attributedNotApplicableAlreadySupportsExactGraphWithoutDatasetAcquisition()throws Exception {
        var f=ResearchGateAcceptanceTest.fixture("valid");
        assertTrue(f.dossier().datasetAudits().stream().allMatch(x->x.applicability()==RuleResearchDossier.Applicability.NOT_APPLICABLE));
        assertTrue(f.run().eligible());
        assertFalse(ResearchGateAcceptanceTest.fixture("missing-data").run().eligible());
    }
    @Test void explicitReviewExpiryAlreadyRemainsPerReview()throws Exception {
        assertTrue(ResearchGateAcceptanceTest.fixture("valid").run().eligible());
        assertTrue(ResearchGateAcceptanceTest.fixture("expired").run().reasons().contains("REVIEW_EXPIRED"));
    }
}
