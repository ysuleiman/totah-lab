package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.FoundationGroupAcceptanceTest.graph;

/** Opt-in local-context mechanics; these synthetic predicates do not activate a scientific group. */
class GroupContextAcceptanceTest {
    @TempDir Path temp;
    static RuleManifest local(RuleManifest original,java.util.function.Consumer<ObjectNode> change)throws Exception {
        var tree=(ObjectNode)node(original);var d=(ObjectNode)JSON.readTree(original.parameters().get("definition").value());
        d.put("schema","athena-group-definition/2");d.putArray("occurrenceExclusions");change.accept(d);
        tree.put("implementationVersion","2");tree.put("profile","ATHENA_GROUP_CONTEXT_V2");
        ((ObjectNode)tree.get("measurementsProduced")).put("group-identities","athena-group-identities/2");
        ((ObjectNode)tree.get("parameters").get("definition")).put("value",d.toString());
        return RuleRegistry.decode(SystemStateView.bytes(tree));
    }
    static ObjectNode exclusion(ObjectNode d,String id,String query,String role,int index) {
        var e=((com.fasterxml.jackson.databind.node.ArrayNode)d.get("occurrenceExclusions")).addObject();
        e.put("id",id);e.put("patternId","FIXTURE/"+id);e.put("patternVersion","1");e.put("query",query);
        e.putObject("anchors").putArray(role).add(index);e.putArray("hydrogenRoles");
        e.put("rationale","Synthetic anchored context witness; no functional or biological conclusion");e.set("sourceReferences",d.get("sourceReferences"));return e;
    }
    static RuleManifest amine()throws Exception {
        return local(FoundationGroupAcceptanceTest.candidate("AMINE.PRIMARY_ALIPHATIC"),d->{
            d.put("query","[#6]-[N;!a;X3;H2;+0]");
            exclusion(d,"amide","[N]-[C](=[O])","nitrogen",0);
        });
    }
    static Fixture mixed(){return graph("N C C C O N","0-1 1-2 2-3 3=4 3-5","2 2 2 0 0 2",Map.of(),Set.of());}
    static JsonNode run(RuleManifest m,Fixture f)throws Exception {
        var s=system(List.of(f.graph()),true,false);byte[] before=SystemStateView.bytes(s.snapshot());
        var result=report(m,s,coverage(s,f));assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));return result;
    }
    @Test void mixedAmineAndAmideExcludesOnlyAnchoredSite()throws Exception {
        var r=run(amine(),mixed());assertEquals("athena-group-identities/2",r.get("schema").asText());
        assertEquals("SUPPORTED_PRESENT",r.get("assessment").asText());assertEquals(1,r.get("occurrences").size());
        assertEquals(List.of("a0"),JSON.convertValue(r.get("occurrences").get(0).get("memberAtomIds"),List.class));
        assertEquals(2,r.get("contextAssessments").size());
        assertEquals("NOT_EXCLUDED",r.get("contextAssessments").get(0).get("status").asText());
        assertEquals("EXCLUDED_BY_CONTEXT",r.get("contextAssessments").get(1).get("status").asText());
        assertEquals(List.of("a5","a3","a4"),JSON.convertValue(r.get("contextAssessments").get(1).get("matchedExclusionCorrespondences").get(0),List.class));
    }
    @Test void excludedOnlyIsExhaustiveAbsenceAndOriginalMatchesSurvive()throws Exception {
        var r=run(amine(),fixture("amide"));assertEquals("ABSENT_FALSE",r.get("assessment").asText());
        assertTrue(r.get("occurrences").isEmpty());assertFalse(r.get("b00Results").isEmpty());assertEquals(1,r.get("contextAssessments").size());
        assertEquals("EXCLUDED_BY_CONTEXT",r.get("contextAssessments").get(0).get("status").asText());
        assertEquals("SUPPORTED_PRESENT",report("AMIDE",fixture("amide")).get("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> completeStateAndOccurrenceCases(){return Stream.of("explicit","double","permutation","unknownH","unknownCharge","incomplete").map(kind->DynamicTest.dynamicTest(kind,()->{
        var f=mixed();if(kind.equals("explicit"))f=explicit(f);if(kind.equals("double"))f=doubled(f);
        if(kind.equals("permutation")){var a=new ArrayList<>(f.graph().atoms());var b=new ArrayList<>(f.graph().bonds());Collections.reverse(a);Collections.reverse(b);f=new Fixture(new MolecularGraph(a,b,f.graph().properties()),f.hydrogens());}
        var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        if(kind.equals("unknownH"))c.get("atomState").forEach(x->{((ObjectNode)x).put("hydrogenMode","UNKNOWN");((ObjectNode)x).putNull("implicitHydrogenCount");});
        if(kind.equals("unknownCharge"))c.get("atomState").forEach(x->((ObjectNode)x).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));
        if(kind.equals("incomplete"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");
        var r=report(amine(),s,c);boolean unknown=kind.startsWith("unknown")||kind.equals("incomplete");
        assertEquals(unknown?"UNKNOWN_INCONCLUSIVE":"SUPPORTED_PRESENT",r.get("assessment").asText());
        assertEquals(unknown?0:kind.equals("double")?2:1,r.get("occurrences").size());
        if(kind.equals("permutation")){var normal=run(amine(),mixed());assertEquals(normalized(normal),normalized(r));assertEquals(normal.get("contextAssessments"),r.get("contextAssessments"));}
    }));}
    @Test void sulfurContextDoesNotSuppressUnrelatedNitrogen()throws Exception {
        var m=local(FoundationGroupAcceptanceTest.candidate("AMINE.PRIMARY_ALIPHATIC"),d->{
            d.put("query","[N;!a;X3;H2;+0]");d.putArray("memberQueryIndices").add(0);d.putObject("roles").putArray("nitrogen").add(0);
            exclusion(d,"sulfonamide","[N]-[S](=[O])(=[O])","nitrogen",0);
        });
        var f=graph("N C C S O O N","0-1 1-2 2-3 3=4 3=5 3-6","2 2 2 0 0 0 2",Map.of(),Set.of());
        var r=run(m,f);assertEquals(1,r.get("occurrences").size());assertEquals("a0",r.get("occurrences").get(0).get("memberAtomIds").get(0).asText());
        assertEquals(2,r.get("contextAssessments").get(1).get("matchedExclusionCorrespondences").size(),"equivalent O roles are preserved, not duplicate groups");
    }
    @Test void aromaticNitrogenContextIsDistinct()throws Exception {
        var m=local(FoundationGroupAcceptanceTest.candidate("AMINE.PRIMARY_ALIPHATIC"),d->{
            d.put("query","[#7]");d.putArray("memberQueryIndices").add(0);d.putObject("roles").putArray("nitrogen").add(0);
            var state=(ObjectNode)d.get("requiredState");state.putArray("hydrogenRoles");state.putObject("roleHeavyDegree");state.put("aromaticity",true);
            exclusion(d,"aromaticN","[n]","nitrogen",0);
        });
        assertEquals("ABSENT_FALSE",run(m,fixture("pyridine")).get("assessment").asText());
        assertEquals("SUPPORTED_PRESENT",run(m,fixture("amine")).get("assessment").asText());
    }
    @Test void symmetricAlternativesAreNotArbitrarilySelected()throws Exception {
        var m=local(manifest("CARBONYL"),d->{
            d.put("query","[C]-[C]");d.putArray("memberQueryIndices").add(0).add(1);
            d.putObject("roles").putArray("left").add(0);((ObjectNode)d.get("roles")).putArray("right").add(1);
            ((ObjectNode)d.get("supportedDomain")).putArray("unsupportedQueries");
            exclusion(d,"leftOxygen","[C]-[O]","left",0);
        });
        var f=graph("C C O","0-1 1-2","3 2 1",Map.of(),Set.of());var r=run(m,f);
        assertEquals(1,r.get("occurrences").size());assertEquals(2,r.get("contextAssessments").size());
        var statuses=new HashSet<String>();r.get("contextAssessments").forEach(x->statuses.add(x.get("status").asText()));
        assertEquals(Set.of("NOT_EXCLUDED","EXCLUDED_BY_CONTEXT"),statuses);
    }
    static RuleManifest hydrogenCondition()throws Exception {
        return local(manifest("CARBONYL"),d->{
            d.put("query","[C]-[C]");d.putObject("roles").putArray("left").add(0);((ObjectNode)d.get("roles")).putArray("right").add(1);
            ((ObjectNode)d.get("supportedDomain")).putArray("unsupportedQueries");
            var consistency=((ObjectNode)d.get("requiredState")).putObject("hydrogenConsistencyQueries");
            for(int h=0;h<=4;h++)consistency.put(Integer.toString(h),"[*;H"+h+"]");
            var e=exclusion(d,"methylEnd","[C;H3]","left",0);e.withArray("hydrogenRoles").addObject().put("queryIndex",0).put("count",3);
        });
    }
    @Test void missingHOnUnmatchedConditionCannotBecomeNegative()throws Exception {
        var f=fixture("cyclohexane");var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        c.get("atomState").forEach(x->{((ObjectNode)x).put("hydrogenMode","UNKNOWN");((ObjectNode)x).putNull("implicitHydrogenCount");});
        var r=report(hydrogenCondition(),s,c);assertEquals("UNKNOWN_INCONCLUSIVE",r.get("assessment").asText());
        r.get("contextAssessments").forEach(x->assertEquals("UNKNOWN_CONTEXT",x.get("status").asText()));
    }
    @Test void authoritativeHydrogenConditionSurvivesExplicitRepresentation()throws Exception {
        for(var f:List.of(fixture("ethane"),explicit(fixture("ethane"))))assertEquals("ABSENT_FALSE",run(hydrogenCondition(),f).get("assessment").asText());
    }
    @Test void contradictoryExclusionHydrogenStateIsNotRepaired()throws Exception {
        var f=fixture("ethane");var s=system(List.of(f.graph()),true,false);var c=coverage(s,f);
        ((ObjectNode)c.get("atomState").get("a0")).put("implicitHydrogenCount",2);
        assertEquals("UNKNOWN_INCONCLUSIVE",report(hydrogenCondition(),s,c).get("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> unsupportedAndMalformedConditionsFailEvenWithoutPositive(){return Stream.of("[z2]","[C;Q]","C(","[A]").map(q->DynamicTest.dynamicTest(q,()->{
        var bad=local(FoundationGroupAcceptanceTest.candidate("AMINE.PRIMARY_ALIPHATIC"),d->exclusion(d,"bad",q,"nitrogen",0));
        assertThrows(MolecularBackendException.class,()->run(bad,fixture("ethane")));
    }));}
    @Test void malformedPayloadIsRejected()throws Exception {
        var original=FoundationGroupAcceptanceTest.candidate("AMINE.PRIMARY_ALIPHATIC");
        assertThrows(java.io.IOException.class,()->local(original,d->exclusion(d,"x","[N]","missing",0)));
        assertThrows(java.io.IOException.class,()->local(original,d->{exclusion(d,"x","[N]","nitrogen",0);exclusion(d,"x","[N]","nitrogen",0);}));
        assertThrows(java.io.IOException.class,()->local(original,d->exclusion(d,"x","[N]","nitrogen",0).put("extra",true)));
        assertThrows(java.io.IOException.class,()->local(original,d->exclusion(d,"x","[N]","nitrogen",0).putArray("sourceReferences")));
    }
    @Test void emptyContextIsOptInAndEquivalentToOriginalIdentity()throws Exception {
        var f=fixture("ester");var m=manifest("ESTER");var r=run(local(m,d->{}),f);assertEquals(normalized(run(m,f)),normalized(r));assertTrue(r.get("contextAssessments").isEmpty());
        assertEquals("athena-group-identities/1",run(m,f).get("schema").asText());
    }
    @Test void excludedDecisionTamperingFailsReplay()throws Exception {
        var f=mixed();var s=system(List.of(f.graph()),true,false);var m=amine();var r=run(m,f);
        ((ObjectNode)r.get("contextAssessments").get(1)).put("status","NOT_EXCLUDED");
        var env=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"context"),"report","athena:group-identities",SystemStateView.bytes(r),RuleAnalyzers.collector(m,request(s,m),BACKEND).method(),s.subject(),T,List.of());
        assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(m,request(s,m)).analyze(s,List.of(env),Map.of()));
    }
    @Test void failedExclusionKeepsDefinitionAndSourceInJournal()throws Exception {
        var f=mixed();var s=system(List.of(f.graph()),true,false);
        var m=local(FoundationGroupAcceptanceTest.candidate("AMINE.PRIMARY_ALIPHATIC"),d->exclusion(d,"bad","C(","nitrogen",0));
        var inputs=List.of(envelope(s,"athena:group-source-coverage",coverage(s,f)),envelope(s,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value())));
        var catalog=new EvidenceSnapshotCatalog(temp);var analyzer=RuleAnalyzers.collector(m,request(s,m),BACKEND);
        var result=pipeline().run(catalog,Optional.empty(),s,inputs,Map.of(),List.of(analyzer),ref(ScientificReference.Kind.ACTIVITY,"bad-context"),T);
        var history=catalog.read(result.catalogSnapshot()).orElseThrow().history();
        for(var input:inputs)assertEquals(input,history.envelopes().get(input.reference()));
        assertTrue(history.interpretations().values().stream().anyMatch(i->i.status()==EvidenceInterpretation.Status.FAILED));
    }
    public static void main(String[] args)throws Exception {
        var results=new ArrayList<JsonNode>();for(var f:List.of(mixed(),explicit(mixed()),doubled(mixed()),fixture("amide")))results.add(run(amine(),f));
        Files.write(Path.of(args[0]),SystemStateView.bytes(results));
        if(args.length>1) {
            var catalog=new EvidenceSnapshotCatalog(Path.of(args[1]));var f=mixed();var state=system(List.of(f.graph()),true,false);var m=amine();
            var collector=RuleAnalyzers.collector(m,request(state,m),BACKEND);
            var inputs=List.of(envelope(state,"athena:group-source-coverage",coverage(state,f)),envelope(state,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value())));
            var collected=pipeline().run(catalog,Optional.empty(),state,inputs,Map.of(),List.of(collector),ref(ScientificReference.Kind.ACTIVITY,"context-collect"),T);
            var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"context-result"),"result","athena:group-identities",SystemStateView.bytes(results.getFirst()),collector.method(),state.subject(),T,List.of());
            var evaluator=RuleAnalyzers.evaluator(m,request(state,m));
            var evaluated=pipeline().run(catalog,Optional.of(collected.catalogSnapshot()),state,List.of(raw),Map.of(),List.of(evaluator),ref(ScientificReference.Kind.ACTIVITY,"context-evaluate"),T);
            var history=catalog.read(evaluated.catalogSnapshot()).orElseThrow().history();
            assertEquals(raw,history.envelopes().get(raw.reference()));
            assertTrue(history.interpretations().values().stream().anyMatch(i->i.evaluator().equals(evaluator.method())&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
        }
    }
}
