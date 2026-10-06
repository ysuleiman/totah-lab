package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

public record RuleImplementationQualificationV2(String schema, String ruleKey, String definitionSha256,
        String domainSha256, String manifestSha256, List<Source> implementationPins, List<Source> fixturePins, List<RuleImplementationQualification.Check> checkResults,
        ScientificReference reviewer, Source reviewSource, Instant completedAt) {
    public RuleImplementationQualificationV2 {
        ResearchCodec.schema(schema,"athena-rule-implementation-qualification/2"); ResearchCodec.text(ruleKey);
        ResearchCodec.hash(definitionSha256); ResearchCodec.hash(domainSha256); ResearchCodec.hash(manifestSha256);
        implementationPins=List.copyOf(implementationPins); fixturePins=List.copyOf(fixturePins); checkResults=List.copyOf(checkResults);
        Objects.requireNonNull(reviewer); Objects.requireNonNull(reviewSource); Objects.requireNonNull(completedAt);
        if(checkResults.stream().map(RuleImplementationQualification.Check::id).distinct().count()!=checkResults.size())throw new IllegalArgumentException("duplicate check identity");
    }
}
