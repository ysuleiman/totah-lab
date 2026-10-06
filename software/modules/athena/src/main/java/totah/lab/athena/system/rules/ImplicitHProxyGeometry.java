package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** Approved real-atom SP3 proxy arithmetic; never generates a hydrogen or scientific authority. */
final class ImplicitHProxyGeometry {
    private ImplicitHProxyGeometry() { }
    static Double minimum(List<Double> angles){
        if(angles.isEmpty()||angles.stream().anyMatch(a->a==null||!Double.isFinite(a)||a<0||a>180))return null;
        return angles.stream().mapToDouble(a->Math.abs(a-109.5)).min().orElseThrow();
    }
    static Boolean predicate(Double distance,Double donorDeviation,Double acceptorDeviation){
        if(distance==null||donorDeviation==null||acceptorDeviation==null||!Double.isFinite(distance)
                ||!Double.isFinite(donorDeviation)||!Double.isFinite(acceptorDeviation)||distance<=0||donorDeviation<0||acceptorDeviation<0)return null;
        return distance<=3.5&&donorDeviation<=25&&acceptorDeviation<=90;
    }
    static JsonNode measure(SystemStateView s,RuleManifest m,RuleRequest r,EvidenceEnvelope parent,
                            AtomReference d,AtomReference a,List<AtomReference> dn,List<AtomReference> an)throws Exception{
        var geometry=RuleRegistry.decode(SystemStateView.bytes(HbondCandidateRules.parameter(m,"geometry")));
        var plan=JSON.createObjectNode();plan.put("schema","athena-continuous-geometry-plan/1");plan.set("stateBinding",JSON.valueToTree(s.binding()));
        plan.put("coordinateUnit","ANGSTROM");plan.putArray("groups");plan.putNull("radiusAssignmentReference");
        plan.set("coordinateSourceReferences",JSON.valueToTree(s.sources()));plan.set("sourceReferences",JSON.valueToTree(List.of(parent.reference())));
        plan.putArray("limitations").add("Real heavy atoms only; SP3 applicability unqualified; no observed or inferred H geometry");
        var ops=plan.putArray("operations");operation(ops.addObject(),"DA","DISTANCE",d,a);
        for(var n:dn)operation(ops.addObject(),"D:"+canonical(n),"ANGLE",n,d,a);
        for(var n:an)operation(ops.addObject(),"A:"+canonical(n),"ANGLE",n,a,d);
        var atoms=new TreeSet<AtomReference>();atoms.add(d);atoms.add(a);atoms.addAll(dn);atoms.addAll(an);
        var request=new RuleRequest(s.binding(),geometry.key(),RuleRegistry.digest(geometry),List.copyOf(atoms),List.of(),List.of(),r.radiusAngstrom(),0,r.maximumNodes(),r.maximumCandidates());
        byte[] bytes=SystemStateView.bytes(plan);String hash=EvidenceExchange.sha256(bytes);
        var e=new EvidenceEnvelope(new ScientificReference(ScientificReference.Kind.EVIDENCE_ENVELOPE,"athena.implicit-h-proxy.plan",hash,"1"),"athena:continuous-geometry-plan","application/json","1",
                Optional.of(Base64.getEncoder().encodeToString(bytes)),Optional.empty(),hash,parent.provenance(),ImplicitHProxyRules.method(m,false),parent.context(),List.of(s.subject()),List.of(),List.of("Derived real-heavy-atom measurement plan"),parent.recordedAt());
        var finding=ContinuousGeometryRules.analyzer(geometry,request,false).analyze(s,List.of(e),Map.of()).getFirst();
        var out=JSON.createObjectNode();out.set("plan",plan);out.set("measurements",JSON.readTree(finding.measurements().get("payload")));return out;
    }
    private static void operation(com.fasterxml.jackson.databind.node.ObjectNode n,String id,String kind,AtomReference...atoms){n.put("id",id);n.put("kind",kind);n.set("atoms",JSON.valueToTree(List.of(atoms)));}
}
