package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

/** Immutable, versioned research metadata; never scientific truth or automatic qualification. */
public record RuleQualificationReceipt(String schema, String ruleKey, String manifestSha256, Source eligibility, Source implementationReport, Source foundationCertificate, Source stateBinding, Source request, totah.lab.athena.system.SystemGraphCertificate.Status qualification, QualificationMode mode, Instant evaluatedAt, List<String> reasons) {
    public RuleQualificationReceipt { ResearchCodec.schema(schema,"athena-rule-qualification-receipt/1"); ResearchCodec.text(ruleKey); ResearchCodec.hash(manifestSha256); Objects.requireNonNull(eligibility); Objects.requireNonNull(implementationReport); Objects.requireNonNull(foundationCertificate); Objects.requireNonNull(stateBinding); Objects.requireNonNull(request); Objects.requireNonNull(qualification); Objects.requireNonNull(mode); Objects.requireNonNull(evaluatedAt); reasons=ResearchCodec.strings(reasons); }
}
