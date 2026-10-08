package totah.lab.daedalus.system;

import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.Path;
import java.util.*;

/** Synthetic engineering reviews only. No production protocol or current authority is created. */
final class BatchCSourceReviewFixtures {
    static S1ResearchFixtures.Qualified review(String id,RuleManifest base,SystemStateView state,List<totah.lab.gaia.structure.AtomReference> atoms,Path temp,List<EvidenceEnvelope> evidence)throws Exception {
        var sources=new ArrayList<RuleManifest.Source>();var bytes=new HashMap<String,byte[]>();for(var e:evidence){sources.add(new RuleManifest.Source("sha256:"+e.payloadSha256(),e.payloadSha256(),"Exact synthetic engineering source artifact only"));bytes.put(e.payloadSha256(),e.readPayload());}
        var m=new RuleManifest("athena-rule/2",id,"1.0.0","SYNTHETIC_BATCH_C_SOURCE_REVIEW",base.family(),base.tier(),"fixture.batch-c-source-review","1",SystemGraphCertificate.Status.NOT_EVALUATED,false,List.of(),base.requiredChemistry(),List.of(),base.measurementsProduced(),base.classificationStates(),base.parameters(),sources,List.of(),List.of("Engineering review fixture; never production authority"),base.negativeCoverage());
        return S1ResearchFixtures.qualify(m,state,atoms,temp,id.toLowerCase(Locale.ROOT).replace('.','-'),bytes);
    }
}
