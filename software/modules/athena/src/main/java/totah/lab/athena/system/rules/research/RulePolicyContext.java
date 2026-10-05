package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

public record RulePolicyContext(String schema, Source policy, List<Invalidation> invalidations,
                                Instant asOf, ScientificReference issuer, Source contextSource) {
    public record Invalidation(String targetSha256, Instant effectiveAt, String reason, Source source) {
        public Invalidation { ResearchCodec.hash(targetSha256); Objects.requireNonNull(effectiveAt); ResearchCodec.text(reason); Objects.requireNonNull(source); }
    }
    public RulePolicyContext { ResearchCodec.schema(schema,"athena-rule-policy-context/1"); Objects.requireNonNull(policy); invalidations=List.copyOf(invalidations); Objects.requireNonNull(asOf); Objects.requireNonNull(issuer); Objects.requireNonNull(contextSource); }
}
