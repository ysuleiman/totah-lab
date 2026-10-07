package totah.lab.daedalus.system;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.system.SystemStateView;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.util.*;

/** Bounded routing of explicit source coverage, not a second chemistry or coverage evaluator. */
final class DirectAssessmentInputs {
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private DirectAssessmentInputs() { }
    static List<EvidenceEnvelope> resolve(RuleManifest manifest,RuleRequest request,SystemStateView state,
                                          List<EvidenceEnvelope> selected)throws IOException {
        if(manifest.implementationId().equals("athena.i03-n-sp3-s1")) {
            if(!state.binding().equals(request.state()))throw new IOException("S1 direct state mismatch");
            return List.copyOf(selected); // Current invocation only; never the inherited catalog. S1 checks all pins and scope.
        }
        if(manifest.implementationId().equals("athena.events"))return EventAssessmentInputs.resolve(request,state,selected);
        if(!manifest.implementationId().equals("athena.ss-connectivity")||!state.binding().equals(request.state()))
            throw new IOException("unsupported direct input binding");
        var applicable=new TreeMap<String,EvidenceEnvelope>();
        for(var envelope:selected) {
            if(!envelope.evidenceType().equals("athena:group-source-coverage"))continue;
            var bytes=envelope.readPayload();
            if(!EvidenceExchange.sha256(bytes).equals(envelope.payloadSha256()))throw new IOException("selected coverage hash mismatch");
            var node=JSON.readTree(bytes);
            if(node==null||!node.isObject()||!node.path("schema").asText().equals("athena-group-source-coverage/1")
                    ||!node.path("stateBinding").equals(JSON.valueToTree(state.binding())))
                throw new IOException("selected coverage schema/state mismatch");
            var component=state.components().stream().filter(c->JSON.valueToTree(c.identity()).equals(node.path("componentReference")))
                    .findFirst().orElseThrow(()->new IOException("selected coverage component not in state"));
            // Coverage for a different component is preserved, but cannot establish this pair's absence.
            boolean relevant=component.correspondenceAlternatives().stream()
                    .anyMatch(map->request.atoms().stream().anyMatch(map.values()::contains));
            if(!relevant)continue;
            String key=new String(SystemStateView.bytes(envelope.reference()),java.nio.charset.StandardCharsets.UTF_8);
            var old=applicable.putIfAbsent(key,envelope);
            if(old!=null&&!old.equals(envelope))throw new IOException("conflicting selected evidence identity");
        }
        return List.copyOf(applicable.values());
    }
}
