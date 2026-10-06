package totah.lab.athena.system.rules.research;

import totah.lab.mnemosyne.ScientificReference;
import java.util.*;

/** Separated review authority; validity remains explicit on each scientific review. */
public record RuleReviewPolicyV2(String schema, String id, String version,
        List<String> applicableRulePrefixes, List<String> requiredSystems,
        List<String> requiredAuditCategories,
        List<ScientificReference> authorizedScientificReviewers,
        List<ScientificReference> authorizedImplementationReviewers,
        List<ScientificReference> authorizedContextIssuers,
        String freshnessMode, boolean requireExpiryAfterReview,
        List<String> scientificInvalidatingChangeKinds,
        List<String> implementationInvalidatingChangeKinds,
        List<String> requiredQualificationChecks, String canonicalizationVersion) {
    public RuleReviewPolicyV2 {
        ResearchCodec.schema(schema,"athena-rule-review-policy/2");
        ResearchCodec.text(id); ResearchCodec.text(version);
        ResearchCodec.schema(freshnessMode,"EXPLICIT_EXPIRY");
        ResearchCodec.schema(canonicalizationVersion,"athena-research-json/1");
        if(!requireExpiryAfterReview)throw new IllegalArgumentException("expiry required");
        applicableRulePrefixes=ResearchCodec.stringSet(applicableRulePrefixes);
        requiredSystems=ResearchCodec.stringSet(requiredSystems);
        requiredAuditCategories=ResearchCodec.stringSet(requiredAuditCategories);
        authorizedScientificReviewers=List.copyOf(authorizedScientificReviewers);
        authorizedImplementationReviewers=List.copyOf(authorizedImplementationReviewers);
        authorizedContextIssuers=List.copyOf(authorizedContextIssuers);
        if(!Collections.disjoint(authorizedScientificReviewers,authorizedImplementationReviewers)
                ||!Collections.disjoint(authorizedScientificReviewers,authorizedContextIssuers))
            throw new IllegalArgumentException("scientific and executor authority must be disjoint");
        scientificInvalidatingChangeKinds=ResearchCodec.stringSet(scientificInvalidatingChangeKinds);
        implementationInvalidatingChangeKinds=ResearchCodec.stringSet(implementationInvalidatingChangeKinds);
        requiredQualificationChecks=ResearchCodec.stringSet(requiredQualificationChecks);
    }
}
