package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

/** Immutable, versioned research metadata; never scientific truth or automatic qualification. */
public record ResearchBinding(String schema, Source dossier, Source reviewPolicy, Source domain, String definitionSha256, String projectionVersion) {
    public ResearchBinding { if(!Set.of("athena-rule-research-binding/1","athena-rule-research-binding/2").contains(schema))throw new IllegalArgumentException("unsupported research schema"); ResearchCodec.schema(projectionVersion,schema.endsWith("/2")?"athena-rule-definition-projection/2":"athena-rule-definition-projection/1"); ResearchCodec.hash(definitionSha256); Objects.requireNonNull(dossier); Objects.requireNonNull(reviewPolicy); Objects.requireNonNull(domain); }
}
