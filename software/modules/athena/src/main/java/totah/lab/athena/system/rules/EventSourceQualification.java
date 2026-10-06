package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** Reuses existing historical manifests and current receipt verification; never issues receipts. */
final class EventSourceQualification {
    private EventSourceQualification() { }
    static boolean verify(JsonNode qualification, RuleManifest manifest, EvidenceInterpretation observation,
                          SystemStateView.Binding state, Map<String,EvidenceEnvelope> artifacts) throws Exception {
        if (qualification.isNull()) return false;
        fields(qualification,"receipt","context");
        if (!manifest.schema().equals("athena-rule/3")) {
            require(qualification.get("receipt").isNull() && qualification.get("context").isNull(), "historical rule cannot acquire current receipt");
            return !manifest.retired() && manifest.qualification()==SystemGraphCertificate.Status.QUALIFIED;
        }
        var receipt=ResearchDocuments.decode(pinned(qualification.get("receipt"),artifacts).readPayload(),RuleQualificationReceipt.class);
        var context=ResearchDocuments.decode(pinned(qualification.get("context"),artifacts).readPayload(),RulePolicyContext.class);
        var byHash=new HashMap<String,byte[]>();
        for(var e:artifacts.values())byHash.put(e.payloadSha256(),e.readPayload());
        ResearchArtifactReader reader=source->{var bytes=byHash.get(source.sha256());if(bytes==null)throw new java.io.IOException("missing explicit qualification dependency");return bytes.clone();};
        RuleQualification.verify(manifest,receipt,context,reader,observation.recordedAt());
        var bound=JSON.readTree(reader.read(receipt.stateBinding()));
        require(bound.equals(JSON.valueToTree(state)),"edge receipt belongs to another state");
        return receipt.qualification()==SystemGraphCertificate.Status.QUALIFIED;
    }
}
