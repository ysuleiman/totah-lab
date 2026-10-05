package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

/** Immutable, versioned research metadata; never scientific truth or automatic qualification. */
public record RuleReviewPolicy(String schema, String id, String version, List<String> applicableRulePrefixes, List<String> requiredSystems, List<String> requiredAuditCategories, List<ScientificReference> authorizedReviewers, List<ScientificReference> authorizedContextIssuers, String freshnessMode, boolean requireExpiryAfterReview, List<String> invalidatingChangeKinds, List<String> requiredQualificationChecks, String canonicalizationVersion) {
    public RuleReviewPolicy { ResearchCodec.schema(schema,"athena-rule-review-policy/1"); ResearchCodec.text(id); ResearchCodec.text(version); ResearchCodec.schema(freshnessMode,"EXPLICIT_EXPIRY"); ResearchCodec.schema(canonicalizationVersion,"athena-research-json/1"); if(!requireExpiryAfterReview)throw new IllegalArgumentException("expiry required"); applicableRulePrefixes=ResearchCodec.stringSet(applicableRulePrefixes); requiredSystems=ResearchCodec.stringSet(requiredSystems); requiredAuditCategories=ResearchCodec.stringSet(requiredAuditCategories); authorizedReviewers=List.copyOf(authorizedReviewers); authorizedContextIssuers=List.copyOf(authorizedContextIssuers); invalidatingChangeKinds=ResearchCodec.stringSet(invalidatingChangeKinds); requiredQualificationChecks=ResearchCodec.stringSet(requiredQualificationChecks); }
}
