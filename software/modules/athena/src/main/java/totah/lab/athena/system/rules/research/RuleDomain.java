package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

/** Immutable, versioned research metadata; never scientific truth or automatic qualification. */
public record RuleDomain(String schema, String id, String version, List<String> entityKinds, List<String> chemistryClauses, List<String> coordinateClauses, List<String> exclusions, List<Source> perceptionPins, List<Source> measurementPins, String negativeCoverageVersion) {
    public RuleDomain { ResearchCodec.schema(schema,"athena-rule-domain/1"); ResearchCodec.text(id); ResearchCodec.text(version); ResearchCodec.text(negativeCoverageVersion); entityKinds=ResearchCodec.strings(entityKinds); chemistryClauses=ResearchCodec.strings(chemistryClauses); coordinateClauses=ResearchCodec.strings(coordinateClauses); exclusions=ResearchCodec.strings(exclusions); perceptionPins=List.copyOf(perceptionPins); measurementPins=List.copyOf(measurementPins); }
}
