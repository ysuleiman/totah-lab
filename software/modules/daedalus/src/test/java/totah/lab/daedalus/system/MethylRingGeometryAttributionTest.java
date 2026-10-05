package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.SystemStateView;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.EvidenceExchange;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;

/** Explicit synthetic attribution, not an automatic adapter or CH-pi qualification. */
class MethylRingGeometryAttributionTest {
    static Fixture fixture(boolean explicit) {
        var methyl=fixtureBase("ethane");var ring=fixtureBase("benzene");
        var atoms=new ArrayList<>(methyl.graph().atoms());var bonds=new ArrayList<>(methyl.graph().bonds());var h=new TreeMap<>(methyl.hydrogens());
        for(var a:ring.graph().atoms()) {atoms.add(new MolecularGraph.Atom("r"+a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties()));h.put("r"+a.id(),ring.hydrogens().get(a.id()));}
        for(var b:ring.graph().bonds())bonds.add(new MolecularGraph.Bond("r"+b.id(),"r"+b.firstAtomId(),"r"+b.secondAtomId(),b.order(),b.aromatic(),b.stereochemistry(),b.properties()));
        var f=new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture","synthetic disconnected ethane/benzene; no physical complex")),h);
        if(explicit)f=explicit(f);
        atoms=new ArrayList<>();
        for(var a:f.graph().atoms()) {
            double x=0,y=0,z=4;
            if(a.id().matches("ra[0-5]")){int i=Integer.parseInt(a.id().substring(2));x=Math.cos(i*Math.PI/3);y=Math.sin(i*Math.PI/3);z=0;}
            else if(a.id().equals("a0"))z=3;
            else if(a.id().equals("a0H0"))z=2;
            else if(a.id().equals("a0H1")){x=1;z=3;}
            else if(a.id().equals("a0H2")){x=-1;z=3;}
            atoms.add(new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(x,y,z),a.properties()));
        }
        return new Fixture(new MolecularGraph(atoms,f.graph().bonds(),f.graph().properties()),f.hydrogens());
    }
    private static Fixture fixtureBase(String name){return B01FunctionalGroupAcceptanceTest.fixture(name);}
    static JsonNode exercise(boolean explicit)throws Exception {
        var f=fixture(explicit);var s=system(List.of(f.graph()),true,false);var before=SystemStateView.bytes(s.snapshot());
        var methyl=report(manifest("METHYL"),s,coverage(s,f));
        var rm=AromaticSystemAcceptanceTest.groupManifests().stream().filter(m->m.ruleId().equals("ATHENA.GROUP.AROMATIC.RING6")).findFirst().orElseThrow();
        var ringEnvelope=AromaticSystemAcceptanceTest.sources(s,coverage(s,f)).stream().filter(e->e.method().id().startsWith("ATHENA.GROUP.AROMATIC.RING6/")).findFirst().orElseThrow();
        var ring=JSON.readTree(ringEnvelope.readPayload());
        var evaluated=totah.lab.athena.system.rules.RuleAnalyzers.evaluator(rm,request(s,rm)).analyze(s,List.of(ringEnvelope),Map.of()).getFirst();
        assertEquals(totah.lab.mnemosyne.EvidenceInterpretation.Status.NOT_EVALUATED,evaluated.status());
        assertEquals(ring,JSON.readTree(evaluated.measurements().get("payload")));
        assertEquals(2,methyl.path("occurrences").size());assertEquals(1,ring.path("occurrences").size());
        var me=envelope(s,"athena:group-identities",methyl);var re=envelope(s,"athena:group-identities",ring);
        var p=ContinuousGeometryAcceptanceTest.plan(s);var component=s.components().getFirst();assertEquals(1,component.correspondenceAlternatives().size());var mapping=component.correspondenceAlternatives().getFirst();
        var refs=new ArrayList<AtomReference>();for(var id:ring.path("occurrences").get(0).path("memberAtomIds"))refs.add(mapping.get(id.asText()));
        assertEquals(6,refs.size());ContinuousGeometryAcceptanceTest.group(p,"ring",refs.toArray(AtomReference[]::new));
        ((ObjectNode)p.path("groups").get(0)).set("sourceReferences",ContinuousGeometryAcceptanceTest.node(List.of(re.reference())));
        p.set("sourceReferences",ContinuousGeometryAcceptanceTest.node(List.of(me.reference(),re.reference())));
        int hydrogenCount=0;
        for(var occurrence:methyl.path("occurrences")) {
            String carbon=occurrence.path("memberAtomIds").get(0).asText();
            // Every explicitly supplied bonded hydrogen is retained; counts never invent coordinates.
            for(var bond:f.graph().bonds()) {
                String other=bond.firstAtomId().equals(carbon)?bond.secondAtomId():bond.secondAtomId().equals(carbon)?bond.firstAtomId():null;
                if(other!=null&&f.graph().atom(other).orElseThrow().element().equals("H")) {
                    ContinuousGeometryAcceptanceTest.op(p,"POINT_PAIR_GROUP",mapping.get(carbon),mapping.get(other)).put("groupId","ring");hydrogenCount++;
                }
            }
        }
        assertEquals(explicit?6:0,hydrogenCount);
        var result=ContinuousGeometryV3AcceptanceTest.runV3(s,p,null,100,100);
        assertEquals(hydrogenCount,result.path("operations").size());assertEquals(p,result.path("plan"));
        var exchange=new EvidenceExchange();for(var e:List.of(me,re,envelope(s,"athena:rule-measurements",result)))assertEquals(e,exchange.decodeRecord(exchange.encodeRecord(e)));
        assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return result;
    }
    @Test void allExplicitMethylHydrogensAndRingSourceSurvive(){assertDoesNotThrow(()->exercise(true));}
    @Test void implicitCountsDoNotInventHydrogenOrientations(){assertDoesNotThrow(()->exercise(false));}
    public static void main(String[] args)throws Exception {Files.write(Path.of(args[0]),SystemStateView.bytes(Map.of("explicit",exercise(true),"implicit",exercise(false))));}
}
