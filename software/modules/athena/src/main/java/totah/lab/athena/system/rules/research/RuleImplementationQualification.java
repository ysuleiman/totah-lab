package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

public record RuleImplementationQualification(String schema, String ruleKey, String definitionSha256,
        String domainSha256, List<Source> implementationPins, List<Source> fixturePins, List<Check> checkResults,
        ScientificReference reviewer, Source reviewSource, Instant completedAt) {
    public record Check(String id, boolean passed, Source resultArtifact, String declaredScope) {
        public Check { ResearchCodec.text(id); Objects.requireNonNull(resultArtifact); ResearchCodec.text(declaredScope); }
    }
    public RuleImplementationQualification {
        ResearchCodec.schema(schema,"athena-rule-implementation-qualification/1"); ResearchCodec.text(ruleKey);
        ResearchCodec.hash(definitionSha256); ResearchCodec.hash(domainSha256);
        implementationPins=List.copyOf(implementationPins); fixturePins=List.copyOf(fixturePins); checkResults=List.copyOf(checkResults);
        Objects.requireNonNull(reviewer); Objects.requireNonNull(reviewSource); Objects.requireNonNull(completedAt);
        if(checkResults.stream().map(Check::id).distinct().count()!=checkResults.size())throw new IllegalArgumentException("duplicate check identity");
    }
}
