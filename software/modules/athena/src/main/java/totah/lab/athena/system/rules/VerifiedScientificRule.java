package totah.lab.athena.system.rules;

import totah.lab.athena.system.rules.research.RuleQualificationReceipt;

/** A checked receipt handle, scoped to the exact rule, context, time and request it contains. */
public final class VerifiedScientificRule {
    private final RuleManifest manifest;
    private final RuleQualificationReceipt receipt;
    VerifiedScientificRule(RuleManifest manifest,RuleQualificationReceipt receipt){this.manifest=manifest;this.receipt=receipt;}
    public RuleManifest manifest(){return manifest;}
    public RuleQualificationReceipt receipt(){return receipt;}
}
