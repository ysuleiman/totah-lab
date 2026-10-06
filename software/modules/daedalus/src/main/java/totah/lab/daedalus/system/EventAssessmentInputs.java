package totah.lab.daedalus.system;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.RuleRequest;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.util.*;

/** Explicit plan artifact routing only; Athena owns all event scientific/coverage semantics. */
final class EventAssessmentInputs {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private EventAssessmentInputs() { }
    static List<EvidenceEnvelope> resolve(RuleRequest request,SystemStateView state,List<EvidenceEnvelope> supplied)throws IOException {
        if(!state.binding().equals(request.state()))throw new IOException("event request anchor mismatch");
        var index=new HashMap<ScientificReference,EvidenceEnvelope>();
        for(var e:supplied){var old=index.putIfAbsent(e.reference(),e);if(old!=null&&!old.equals(e))throw new IOException("conflicting explicit event artifact");}
        var plans=index.values().stream().filter(e->e.evidenceType().equals("athena:event-analysis-plan")).toList();
        if(plans.size()!=1)throw new IOException("one explicitly supplied event plan required");
        var pe=plans.getFirst();var plan=JSON.readTree(pe.readPayload());
        if(!plan.path("schema").asText().equals("athena-event-analysis-plan/1")
                ||!plan.path("stateBinding").equals(JSON.valueToTree(state.binding()))||!plan.path("artifacts").isArray())throw new IOException("event plan schema/state/selection mismatch");
        var order=Comparator.comparing((ScientificReference r)->r.kind().name())
                .thenComparing(ScientificReference::namespace).thenComparing(ScientificReference::id).thenComparing(ScientificReference::version);
        var selected=new TreeMap<ScientificReference,EvidenceEnvelope>(order);selected.put(pe.reference(),pe);
        for(var pin:plan.path("artifacts")) {
            var reference=JSON.treeToValue(pin.path("reference"),ScientificReference.class);var e=index.get(reference);
            if(e==null||!e.payloadSha256().equals(pin.path("sha256").asText()))throw new IOException("event input not explicitly supplied or digest mismatch");
            e.verifyArtifact();selected.put(reference,e);
        }
        return List.copyOf(selected.values());
    }
}
