package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;

/** Source plumbing qualification. These source assertions deliberately have no scientific authority. */
class S1NitrogenAcceptanceTest {
    static final Path MANIFEST=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/i03-n-sp3-s1-v1/ATHENA.I03.N_SP3.SATURATED_CARBON_ATTACHMENTS.rule.json");
    static String canonical(Object o){return new String(SystemStateView.bytes(o),java.nio.charset.StandardCharsets.UTF_8);}
    static Map<String,Object> pin(EvidenceEnvelope e){return Map.of("reference",e.reference(),"sha256",e.payloadSha256());}
    static class Fixture {
        final SystemStateView state;RuleManifest manifest;final List<EvidenceEnvelope> inputs=new ArrayList<>();final List<AtomReference> selected=new ArrayList<>();final List<EvidenceEnvelope> coverages=new ArrayList<>();int seq;
        Fixture(String variant)throws Exception {this(variant,null);}
        Fixture(String variant,List<B01FunctionalGroupAcceptanceTest.Fixture> supplied)throws Exception {
            manifest=RuleRegistry.decode(Files.readAllBytes(MANIFEST));
            if(supplied==null){var base=ChemicalRoleAcceptanceTest.chemical(variant.equals("tertiary")?"trimethylamine":variant.equals("secondary")?"dimethylamine":"methylamine");if(variant.equals("explicit"))base=explicit(base);supplied=List.of(base);}
            var fs=new ArrayList<B01FunctionalGroupAcceptanceTest.Fixture>();
            for(var base:supplied){var atoms=new ArrayList<MolecularGraph.Atom>();
                for(var a:base.graph().atoms()){var properties=new TreeMap<>(a.properties());if(!variant.equals("missing-none"))properties.put("athena.ocl.atomRadicalState/1","NONE");atoms.add(new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),a.coordinates(),properties));}
                fs.add(new B01FunctionalGroupAcceptanceTest.Fixture(new MolecularGraph(atoms,base.graph().bonds(),base.graph().properties()),base.hydrogens()));}
            var built=system(fs.stream().map(B01FunctionalGroupAcceptanceTest.Fixture::graph).toList(),true,false);
            if(variant.equals("ambiguous")){var cs=new ArrayList<>(built.components());var c=cs.getFirst();cs.set(0,new SystemStateView.Component(c.identity(),c.chemistry(),List.of(c.correspondenceAlternatives().getFirst(),c.correspondenceAlternatives().getFirst()),c.limitations()));built=new SystemStateView(built.identity(),built.graph(),cs,built.sources(),Set.of(),built.charges(),true,true,List.of("synthetic ambiguous correspondence"));}
            state=built;
            for(int index=0;index<fs.size();index++) {var f=fs.get(index);var component=state.components().get(index);
                for(var a:f.graph().atoms())if(a.element().equals("N"))selected.add(component.correspondenceAlternatives().getFirst().get(a.id()));
                var c=coverage(state,f);c.set("componentReference",node(component.identity()));if(variant.equals("missing-h"))c.path("atomState").forEach(v->((ObjectNode)v).put("hydrogenMode","UNKNOWN").putNull("implicitHydrogenCount"));
                var ce=add("athena:group-source-coverage",SystemStateView.bytes(c),ref(ScientificReference.Kind.METHOD,"source-coverage"));coverages.add(ce);
                var sources=JSON.readTree(manifest.parameters().get("sources").value());
                for(var it=sources.fields();it.hasNext();) {var entry=it.next();var role=RuleRegistry.decode(SystemStateView.bytes(entry.getValue()));var collector=RuleAnalyzers.collector(role,B01FunctionalGroupAcceptanceTest.request(state,role),BACKEND);
                    var finding=collector.analyze(state,List.of(ce,envelope(state,"athena:group-definition",JSON.readTree(role.parameters().get("definition").value()))),Map.of()).getFirst();
                    add("athena:group-identities",finding.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method());}
                if(!variant.equals("missing-scope"))scope(ce,variant.equals("unknown-scope")?"UNKNOWN":variant.equals("known-connection")?"KNOWN_NON_ORDINARY_CONNECTION":"COMPLETE_ORDINARY_COVALENT_NO_COORDINATION",variant.equals("wrong-atoms"),variant.equals("wrong-state"));
                if(variant.equals("conflict"))scope(ce,"UNKNOWN",false,false);
            }
            add("athena:source-artifact",Files.readAllBytes(Path.of(manifest.scientificSources().getFirst().locator())),ref(ScientificReference.Kind.METHOD,"protocol-source"));
        }
        EvidenceEnvelope add(String type,byte[] bytes,ScientificReference method) {var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"s1-synthetic"),"input-"+seq++,type,bytes,method,state.subject(),T,List.of("hand-authored engineering fixture; no production scope authority"));inputs.add(e);return e;}
        void scope(EvidenceEnvelope coverage,String connection,boolean wrongAtoms,boolean wrongState)throws Exception {
            var coverageComponent=JSON.readTree(coverage.readPayload()).path("componentReference");var c=state.components().stream().filter(x->JSON.valueToTree(x.identity()).equals(coverageComponent)).findFirst().orElseThrow();var protocol=add("athena:source-artifact","synthetic lossless source protocol; no real preparation assertion".getBytes(java.nio.charset.StandardCharsets.UTF_8),ref(ScientificReference.Kind.METHOD,"scope-protocol"));
            boolean nonordinary=connection.equals("KNOWN_NON_ORDINARY_CONNECTION");
            var original=add("athena:source-artifact",SystemStateView.bytes(nonordinary?Map.of("ordinaryProjection",state.snapshot(),"explicitOriginalConnection",Map.of("identity","source-dative-17","first","component-source-N:a1","second","source-Zn:external","kind","DATIVE")):state.snapshot()),ref(ScientificReference.Kind.METHOD,"original-source"));
            var values=new TreeMap<String,String>();values.put("proposition",nonordinary?"ATHENA.I03.SP3_SOURCE_SCOPE/2":"ATHENA.I03.SP3_SOURCE_SCOPE/1");values.put("stateBinding",wrongState?"{}":canonical(state.binding()));values.put("componentReference",canonical(c.identity()));values.put("atoms",canonical(wrongAtoms?List.of():c.correspondenceAlternatives().getFirst().values().stream().sorted(Comparator.comparing(S1NitrogenAcceptanceTest::canonical)).toList()));values.put("connectionCoverage",connection);values.put("electronicStateCoverage","COMPLETE_EXPLICIT_NONE");values.put("sourceProtocol",canonical(pin(protocol)));
            var method=ref(ScientificReference.Kind.METHOD,"unqualified-synthetic-scope");var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"scope-"+seq),List.of(new EvidenceInterpretation.Input(coverage.reference(),coverage.payloadSha256()),new EvidenceInterpretation.Input(protocol.reference(),protocol.payloadSha256()),new EvidenceInterpretation.Input(original.reference(),original.payloadSha256())),method,Map.of(),List.of(state.subject()),nonordinary?EvidenceInterpretation.Status.UNSUPPORTED:EvidenceInterpretation.Status.SUPPORTED_PRESENT,values,List.of(nonordinary?"Original connection source-dative-17: component-source-N:a1 -> source-Zn:external, explicitly DATIVE; exact original bytes pinned":"synthetic complete assertion"),List.of("not independently qualified"),Optional.empty(),totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT);
            add("athena:event-source",new EvidenceExchange().encodeRecord(i),method);
        }
        RuleRequest request(){return new RuleRequest(state.binding(),manifest.key(),RuleRegistry.digest(manifest),selected,List.of(),List.of(),0.1,0,10000,10000);}
        List<SystemGraphAnalyzer.Finding> result()throws Exception {var before=SystemStateView.bytes(state.snapshot());var out=RuleAnalyzers.evaluator(manifest,request()).analyze(state,inputs,Map.of());assertArrayEquals(before,SystemStateView.bytes(state.snapshot()));return out;}
    }
    @ParameterizedTest @ValueSource(strings={"primary","secondary","tertiary","explicit"})
    void completeRawPatternNeverManufacturesScopeAuthority(String variant)throws Exception {var f=new Fixture(variant);var r=f.result();assertEquals(1,r.size());assertEquals("SP3",r.getFirst().measurements().get("hybridization"),r.toString());assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,r.getFirst().status());}
    @ParameterizedTest @ValueSource(strings={"missing-none","missing-h","missing-scope","unknown-scope","conflict","ambiguous"})
    void unresolvedSourceRemainsUnknownAndIneligible(String variant)throws Exception {var r=new Fixture(variant).result().getFirst();assertEquals("UNKNOWN",r.measurements().get("hybridization"));assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,r.status());}
    @ParameterizedTest @ValueSource(strings={"wrong-atoms","wrong-state"})
    void sourceScopeBindingRejected(String variant)throws Exception {var f=new Fixture(variant);assertThrows(IllegalArgumentException.class,f::result);}
    @Test void duplicatesAndOrderingAreIdempotent()throws Exception {var f=new Fixture("primary");var first=f.result();f.inputs.add(f.inputs.getFirst());Collections.reverse(f.inputs);assertEquals(first,f.result());}
    @Test void selectedProtocolCannotBeReplaced()throws Exception {var f=new Fixture("primary");f.inputs.removeIf(e->e.payloadSha256().equals(f.manifest.scientificSources().getFirst().sha256()));assertThrows(IllegalArgumentException.class,f::result);}
    @Test void qualificationFlagCannotSelfCertify()throws Exception {var m=(ObjectNode)node(new Fixture("primary").manifest);m.put("qualification","QUALIFIED");assertThrows(java.io.IOException.class,()->RuleRegistry.decode(SystemStateView.bytes(m)));}
    @Test void changedSourceIdentityIsRejected()throws Exception {var f=new Fixture("primary");var e=f.inputs.getFirst();var replacement=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"s1-synthetic"),"input-0",e.evidenceType(),"{}".getBytes(),e.method(),f.state.subject(),T,List.of());f.inputs.add(replacement);assertThrows(IllegalArgumentException.class,f::result);}
    @Test void unselectedHistoricalScopeCannotBeConsumed()throws Exception {var f=new Fixture("missing-scope");assertEquals("UNKNOWN",f.result().getFirst().measurements().get("hybridization"));}
    @Test void selectedAtomsAreNotSilentlyDroppedAtBudget()throws Exception {
        var base=ChemicalRoleAcceptanceTest.chemical("methylamine");var f=new Fixture("primary",List.of(base,base));var r=f.request();var limited=new RuleRequest(r.state(),r.manifestKey(),r.manifestSha256(),r.atoms(),r.first(),r.second(),r.radiusAngstrom(),r.maximumHops(),r.maximumNodes(),1);var findings=RuleAnalyzers.evaluator(f.manifest,limited).analyze(f.state,f.inputs,Map.of());assertEquals(2,findings.size());assertEquals("UNKNOWN",findings.get(1).measurements().get("hybridization"));assertTrue(findings.get(1).reasons().toString().contains("budget"));
    }
    @ParameterizedTest @ValueSource(strings={"radical","protonation","coordinate","correspondence"})
    void changedChemicalPreparationOrMappingCannotReplaySourceEvidence(String variant)throws Exception {
        var f=new Fixture("primary");var c=f.state.components().getFirst();var atoms=new ArrayList<>(c.chemistry().atoms());var a=atoms.get(1);var props=new TreeMap<>(a.properties());if(variant.equals("radical"))props.put("athena.ocl.atomRadicalState/1","D");
        atoms.set(1,new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),variant.equals("protonation")?1:a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),variant.equals("coordinate")?new MolecularGraph.Coordinates(99,0,0):a.coordinates(),props));
        var mapping=new TreeMap<>(c.correspondenceAlternatives().getFirst());if(variant.equals("correspondence")){var first=mapping.get("a0");mapping.put("a0",mapping.get("a1"));mapping.put("a1",first);}
        var changed=new SystemStateView(f.state.identity(),f.state.graph(),List.of(new SystemStateView.Component(c.identity(),new MolecularGraph(atoms,c.chemistry().bonds(),c.chemistry().properties()),List.of(mapping),c.limitations())),f.state.sources(),Set.of(),f.state.charges(),true,true,List.of("different immutable source state"));
        var r=f.request();var request=new RuleRequest(changed.binding(),r.manifestKey(),r.manifestSha256(),r.atoms(),r.first(),r.second(),r.radiusAngstrom(),r.maximumHops(),r.maximumNodes(),r.maximumCandidates());assertThrows(IllegalArgumentException.class,()->RuleAnalyzers.evaluator(f.manifest,request).analyze(changed,f.inputs,Map.of()));
    }
    @ParameterizedTest @ValueSource(strings={"aziridine","quinuclidine"})
    void cyclicAndCageGraphsReuseUnchangedAdmissionRoles(String name)throws Exception {
        int size=name.equals("aziridine")?3:8;String edges=name.equals("aziridine")?"0-1 1-2 2-0":"0-1 1-2 2-7 0-3 3-4 4-7 0-5 5-6 6-7";var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();var h=new TreeMap<String,Integer>();
        for(int n=0;n<size;n++){atoms.add(atom("a"+n,n==0?"N":"C",0,false,n,0,0));h.put("a"+n,n==0?(size==3?1:0):n==7?1:2);}
        for(var edge:edges.split(" ")){var endpoints=edge.split("-");bonds.add(bond("a"+endpoints[0],"a"+endpoints[1],MolecularGraph.BondOrder.SINGLE));}
        var f=new Fixture("primary",List.of(new B01FunctionalGroupAcceptanceTest.Fixture(new MolecularGraph(atoms,bonds,Map.of("fixture",name)),h)));assertEquals("SP3",f.result().getFirst().measurements().get("hybridization"));assertEquals(EvidenceInterpretation.Status.NOT_EVALUATED,f.result().getFirst().status(),"raw source assertion cannot manufacture authority");
    }
    public static void main(String[] args)throws Exception {var f=new Fixture("primary");Files.write(Path.of(args[0]),SystemStateView.bytes(f.result()));}
}
