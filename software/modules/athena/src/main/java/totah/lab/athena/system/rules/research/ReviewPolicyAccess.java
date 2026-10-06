package totah.lab.athena.system.rules.research;

import java.util.*;
import totah.lab.mnemosyne.ScientificReference;

/** Internal version adapter; document bytes and authority semantics stay version-specific. */
record ReviewPolicyAccess(Object document, boolean v2, List<String> applicableRulePrefixes,
    List<String> requiredSystems,List<String> requiredAuditCategories,
    List<ScientificReference> authorizedReviewers,List<ScientificReference> implementationReviewers,
    List<ScientificReference> authorizedContextIssuers,List<String> invalidatingChangeKinds,
    List<String> implementationChanges,List<String> requiredQualificationChecks) {
    static ReviewPolicyAccess of(RuleReviewPolicy p) { return new ReviewPolicyAccess(p,false,p.applicableRulePrefixes(),p.requiredSystems(),p.requiredAuditCategories(),p.authorizedReviewers(),p.authorizedReviewers(),p.authorizedContextIssuers(),p.invalidatingChangeKinds(),List.of(),p.requiredQualificationChecks()); }
    static ReviewPolicyAccess of(RuleReviewPolicyV2 p) { return new ReviewPolicyAccess(p,true,p.applicableRulePrefixes(),p.requiredSystems(),p.requiredAuditCategories(),p.authorizedScientificReviewers(),p.authorizedImplementationReviewers(),p.authorizedContextIssuers(),p.scientificInvalidatingChangeKinds(),p.implementationInvalidatingChangeKinds(),p.requiredQualificationChecks()); }
}
