package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

class B01FunctionalGroupAcceptanceTest {
    static final ObjectMapper JSON=new ObjectMapper();
    static final OclMolecularBackend BACKEND=new OclMolecularBackend();
    static final List<String> GROUPS=List.of("CARBONYL","ESTER","AMIDE","AROMATIC","METHYL","THIOL","THIOLATE");
    @TempDir Path temp;
    record Fixture(MolecularGraph graph,Map<String,Integer> hydrogens) { }
    static Fixture fixture(String name) {
        String elements,edges;int[] hs;Map<Integer,Integer> charges=Map.of();boolean aromatic=false;
        switch(name) {
            case "aldehyde"->{elements="CCO";edges="0-1 1=2";hs=new int[]{3,1,0};}
            case "ester"->{elements="CCOOC";edges="0-1 1=2 1-3 3-4";hs=new int[]{3,0,0,0,3};}
            case "amide","chargedAmide"->{elements="CCON";edges="0-1 1=2 1-3";hs=new int[]{3,0,0,name.equals("amide")?2:3};if(name.equals("chargedAmide"))charges=Map.of(3,1);}
            case "acid","carboxylate"->{elements="CCOO";edges="0-1 1=2 1-3";hs=new int[]{3,0,0,name.equals("acid")?1:0};if(name.equals("carboxylate"))charges=Map.of(3,-1);}
            case "carbonate"->{elements="COCOOC";edges="0-1 1-2 2=3 2-4 4-5";hs=new int[]{3,0,0,0,0,3};}
            case "carbamate"->{elements="COCON";edges="0-1 1-2 2=3 2-4";hs=new int[]{3,0,0,0,2};}
            case "formate"->{elements="COOC";edges="0=1 0-2 2-3";hs=new int[]{1,0,0,3};}
            case "formamide"->{elements="CON";edges="0=1 0-2";hs=new int[]{1,0,2};}
            case "resonance"->{elements="CCO";edges="0-1 1-2";hs=new int[]{3,1,0};charges=Map.of(1,1,2,-1);}
            case "methanol"->{elements="CO";edges="0-1";hs=new int[]{3,1};}
            case "amine"->{elements="CN";edges="0-1";hs=new int[]{3,2};}
            case "ethane"->{elements="CC";edges="0-1";hs=new int[]{3,3};}
            case "sulfonium"->{elements="CSCC";edges="0-1 1-2 1-3";hs=new int[]{3,0,3,3};charges=Map.of(1,1);}
            case "thiol","thiolate"->{elements="CS";edges="0-1";hs=new int[]{3,name.equals("thiol")?1:0};if(name.equals("thiolate"))charges=Map.of(1,-1);}
            case "disulfide"->{elements="CSSC";edges="0-1 1-2 2-3";hs=new int[]{3,0,0,3};}
            case "benzene","cyclohexane","pyridine"->{elements=name.equals("pyridine")?"CCCCCN":"CCCCCC";edges="0-1 1-2 2-3 3-4 4-5 5-0";aromatic=!name.equals("cyclohexane");hs=aromatic?new int[]{1,1,1,1,1,name.equals("pyridine")?0:1}:new int[]{2,2,2,2,2,2};}
            default->throw new IllegalArgumentException(name);
        }
        var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();var h=new TreeMap<String,Integer>();
        for(int i=0;i<elements.length();i++){atoms.add(atom("a"+i,""+elements.charAt(i),charges.getOrDefault(i,0),aromatic,i,0,0));h.put("a"+i,hs[i]);}
        for(String edge:edges.split(" ")){int a=edge.charAt(0)-'0',b=edge.charAt(2)-'0';bonds.add(bond("a"+a,"a"+b,aromatic?MolecularGraph.BondOrder.AROMATIC:edge.charAt(1)=='='?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE));}
        return new Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture",name)),h);
    }
    static Fixture explicit(Fixture f) {
        var atoms=new ArrayList<>(f.graph.atoms());var bonds=new ArrayList<>(f.graph.bonds());var counts=new TreeMap<String,Integer>();
        for(var a:f.graph.atoms()) {counts.put(a.id(),0);for(int i=0;i<f.hydrogens.get(a.id());i++){String id=a.id()+"H"+i;atoms.add(atom(id,"H",0,false,atoms.size(),1,0));counts.put(id,0);bonds.add(bond(a.id(),id,MolecularGraph.BondOrder.SINGLE));}}
        return new Fixture(new MolecularGraph(atoms,bonds,f.graph.properties()),counts);
    }
    static Fixture doubled(Fixture f) {
        var a=new ArrayList<>(f.graph.atoms());var b=new ArrayList<>(f.graph.bonds());var h=new TreeMap<>(f.hydrogens);
        for(var x:f.graph.atoms()){a.add(new MolecularGraph.Atom("z"+x.id(),x.element(),x.isotope(),x.formalCharge(),x.explicitHydrogens(),x.aromatic(),x.stereochemistry(),x.coordinates(),x.properties()));h.put("z"+x.id(),f.hydrogens.get(x.id()));}
        for(var x:f.graph.bonds())b.add(new MolecularGraph.Bond("z"+x.id(),"z"+x.firstAtomId(),"z"+x.secondAtomId(),x.order(),x.aromatic(),x.stereochemistry(),x.properties()));
        return new Fixture(new MolecularGraph(a,b,f.graph.properties()),h);
    }
    static String positive(String group){return switch(group){case "CARBONYL"->"aldehyde";case "ESTER"->"ester";case "AMIDE"->"amide";case "AROMATIC"->"benzene";case "METHYL"->"ethane";case "THIOL"->"thiol";case "THIOLATE"->"thiolate";default->throw new IllegalArgumentException();};}
    static RuleManifest manifest(String group)throws Exception {return RuleRegistry.load(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-b01")).manifests().values().stream().filter(m->m.ruleId().equals("ATHENA.GROUP."+group)).findFirst().orElseThrow();}
    static RuleRequest request(SystemStateView s,RuleManifest m){return new RuleRequest(s.binding(),m.key(),RuleRegistry.digest(m),List.of(),List.of(),List.of(),4.5,0,10000,10000);}
    static JsonNode node(Object value)throws Exception{return JSON.readTree(SystemStateView.bytes(value));}
    static ObjectNode coverage(SystemStateView s,Fixture fixture)throws Exception {
        var root=JSON.createObjectNode();root.put("schema","athena-group-source-coverage/1");root.set("stateBinding",node(s.binding()));root.set("componentReference",node(s.components().getFirst().identity()));root.put("completeGraph","SUPPORTED_PRESENT");root.set("sourceReferences",node(s.sources()));root.set("limitations",node(List.of("hand-authored synthetic H/state oracle; not inferred from query")));
        var states=root.putObject("atomState");
        for(var a:fixture.graph.atoms()) {
            var v=states.putObject(a.id());v.put("chargeStatus","SUPPORTED_PRESENT");v.put("formalCharge",a.formalCharge());v.put("hydrogenMode",fixture.graph.atoms().stream().anyMatch(x->x.element().equals("H"))?"EXPLICIT_GRAPH":"AUTHORITATIVE_IMPLICIT");
            var explicitH=new TreeSet<String>();for(var b:fixture.graph.bonds()){String other=b.firstAtomId().equals(a.id())?b.secondAtomId():b.secondAtomId().equals(a.id())?b.firstAtomId():null;if(other!=null&&fixture.graph.atom(other).orElseThrow().element().equals("H"))explicitH.add(other);}
            v.set("explicitHydrogenAtomIds",node(explicitH));v.put("implicitHydrogenCount",fixture.hydrogens.get(a.id()));v.put("aromaticityStatus","SUPPORTED_PRESENT");v.put("aromaticityModel","OCL/2026.7.2");v.set("evidenceReferences",node(s.sources()));
        }return root;
    }
    static EvidenceEnvelope envelope(SystemStateView s,String type,JsonNode payload) {
        return SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"b01"),type,type,SystemStateView.bytes(payload),ref(ScientificReference.Kind.METHOD,"b01-fixture"),s.subject(),T,List.of("engineering fixture"));
    }
    static JsonNode report(String group,Fixture f)throws Exception {var s=system(List.of(f.graph),true,false);return report(manifest(group),s,coverage(s,f));}
    static JsonNode report(RuleManifest m,SystemStateView s,JsonNode coverage)throws Exception {
        var input=envelope(s,"athena:group-source-coverage",coverage);
        var finding=RuleAnalyzers.collector(m,request(s,m),BACKEND).analyze(s,List.of(input,envelope(s,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value()))),Map.of()).getFirst();
        var result=JSON.readTree(finding.measurements().get("payload"));
        var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"b01"),"result","athena:group-identities",SystemStateView.bytes(result),RuleAnalyzers.collector(m,request(s,m),BACKEND).method(),s.subject(),T,List.of());
        var exchange=new EvidenceExchange();var restored=(EvidenceEnvelope)exchange.decodeRecord(exchange.encodeRecord(raw));assertArrayEquals(raw.readPayload(),restored.readPayload());
        var evaluated=RuleAnalyzers.evaluator(m,request(s,m)).analyze(s,List.of(restored),Map.of()).getFirst();
        assertEquals(result.get("assessment").asText(),evaluated.status().name());
        assertEquals(result,JSON.readTree(evaluated.measurements().get("payload")));return result;
    }
    @TestFactory Stream<DynamicTest> constitutionalMatrix()throws Exception {
        var rows=Files.readAllLines(Path.of("software/modules/daedalus/src/test/resources/b01/group-cases.tsv"));
        return rows.stream().filter(x->!x.startsWith("#")).map(line->DynamicTest.dynamicTest(line,()->{var v=line.split("\\|");var r=report(v[0],fixture(v[1]));assertEquals(v[2],r.get("assessment").asText());assertEquals(Integer.parseInt(v[3]),r.get("occurrences").size());}));
    }
    @TestFactory Stream<DynamicTest> allGroupsStateSymmetryPermutation() {
        return GROUPS.stream().flatMap(group->Stream.of("explicit","multiple","permuted","unknownCharge","incompleteGraph").map(kind->DynamicTest.dynamicTest(group+"/"+kind,()-> {
            var f=fixture(positive(group));var base=report(group,f);int count=base.get("occurrences").size();
            if(kind.equals("explicit")){var r=report(group,explicit(f));assertEquals("SUPPORTED_PRESENT",r.get("assessment").asText());assertEquals(count,r.get("occurrences").size());}
            if(kind.equals("multiple")){var r=report(group,doubled(f));assertEquals("SUPPORTED_PRESENT",r.get("assessment").asText());assertEquals(2*count,r.get("occurrences").size());}
            if(kind.equals("permuted")){var a=new ArrayList<>(f.graph.atoms());var b=new ArrayList<>(f.graph.bonds());Collections.reverse(a);Collections.reverse(b);var r=report(group,new Fixture(new MolecularGraph(a,b,f.graph.properties()),f.hydrogens));assertEquals(normalized(base),normalized(r));}
            if(kind.equals("unknownCharge")||kind.equals("incompleteGraph")){var s=system(List.of(f.graph),true,false);var c=coverage(s,f);if(kind.equals("incompleteGraph"))c.put("completeGraph","UNKNOWN_INCONCLUSIVE");else c.get("atomState").forEach(x->((ObjectNode)x).put("chargeStatus","UNKNOWN_INCONCLUSIVE"));var r=report(manifest(group),s,c);assertEquals("UNKNOWN_INCONCLUSIVE",r.get("assessment").asText());assertTrue(r.get("occurrences").isEmpty());}
        })));
    }
    static JsonNode normalized(JsonNode report){var occurrences=report.get("occurrences").deepCopy();occurrences.forEach(o->((ObjectNode)o).remove("occurrenceId"));return occurrences;}
    @TestFactory Stream<DynamicTest> unknownHNeverBecomesPositiveOrNegative() {
        return Stream.of("METHYL","THIOL","THIOLATE").flatMap(group->Stream.of(true,false).map(positive->DynamicTest.dynamicTest(group+" unknownH positive="+positive,()->{
            var f=fixture(positive?positive(group):group.equals("METHYL")?"cyclohexane":"disulfide");var s=system(List.of(f.graph),true,false);var c=coverage(s,f);
            c.get("atomState").forEach(x->{((ObjectNode)x).put("hydrogenMode","UNKNOWN");((ObjectNode)x).putNull("implicitHydrogenCount");});
            var r=report(manifest(group),s,c);assertEquals("UNKNOWN_INCONCLUSIVE",r.get("assessment").asText());assertTrue(r.get("occurrences").isEmpty());
        })));
    }
    @Test void overlappingIdentitiesAndSymmetryRolesSurvive()throws Exception {
        for(String name:List.of("ester","amide")){var f=fixture(name);var carbonyl=report("CARBONYL",f);var larger=report(name.toUpperCase(),f);var ids=new HashSet<String>();larger.get("occurrences").get(0).get("memberAtomIds").forEach(x->ids.add(x.asText()));carbonyl.get("occurrences").get(0).get("memberAtomIds").forEach(x->assertTrue(ids.contains(x.asText())));}
        var benzene=report("AROMATIC",fixture("benzene"));assertEquals(1,benzene.get("occurrences").size());assertEquals(12,benzene.get("occurrences").get(0).get("roleCorrespondenceAlternatives").size());
        assertEquals(2,report("METHYL",fixture("ethane")).get("occurrences").size(),"B00 same target set has two methyl role assignments, both must survive");
    }
    @Test void unknownAromaticModelIsNotAbsence()throws Exception {var f=fixture("benzene");var s=system(List.of(f.graph),true,false);var c=coverage(s,f);c.get("atomState").forEach(x->((ObjectNode)x).put("aromaticityModel","unknown"));assertEquals("UNKNOWN_INCONCLUSIVE",report(manifest("AROMATIC"),s,c).get("assessment").asText());}
    @Test void rawStateAndContradictoryHRemainUnchanged()throws Exception {var f=fixture("thiol");var s=system(List.of(f.graph),true,false);var c=coverage(s,f);((ObjectNode)c.get("atomState").get("a1")).put("implicitHydrogenCount",0);byte[] before=SystemStateView.bytes(s.snapshot());assertEquals("UNKNOWN_INCONCLUSIVE",report(manifest("THIOL"),s,c).get("assessment").asText());assertArrayEquals(before,SystemStateView.bytes(s.snapshot()));}
    static RuleManifest changedDefinition(RuleManifest original,java.util.function.Consumer<ObjectNode> change)throws Exception {
        var tree=(ObjectNode)node(original);var d=(ObjectNode)JSON.readTree(original.parameters().get("definition").value());change.accept(d);
        ((ObjectNode)tree.get("parameters").get("definition")).put("value",d.toString());
        tree.put("ruleId",d.get("groupId").asText());((ObjectNode)tree.get("negativeCoverage")).put("version",d.get("negativeCoverageVersion").asText());
        return RuleRegistry.decode(SystemStateView.bytes(tree));
    }
    @Test void eighthMotifIsDeclarativeAndFixtureOnly()throws Exception {
        var m=changedDefinition(manifest("CARBONYL"),d->{d.put("groupId","FIXTURE.GROUP.CC");d.put("patternId","FIXTURE.GROUP.CC/pattern");d.put("negativeCoverageVersion","FIXTURE.GROUP.CC/negative/1");d.put("query","[C;+0]-[C;+0]");d.putObject("roles").putArray("bondEnds").add(0).add(1);((ObjectNode)d.get("supportedDomain")).putArray("unsupportedQueries");});
        for(String name:List.of("ethane","methanol")){var f=fixture(name);var state=system(List.of(f.graph),true,false);var r=report(m,state,coverage(state,f));assertEquals(name.equals("ethane")?"SUPPORTED_PRESENT":"ABSENT_FALSE",r.get("assessment").asText());}
        assertEquals(7,RuleRegistry.load(Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/groups-b01")).manifests().size());
    }
    @Test void kekuleAndFusedRepresentationsAreNotConflated()throws Exception {
        var f=fixture("benzene");var atoms=f.graph.atoms().stream().map(a->atom(a.id(),a.element(),0,false,a.coordinates().x(),0,0)).toList();var bonds=new ArrayList<MolecularGraph.Bond>();
        for(int i=0;i<6;i++)bonds.add(bond("a"+i,"a"+((i+1)%6),i%2==0?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE));
        assertEquals("SUPPORTED_PRESENT",report("AROMATIC",new Fixture(new MolecularGraph(atoms,bonds,Map.of()),f.hydrogens)).get("assessment").asText());
        var aa=new ArrayList<MolecularGraph.Atom>();var bb=new ArrayList<MolecularGraph.Bond>();var hh=new TreeMap<String,Integer>();for(int i=0;i<10;i++){aa.add(atom("n"+i,"C",0,true,i,0,0));hh.put("n"+i,i==4||i==5?0:1);}
        for(int[] pair:new int[][]{{0,1},{1,2},{2,3},{3,4},{4,5},{5,0},{5,6},{6,7},{7,8},{8,9},{9,4}})bb.add(bond("n"+pair[0],"n"+pair[1],MolecularGraph.BondOrder.AROMATIC));
        assertEquals("UNSUPPORTED",report("AROMATIC",new Fixture(new MolecularGraph(aa,bb,Map.of()),hh)).get("assessment").asText());
    }
    @TestFactory Stream<DynamicTest> changedCoreChargeIsNonNegative() {
        return GROUPS.stream().map(group->DynamicTest.dynamicTest(group+" changed core charge",()-> {
            var f=fixture(positive(group));var baseline=report(group,f);String member=baseline.get("occurrences").get(0).get("memberAtomIds").get(0).asText();
            var atoms=f.graph.atoms().stream().map(a->a.id().equals(member)?new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),1,0,a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties()):a).toList();
            try {var r=report(group,new Fixture(new MolecularGraph(atoms,f.graph.bonds(),f.graph.properties()),f.hydrogens));assertTrue(Set.of("UNSUPPORTED","UNKNOWN_INCONCLUSIVE").contains(r.get("assessment").asText()),r.toString());}
            catch(MolecularBackendException unavailable){assertTrue(unavailable.getMessage().contains("OCL"));}
        }));
    }
    @Test void alteredReportAndCoverageAreRejected()throws Exception {
        var f=fixture("ester");var state=system(List.of(f.graph),true,false);var m=manifest("ESTER");var r=(ObjectNode)report("ESTER",f);r.put("assessment","ABSENT_FALSE");
        var collector=RuleAnalyzers.collector(m,request(state,m),BACKEND);
        var envelope=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"tamper"),"result","athena:group-identities",SystemStateView.bytes(r),collector.method(),state.subject(),T,List.of());
        assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(m,request(state,m)).analyze(state,List.of(envelope),Map.of()));
        var c=coverage(state,f);((ObjectNode)c.get("stateBinding")).put("stateSha256","0".repeat(64));assertThrows(IllegalArgumentException.class,()->report(m,state,c));
    }
    @Test void failedQueryPreservesDefinitionAndCoverageInJournal()throws Exception {
        var f=fixture("ethane");var state=system(List.of(f.graph),true,false);var bad=changedDefinition(manifest("METHYL"),d->d.put("query","[z2]"));
        var coverage=envelope(state,"athena:group-source-coverage",coverage(state,f));var definition=envelope(state,"athena:group-definition",JSON.readTree(bad.parameters().get("definition").value()));
        var catalog=new EvidenceSnapshotCatalog(temp);var analyzer=RuleAnalyzers.collector(bad,request(state,bad),BACKEND);
        var published=pipeline().run(catalog,Optional.empty(),state,List.of(coverage,definition),Map.of(),List.of(analyzer),ref(ScientificReference.Kind.ACTIVITY,"invalid-query"),T);
        var history=catalog.read(published.catalogSnapshot()).orElseThrow().history();
        assertEquals(coverage,history.envelopes().get(coverage.reference()));assertEquals(definition,history.envelopes().get(definition.reference()));
        assertTrue(history.interpretations().values().stream().anyMatch(i->i.evaluator().equals(analyzer.method())&&i.status()==EvidenceInterpretation.Status.FAILED));
    }
    @Test void overlapAndContradictionCoexistInExistingJournal()throws Exception {
        var f=fixture("ester");var state=system(List.of(f.graph),true,false);var catalog=new EvidenceSnapshotCatalog(temp);Optional<EvidenceAdmission.Pin> parent=Optional.empty();
        var stored=new ArrayList<EvidenceEnvelope>();
        for(String group:List.of("ESTER","CARBONYL")) {
            var m=manifest(group);var report=report(group,f);var method=RuleAnalyzers.collector(m,request(state,m),BACKEND).method();
            var raw=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"overlap-"+group),"result","athena:group-identities",SystemStateView.bytes(report),method,state.subject(),T,List.of());stored.add(raw);
            var p=pipeline().run(catalog,parent,state,List.of(raw),Map.of(),List.of(RuleAnalyzers.evaluator(m,request(state,m))),ref(ScientificReference.Kind.ACTIVITY,"publish-"+group),T);parent=Optional.of(p.catalogSnapshot());
        }
        var history=catalog.read(parent.orElseThrow()).orElseThrow().history();for(var raw:stored)assertArrayEquals(raw.readPayload(),history.envelopes().get(raw.reference()).readPayload());
        assertEquals(2,history.interpretations().values().stream().filter(i->i.evaluator().namespace().equals("athena.groups")&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT).count());
    }
    @Test void resonanceDrawingPreservesWhichOxygenIsDoubleBonded()throws Exception {
        var f=fixture("carboxylate");var atoms=f.graph.atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.id().equals("a2")?-1:0,0,a.aromatic(),a.stereochemistry(),a.coordinates(),a.properties())).toList();
        var bonds=List.of(bond("a0","a1",MolecularGraph.BondOrder.SINGLE),bond("a1","a2",MolecularGraph.BondOrder.SINGLE),bond("a1","a3",MolecularGraph.BondOrder.DOUBLE));
        var first=report("CARBONYL",f);var second=report("CARBONYL",new Fixture(new MolecularGraph(atoms,bonds,Map.of()),f.hydrogens));
        assertEquals(List.of("a1","a2"),JSON.convertValue(first.get("occurrences").get(0).get("memberAtomIds"),List.class));
        assertEquals(List.of("a1","a3"),JSON.convertValue(second.get("occurrences").get(0).get("memberAtomIds"),List.class));
    }
    @Test void conflictingCoverageIsPreservedWithoutChoosingOne()throws Exception {
        var f=fixture("thiol");var state=system(List.of(f.graph),true,false);var m=manifest("THIOL");var good=coverage(state,f);var bad=good.deepCopy();((ObjectNode)bad.get("atomState").get("a1")).put("implicitHydrogenCount",0);
        var inputs=new ArrayList<EvidenceEnvelope>();int index=0;for(var value:List.of(good,bad))inputs.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"conflict-"+index++),"coverage","athena:group-source-coverage",SystemStateView.bytes(value),ref(ScientificReference.Kind.METHOD,"fixture"),state.subject(),T,List.of()));
        inputs.add(envelope(state,"athena:group-definition",JSON.readTree(m.parameters().get("definition").value())));
        var analyzer=RuleAnalyzers.collector(m,request(state,m),BACKEND);var catalog=new EvidenceSnapshotCatalog(temp);
        var published=pipeline().run(catalog,Optional.empty(),state,inputs,Map.of(),List.of(analyzer),ref(ScientificReference.Kind.ACTIVITY,"conflicting-sources"),T);
        var history=catalog.read(published.catalogSnapshot()).orElseThrow().history();for(var input:inputs)assertEquals(input,history.envelopes().get(input.reference()));
        assertTrue(history.interpretations().values().stream().anyMatch(i->i.evaluator().equals(analyzer.method())&&i.status()==EvidenceInterpretation.Status.FAILED));
    }
    @Test void positiveIdentityDoesNotCertifyUncoveredOccurrences()throws Exception {
        var f=fixture("ethane");var state=system(List.of(f.graph),true,false);var c=coverage(state,f);var unknown=(ObjectNode)c.get("atomState").get("a1");unknown.put("hydrogenMode","UNKNOWN");unknown.putNull("implicitHydrogenCount");
        var r=report(manifest("METHYL"),state,c);assertEquals("SUPPORTED_PRESENT",r.get("assessment").asText());assertEquals(1,r.get("occurrences").size());assertFalse(r.get("negativeCoverage").get("REQUIRED_H_STATE").asBoolean());
        assertEquals("UNKNOWN",r.get("sourceCoverage").get("atomState").get("a1").get("hydrogenMode").asText());
    }
    @TestFactory Stream<DynamicTest> irrelevantUnknownHIsPreservedWithoutInventingIt() {
        return Stream.of("CARBONYL","ESTER","AMIDE","AROMATIC").map(group->DynamicTest.dynamicTest(group+" H not required",()-> {
            var f=fixture(positive(group));var state=system(List.of(f.graph),true,false);var c=coverage(state,f);c.get("atomState").forEach(x->{((ObjectNode)x).put("hydrogenMode","UNKNOWN");((ObjectNode)x).putNull("implicitHydrogenCount");});
            var r=report(manifest(group),state,c);assertEquals("SUPPORTED_PRESENT",r.get("assessment").asText());assertEquals(c,r.get("sourceCoverage"));
        }));
    }
    public static void main(String[] args)throws Exception {
        var rows=new ArrayList<Object>();for(String group:GROUPS){var f=fixture(positive(group));rows.add(report(group,f));rows.add(report(group,explicit(f)));rows.add(report(group,doubled(f)));}
        Files.write(Path.of(args[0]),SystemStateView.bytes(rows));
        if(args.length>1) {
            var catalog=new EvidenceSnapshotCatalog(Path.of(args[1]));
            for(String group:GROUPS) {
                var f=fixture(positive(group));var state=system(List.of(f.graph),true,false);var m=manifest(group);var run=ref(ScientificReference.Kind.ACTIVITY,"b01-replay-"+group);var collector=RuleAnalyzers.collector(m,request(state,m),BACKEND);
                var coverage=SystemQualificationPipeline.envelope(run,"coverage","athena:group-source-coverage",SystemStateView.bytes(coverage(state,f)),ref(ScientificReference.Kind.METHOD,"fixture"),state.subject(),T,List.of());
                var definition=SystemQualificationPipeline.envelope(run,"definition","athena:group-definition",m.parameters().get("definition").value().getBytes(java.nio.charset.StandardCharsets.UTF_8),ref(ScientificReference.Kind.METHOD,"fixture"),state.subject(),T,List.of());
                var collected=pipeline().run(catalog,Optional.empty(),state,List.of(coverage,definition),Map.of(),List.of(collector),run,T);
                var history=catalog.read(collected.catalogSnapshot()).orElseThrow().history();var finding=history.interpretations().values().stream().filter(i->i.evaluator().equals(collector.method())).findFirst().orElseThrow();
                var raw=SystemQualificationPipeline.envelope(run,"result","athena:group-identities",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),state.subject(),T,List.of());
                var evaluator=RuleAnalyzers.evaluator(m,request(state,m));var evaluated=pipeline().run(catalog,Optional.of(collected.catalogSnapshot()),state,List.of(raw),Map.of(),List.of(evaluator),ref(ScientificReference.Kind.ACTIVITY,"b01-evaluate-"+group),T);
                assertTrue(catalog.read(evaluated.catalogSnapshot()).orElseThrow().history().interpretations().values().stream().anyMatch(i->i.evaluator().equals(evaluator.method())&&i.status()==EvidenceInterpretation.Status.SUPPORTED_PRESENT));
            }
        }
    }
}
