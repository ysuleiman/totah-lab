package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Domain and degeneracy exercises; numerical pass never becomes scientific eligibility. */
class ImplicitHProxyDomainTest {
    static ImplicitHProxyAcceptanceTest.Fixture sample(String variant)throws Exception{
        var d=ChemicalRoleAcceptanceTest.chemical("methylamine");var a=ChemicalRoleAcceptanceTest.chemical("methylamine");
        double x=Math.cos(Math.toRadians(109.5)),y=Math.sin(Math.toRadians(109.5));
        d=WaterBridgeFixtures.position(d,"a1",0,0,0);d=WaterBridgeFixtures.position(d,"a0",x,y,0);
        a=WaterBridgeFixtures.position(a,"a1",3,0,0);a=WaterBridgeFixtures.position(a,"a0",3-x,y,0);
        if(variant.equals("coincident"))a=WaterBridgeFixtures.position(a,"a1",0,0,0);
        if(variant.equals("zero-neighbor"))d=WaterBridgeFixtures.position(d,"a0",0,0,0);
        if(variant.equals("far")){a=WaterBridgeFixtures.position(a,"a1",4,0,0);a=WaterBridgeFixtures.position(a,"a0",4-x,y,0);}
        if(variant.equals("charged"))d=WaterBridgeFixtures.charge(d,"a1",1);
        if(variant.equals("tertiary-donor"))d=ChemicalRoleAcceptanceTest.chemical("trimethylamine");
        var fs=List.of(d,a);var s=system(fs.stream().map(B01FunctionalGroupAcceptanceTest.Fixture::graph).toList(),true,false);
        if(variant.equals("frame"))s=new SystemStateView(s.identity(),s.graph(),s.components(),s.sources(),Set.of(),s.charges(),false,true,List.of("unqualified frame"));
        if(variant.equals("ambiguous")){var cs=new ArrayList<>(s.components());var c=cs.getFirst();cs.set(0,new SystemStateView.Component(c.identity(),c.chemistry(),List.of(c.correspondenceAlternatives().getFirst(),c.correspondenceAlternatives().getFirst()),c.limitations()));s=new SystemStateView(s.identity(),s.graph(),cs,s.sources(),Set.of(),s.charges(),true,true,List.of("ambiguous mapping"));}
        var m=RuleRegistry.decode(Files.readAllBytes(ImplicitHProxyAcceptanceTest.MANIFEST));var sources=JSON.readTree(m.parameters().get("sources").value());var inputs=new ArrayList<EvidenceEnvelope>();
        for(int i=0;i<2;i++)for(var it=sources.fields();it.hasNext();){var e=it.next();var source=RuleRegistry.decode(SystemStateView.bytes(e.getValue()));var c=coverage(s,fs.get(i));c.set("componentReference",node(s.components().get(i).identity()));
            if(variant.equals("aromaticity"))c.path("atomState").forEach(n->((ObjectNode)n).put("aromaticityStatus","UNKNOWN_INCONCLUSIVE"));
            if(variant.equals("charge-evidence"))c.path("atomState").forEach(n->((ObjectNode)n).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
            var collector=RuleAnalyzers.collector(source,B01FunctionalGroupAcceptanceTest.request(s,source),BACKEND);var finding=collector.analyze(s,List.of(envelope(s,"athena:group-source-coverage",c),envelope(s,"athena:group-definition",JSON.readTree(source.parameters().get("definition").value()))),Map.of()).getFirst();
            inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"i03-domain"),i+":"+e.getKey(),"athena:group-identities",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),s.subject(),T,List.of("synthetic")));
        }
        return new ImplicitHProxyAcceptanceTest.Fixture(new HbondCandidateFixtures.Sample(s,m,B01FunctionalGroupAcceptanceTest.request(s,m),inputs));
    }
    @ParameterizedTest @ValueSource(strings={"pass","far","coincident","zero-neighbor","frame","ambiguous","charged","tertiary-donor","aromaticity","charge-evidence"})
    void domainAndGeometryRemainSeparateFromActivation(String variant)throws Exception{
        var f=sample(variant);var result=f.result();var cs=result.get("candidates");
        if(variant.equals("pass")||variant.equals("far")){assertEquals(1,cs.size());var c=cs.get(0);assertEquals(0,c.get("donorMinimumDeviationDegrees").asDouble(),1e-12);assertEquals(0,c.get("acceptorMinimumDeviationDegrees").asDouble(),1e-12);assertTrue(c.get("reasons").toString().contains(variant.equals("pass")?"comparisons pass":"comparisons fail"));}
        if(variant.equals("coincident")||variant.equals("zero-neighbor"))assertTrue(cs.get(0).get("reasons").toString().contains("geometry unresolved"));
        if(variant.equals("frame")||variant.equals("aromaticity"))assertTrue(cs.get(0).get("geometry").isNull());
        if(Set.of("ambiguous","charged","tertiary-donor","charge-evidence").contains(variant))assertTrue(cs.isEmpty());
    }
}
