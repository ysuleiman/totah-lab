package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Synthetic authoritative chemistry and deliberately assigned geometry, not relaxed molecules. */
final class HbondCandidateFixtures {
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/hbond-candidate-v1/ATHENA.HBOND.EXPLICIT_H_DIRECTIONAL_CANDIDATE.rule.json");
    record Sample(SystemStateView state, RuleManifest manifest, RuleRequest request, List<EvidenceEnvelope> inputs) { }
    static RuleManifest manifest() throws Exception { return RuleRegistry.decode(Files.readAllBytes(MANIFEST)); }
    static String[] acceptor(String name) { return switch(name) {
        case "ALCOHOL_O" -> new String[]{"methanol","a1"};
        case "ALDEHYDE_CARBONYL_O" -> new String[]{"acetaldehyde","a2"};
        case "KETONE_CARBONYL_O" -> new String[]{"acetone","a2"};
        case "ESTER_CARBONYL_O" -> new String[]{"methyl_acetate","a2"};
        case "AMIDE_CARBONYL_O" -> new String[]{"acetamide","a2"};
        case "AMINE_PRIMARY" -> new String[]{"methylamine","a1"};
        case "AMINE_SECONDARY" -> new String[]{"dimethylamine","a1"};
        case "AMINE_TERTIARY" -> new String[]{"trimethylamine","a1"};
        case "ETHER_O" -> new String[]{"dimethylether","a1"};
        case "PYRIDINE_LIKE_N" -> new String[]{"pyridine","a0"};
        default -> throw new IllegalArgumentException(name);
    }; }
    static Fixture position(Fixture f, String anchor, boolean donor, double da, double degrees, boolean explicit) {
        if(explicit) f=explicit(f);
        // H=(1,0,0), D=(0,0,0); choose H-A length to give the requested D-A distance.
        double theta=Math.toRadians(degrees), cosine=Math.cos(theta);
        double ha=cosine+Math.sqrt(da*da-1+cosine*cosine);
        var atoms=new ArrayList<MolecularGraph.Atom>();
        for(var a:f.graph().atoms()) {
            double x=10+atoms.size(),y=3,z=2;
            if(a.id().equals(anchor)){ x=donor?0:1-ha*cosine;y=donor?0:ha*Math.sin(theta);z=0; }
            if(!donor&&da==0&&a.id().equals(anchor)){x=0;y=0;z=0;}
            if(donor&&a.id().startsWith(anchor+"H")){x=1;y=0;z=0;}
            atoms.add(new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(x,y,z),a.properties()));
        }
        return new Fixture(new MolecularGraph(atoms,f.graph().bonds(),f.graph().properties()),f.hydrogens());
    }
    static Sample sample(String donor, String acceptor, double da, double angle, boolean explicit, String coverageMode) throws Exception {
        var row=ChemicalRoleAcceptanceTest.cases().stream().filter(r->r[0].equals(donor)).findFirst().orElseThrow();
        var ac=acceptor(acceptor);var d=position(ChemicalRoleAcceptanceTest.chemical(row[1]),row[2],true,da,angle,explicit);
        var a=position(ChemicalRoleAcceptanceTest.chemical(ac[0]),ac[1],false,da,angle,false);
        var state=system(List.of(d.graph(),a.graph()),true,false);var m=manifest();
        var roles=JSON.readTree(m.parameters().get("roles").value());var sources=JSON.readTree(m.parameters().get("sources").value());
        var inputs=new ArrayList<EvidenceEnvelope>();
        for(int i=0;i<2;i++) {
            var ids=new TreeSet<String>();
            var descriptors=i==0?roles.get("donors").get(donor):roles.get("acceptors").get(acceptor);
            descriptors.forEach(n->ids.add(n.get("id").asText()));
            if(i==1&&roles.get("contexts").has(acceptor))roles.get("contexts").get(acceptor).forEach(n->ids.add(n.get("id").asText()));
            for(var id:ids) {
                var source=RuleRegistry.decode(SystemStateView.bytes(sources.get(id)));var c=coverage(state,i==0?d:a);
                c.set("componentReference",node(state.components().get(i).identity()));
                if(coverageMode.equals("incomplete"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
                if(coverageMode.equals("unknownH"))c.get("atomState").forEach(n->{((ObjectNode)n).put("hydrogenMode","UNKNOWN");((ObjectNode)n).putNull("implicitHydrogenCount");});
                var collector=RuleAnalyzers.collector(source,request(state,source),BACKEND);
                var finding=collector.analyze(state,List.of(envelope(state,"athena:group-source-coverage",c),envelope(state,"athena:group-definition",JSON.readTree(source.parameters().get("definition").value()))),Map.of()).getFirst();
                var bytes=finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i02-fixture"),i+":"+id,"athena:group-identities",bytes,collector.method(),state.subject(),T,List.of("synthetic source report")));
            }
        }
        return new Sample(state,m,AthenaScientificRulesAcceptanceTest.request(state,m,10000,false),List.copyOf(inputs));
    }
    static JsonNode collect(Sample s) throws Exception {
        byte[] before=SystemStateView.bytes(s.state().snapshot());
        var collector=RuleAnalyzers.collector(s.manifest(),s.request(),BACKEND);
        var finding=collector.analyze(s.state(),s.inputs(),Map.of()).getFirst();
        var report=JSON.readTree(finding.measurements().get("payload"));
        var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i02-fixture"),"measurements","athena:rule-measurements",SystemStateView.bytes(report),collector.method(),s.state().subject(),T,List.of());
        var exchange=new EvidenceExchange();var restored=(EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(raw));
        var inputs=new ArrayList<>(s.inputs());inputs.add(restored);
        var evaluated=RuleAnalyzers.evaluator(s.manifest(),s.request()).analyze(s.state(),inputs,Map.of()).getFirst();
        org.junit.jupiter.api.Assertions.assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status(),"no invented current-policy receipt");
        org.junit.jupiter.api.Assertions.assertEquals(report,JSON.readTree(evaluated.measurements().get("payload")));
        org.junit.jupiter.api.Assertions.assertArrayEquals(before,SystemStateView.bytes(s.state().snapshot()));
        return report;
    }
    static JsonNode pair(JsonNode report,String d,String a) {
        for(var p:report.get("classPairs"))if(p.get("donorClass").asText().equals(d)&&p.get("acceptorClass").asText().equals(a))return p;
        throw new IllegalArgumentException("missing pair");
    }
}
