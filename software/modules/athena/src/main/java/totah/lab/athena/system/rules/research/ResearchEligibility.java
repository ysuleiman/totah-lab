package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

/** Immutable, versioned research metadata; never scientific truth or automatic qualification. */
public record ResearchEligibility(String schema, String manifestSha256, String dossierSha256, String domainSha256, String policySha256, String contextSha256, Instant evaluatedAt, QualificationMode mode, boolean eligible, List<String> reasons, List<String> checkedArtifactDigests) {
    public ResearchEligibility { ResearchCodec.schema(schema,"athena-rule-research-eligibility/1"); for(var h:List.of(manifestSha256,dossierSha256,domainSha256,policySha256,contextSha256))ResearchCodec.hash(h); Objects.requireNonNull(evaluatedAt); Objects.requireNonNull(mode); reasons=ResearchCodec.stringSet(reasons); checkedArtifactDigests=ResearchCodec.stringSet(checkedArtifactDigests); checkedArtifactDigests.forEach(ResearchCodec::hash); }
}
