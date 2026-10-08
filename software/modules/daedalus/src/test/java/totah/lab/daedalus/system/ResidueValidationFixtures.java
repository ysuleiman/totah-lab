package totah.lab.daedalus.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.system.*;
import totah.lab.athena.system.rules.*;
import totah.lab.athena.system.rules.research.*;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.structure.*;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.mnemosyne.*;
import java.nio.file.*;
import java.util.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.*;
import static totah.lab.daedalus.system.B01FunctionalGroupAcceptanceTest.*;
import static totah.lab.daedalus.system.S1NitrogenAcceptanceTest.canonical;
import static totah.lab.daedalus.system.S1NitrogenAcceptanceTest.pin;
import static totah.lab.athena.system.rules.research.ResearchGateAcceptanceTest.AT;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Hand-authored Gly-X-Gly source states and synthetic short-lived review. No production source authority. */
final class ResidueValidationFixtures {
    static JsonNode node(Object value){return JSON.valueToTree(value);}
    static final Path RESOURCE=Path.of("software/modules/athena/src/main/resources/totah/lab/athena/system/rules/residue-validation-v1");
    static final String V09="ATHENA.V09.TOP8000_CCTBX_COMPILED_SIX_CLASS",V10="ATHENA.V10.TOP8000_CCTBX_SER_THR_VAL_CHI1";
    static final String[] ROLES={"N","CA","C","O","CB","OG","OG1","CG1","CG2","CD1","ND1","NE2","CD"};
    static final List<String> CATEGORIES=List.of("identityState","atomCorrespondence","connections","stereochemistry","preparation","coherence");
    final SystemStateView state;final MolecularGraph graph;final Map<String,Integer> hydrogens=new TreeMap<>();
    final List<EvidenceEnvelope> inputs=new ArrayList<>();final Map<String,ObjectNode> facts=new LinkedHashMap<>();
    final Map<String,AtomReference> map=new TreeMap<>();final ScientificReference component=ref(ScientificReference.Kind.SUBJECT,"residue-source-component");
    final ObjectNode binding;final EvidenceEnvelope original,protocol,coverageEnvelope;final String identity;final boolean rama;
    RuleManifest manifest;RuleRequest request;EvidenceEnvelope bindingEnvelope;List<EvidenceEnvelope> witnessEnvelopes=new ArrayList<>();S1ResearchFixtures.Qualified main;int sequence;
    ResidueValidationFixtures(String identity,boolean rama,String variant)throws Exception {
        this.identity=identity;this.rama=rama;manifest=RuleRegistry.decode(Files.readAllBytes(RESOURCE.resolve((rama?V09:V10)+".rule.json")));
        var atoms=new ArrayList<MolecularGraph.Atom>();var bonds=new ArrayList<MolecularGraph.Bond>();
        for(int r=1;r<=3;r++) {
            put(atoms,r,"N","N",r==1?1:0,r==1?3:(identity.equals("PRO")&&r==2||variant.equals("next-pro")&&r==3)?0:1,"NONE",new double[]{r*3-6,0,0});
            put(atoms,r,"CA","C",0,(r==2&&!identity.equals("GLY")||variant.equals("next-pro")&&r==3)?1:2,(r==2&&!identity.equals("GLY")||variant.equals("next-pro")&&r==3)?"S":"NONE",new double[]{r*3-5,1,0});
            put(atoms,r,"C","C",0,0,"NONE",new double[]{r*3-4,0,1});put(atoms,r,"O","O",0,0,"NONE",new double[]{r*3-4,-1,1});
            edge(bonds,r+"_N",r+"_CA",false);edge(bonds,r+"_CA",r+"_C",false);edge(bonds,r+"_C",r+"_O",true);if(r>1)edge(bonds,(r-1)+"_C",r+"_N",false);
        }
        put(atoms,3,"OXT","O",-1,0,"NONE",new double[]{7,0,1});edge(bonds,"3_C","3_OXT",false);
        if(!identity.equals("GLY")) {
            put(atoms,2,"CB","C",0,identity.equals("SER")||identity.equals("PRO")?2:1,identity.equals("THR")?"R":"NONE",new double[]{1,0,1});edge(bonds,"2_CA","2_CB",false);
            if(identity.equals("SER")){put(atoms,2,"OG","O",variant.equals("deprotonated")?-1:0,variant.equals("deprotonated")?0:1,"NONE",new double[]{2,0,1});edge(bonds,"2_CB","2_OG",false);}
            if(identity.equals("THR")){put(atoms,2,"OG1","O",0,1,"NONE",new double[]{2,0,1});put(atoms,2,"CG2","C",0,3,"NONE",new double[]{1,-1,1});edge(bonds,"2_CB","2_OG1",false);edge(bonds,"2_CB","2_CG2",false);}
            if(identity.equals("VAL")){put(atoms,2,"CG1","C",0,3,"NONE",new double[]{2,0,1});put(atoms,2,"CG2","C",0,3,"NONE",new double[]{1,-1,1});edge(bonds,"2_CB","2_CG1",false);edge(bonds,"2_CB","2_CG2",false);}
            if(identity.equals("PRO")){put(atoms,2,"CG","C",0,2,"NONE",new double[]{2,1,1});put(atoms,2,"CD","C",0,2,"NONE",new double[]{1,2,1});edge(bonds,"2_CB","2_CG",false);edge(bonds,"2_CG","2_CD",false);edge(bonds,"2_CD","2_N",false);}
        }
        if(variant.equals("next-pro")) {
            put(atoms,3,"CB","C",0,2,"NONE",new double[]{5,1,1});put(atoms,3,"CG","C",0,2,"NONE",new double[]{4,2,1});put(atoms,3,"CD","C",0,2,"NONE",new double[]{3,2,1});
            edge(bonds,"3_CA","3_CB",false);edge(bonds,"3_CB","3_CG",false);edge(bonds,"3_CG","3_CD",false);edge(bonds,"3_CD","3_N",false);
        }
        var coords=Map.of("1_C",new double[]{0,0,1},"1_CA",new double[]{-1,0,1},"2_N",new double[]{0,0,0},"2_CA",new double[]{1,0,0},"2_C",new double[]{1,1,0},"3_N",new double[]{1,1,1});
        for(int i=0;i<atoms.size();i++){var a=atoms.get(i);double[] xyz=coords.get(a.id());if(variant.equals("cis-pro")&&a.id().equals("1_CA"))xyz=new double[]{1,0,1};if(variant.equals("outlier")&&a.id().equals("2_OG"))xyz=new double[]{0,0,1};if(variant.equals("degenerate")&&a.id().equals("2_CB"))xyz=new double[]{2,0,0};if(variant.equals("tiny")&&a.id().equals("2_CB"))xyz=new double[]{1,0,1e-11};if(xyz!=null)atoms.set(i,new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),new MolecularGraph.Coordinates(xyz[0],xyz[1],xyz[2]),a.properties()));}
        graph=new MolecularGraph(atoms,bonds,Map.of("fixture","hand-authored synthetic Gly-X-Gly"));
        var residues=new ArrayList<Residue>();var charges=new TreeMap<AtomReference,Integer>();for(int r=1;r<=3;r++){
            var group=new ArrayList<Atom>();for(var a:atoms)if(a.id().startsWith(r+"_")){String name=a.id().substring(2);var ar=new AtomReference("A",r,' ',name);map.put(a.id(),ar);charges.put(ar,a.formalCharge());var p=a.coordinates();group.add(Atom.builder().name(name).element(Element.fromSymbol(a.element())).autoDockType(a.element()).position(new Point3D(p.x(),p.y(),p.z())).build());}
            residues.add(new Residue("UNTRUSTED_LABEL",r,group));
        }
        var structureBonds=bonds.stream().map(b->new Bond(map.get(b.firstAtomId()),map.get(b.secondAtomId()),totah.lab.gaia.chemistry.BondOrder.valueOf(b.order().name()))).toList();
        state=new SystemStateView(ref(ScientificReference.Kind.CONTEXT,"residue-"+identity+"-"+variant),ResidueGraph.from(new Structure(List.of(new Chain("A",residues)),structureBonds,ConnectivityProvenance.EXPLICIT)),List.of(new SystemStateView.Component(component,graph,List.of(map),List.of("Synthetic exact source map"))),List.of(ref(ScientificReference.Kind.SOURCE,"synthetic-selected-residue")),Set.of(),new FormalChargeAssignments(charges),true,true,List.of("No experimental or production claim"));
        original=add("athena:source-artifact",state.snapshot());protocol=add("athena:source-artifact",Map.of("protocol","synthetic independent exact state/stereo/source protocol, virtual H final ligand; no production authority"));
        coverageEnvelope=add("athena:group-source-coverage",coverage(state,new Fixture(graph,hydrogens)));
        binding=JSON.createObjectNode();binding.put("schema","athena-residue-context-binding/1");binding.put("admissionDefinition","ATHENA.V09_V10.REVIEWED_SELECTED_RESIDUE_CONTEXT/1");binding.set("stateBinding",node(state.binding()));
        binding.set("previous",selection(1,"GLY"));binding.set("central",selection(2,identity));binding.set("next",selection(3,variant.equals("next-pro")?"PRO":"GLY"));
        var links=binding.putArray("peptideLinks");links.add(node(Map.of("from",source("1_C"),"to",source("2_N"),"sourceLocations",locations())));links.add(node(Map.of("from",source("2_C"),"to",source("3_N"),"sourceLocations",locations())));
        binding.put("sourceKind","SOURCE");binding.putArray("preparationReferences");binding.set("modelIdentity",node(Map.of("artifact",pin(original),"selector","model","rawValue","1")));binding.set("frameReference",node(pin(original)));binding.set("conformerSelection",node(Map.of("sourceLocations",locations(),"correlationWitnesses",List.of())));binding.set("sourceCoverage",node(List.of(pin(coverageEnvelope))));binding.put("sourceScope","COMPLETE_ORDINARY");binding.putArray("limitations").add("Synthetic source review fixture only");
        makeFacts();
    }
    private void put(List<MolecularGraph.Atom> atoms,int residue,String role,String element,int charge,int h,String stereo,double[] p){String id=residue+"_"+role;hydrogens.put(id,h);atoms.add(new MolecularGraph.Atom(id,element,null,charge,0,false,stereo,new MolecularGraph.Coordinates(p[0],p[1],p[2]),Map.of("athena.ocl.atomRadicalState/1","NONE")));}
    private static void edge(List<MolecularGraph.Bond> bonds,String a,String b,boolean dbl){bonds.add(new MolecularGraph.Bond("b"+bonds.size(),a,b,dbl?MolecularGraph.BondOrder.DOUBLE:MolecularGraph.BondOrder.SINGLE,false,"NONE",Map.of()));}
    ObjectNode source(String id){var n=JSON.createObjectNode();n.set("component",node(component));n.put("atomId",id);return n;}
    static ResidueId residue(int i){return new ResidueId("A",i,null);}
    List<Map<String,Object>> locations(){return List.of(Map.of("artifact",pin(original),"selector","synthetic exact original source"));}
    ObjectNode selection(int index,String identity) {
        var n=JSON.createObjectNode();n.set("residue",node(residue(index)));n.set("sourceLocations",node(locations()));n.put("candidateIdentity",identity);n.put("stateDescription",index==2?"exact ordinary "+identity+" internal":"exact terminal "+identity+" "+index);
        var members=n.putArray("atoms");map.keySet().stream().filter(id->id.startsWith(index+"_")).sorted(Comparator.comparing(id->canonical(source(id)))).forEach(id->{var a=members.addObject();a.set("source",source(id));a.set("coordinate",node(map.get(id)));});
        var roles=n.putObject("roles");for(var role:ROLES)if(map.containsKey(index+"_"+role))roles.set(role,source(index+"_"+role));else roles.putNull(role);return n;
    }
    List<JsonNode> bondFacts(int residue,boolean incident) {
        var out=new ArrayList<JsonNode>();for(var b:graph.bonds()) {
            boolean a=b.firstAtomId().startsWith(residue+"_"),z=b.secondAtomId().startsWith(residue+"_");if(!(incident?a||z:a&&z))continue;
            var pair=List.of(source(b.firstAtomId()),source(b.secondAtomId())).stream().sorted(Comparator.comparing(S1NitrogenAcceptanceTest::canonical)).toList();
            out.add(node(Map.of("first",pair.get(0),"second",pair.get(1),"order",b.order().name(),"aromatic",b.aromatic(),"sourceLocations",locations())));
        }out.sort(Comparator.comparing(b->canonical(b.path("first"))+"\u0000"+canonical(b.path("second"))));return out;
    }
    void makeFacts() {
        var identityFacts=JSON.createObjectNode();var identities=identityFacts.putArray("residues");var correspond=JSON.createObjectNode();var corr=correspond.putArray("residues");var connections=JSON.createObjectNode();var conn=connections.putArray("residues");var stereo=JSON.createObjectNode();var stereoRows=stereo.putArray("residues");stereo.set("interpretationProtocol",node(pin(protocol)));
        for(int i=1;i<=3;i++) {
            var selected=binding.path(i==1?"previous":i==2?"central":"next");String id=selected.path("candidateIdentity").asText();
            var r=identities.addObject();r.set("residue",selected.get("residue"));r.put("canonicalIdentity",id);r.put("domainStatus","SUPPORTED_PRESENT");r.set("sourceStateDescription",selected.get("stateDescription"));var af=r.putArray("atoms");
            for(var a:selected.path("atoms")) {
                String key=a.path("source").path("atomId").asText();var atom=graph.atom(key).orElseThrow();var f=af.addObject();f.set("source",source(key));f.put("element",atom.element());f.putNull("isotopeMass");f.put("isotopeStatus","KNOWN_UNSPECIFIED");f.put("formalCharge",atom.formalCharge());f.putArray("explicitHydrogens");f.put("nonExplicitHydrogenCount",hydrogens.get(key));f.put("aromatic",false);f.put("aromaticityModel","OCL/2026.7.2");f.put("electronicState","NONE");
            }r.set("bonds",node(bondFacts(i,false)));corr.add(node(Map.of("residue",selected.get("residue"),"atoms",selected.get("atoms"),"roles",selected.get("roles"))));conn.add(node(Map.of("residue",selected.get("residue"),"coverage","COMPLETE_ORDINARY","incidentOrdinaryBonds",bondFacts(i,true),"nonordinary",List.of())));
            var st=stereoRows.addObject();st.set("residue",selected.get("residue"));st.put("alpha",id.equals("GLY")?"ACHIRAL_GLY":"L");st.put("beta",id.equals("THR")?"NATIVE_THR":"NOT_APPLICABLE");var centers=st.putArray("sourceStereo");
            if(!id.equals("GLY")){centers.add(stereo(i+"_CA"));if(id.equals("THR"))centers.add(stereo(i+"_CB"));}
            if(id.equals("VAL"))st.set("valineMethylAttribution",node(Map.of("cg1",source("2_CG1"),"cg2",source("2_CG2"),"protocol",pin(protocol))));else st.putNull("valineMethylAttribution");
        }
        connections.put("previousPresence","PRESENT");connections.put("nextPresence","PRESENT");connections.set("previousResidue",node(residue(1)));connections.set("nextResidue",node(residue(3)));connections.put("nextProline",binding.path("next").path("candidateIdentity").asText().equals("PRO")?"TRUE":"FALSE");connections.put("cyclicPeptide","FALSE");
        facts.put("identityState",identityFacts);facts.put("atomCorrespondence",correspond);facts.put("connections",connections);facts.put("stereochemistry",stereo);
        facts.put("preparation",(ObjectNode)node(Map.of("sourceKind","SOURCE","preparationReferences",List.of(),"originalSources",List.of(pin(original)),"resultState",state.binding())));
        facts.put("coherence",(ObjectNode)node(Map.of("stateBinding",state.binding(),"modelIdentity",binding.get("modelIdentity"),"frameReference",pin(original),"selectedAtoms",map.values().stream().sorted().toList(),"sourceLocations",locations(),"alternateEnsembleReferences",List.of(),"correlation","EXPLICIT_SINGLE_CONFORMER")));
    }
    ObjectNode stereo(String id){var neighbors=new TreeSet<String>();for(var b:graph.bonds())if(b.firstAtomId().equals(id)||b.secondAtomId().equals(id))neighbors.add(b.firstAtomId().equals(id)?b.secondAtomId():b.firstAtomId());return (ObjectNode)node(Map.of("center",source(id),"orderedLigands",neighbors.stream().map(this::source).toList(),"nonExplicitHydrogenLigand",true,"sourceToken",graph.atom(id).orElseThrow().stereochemistry(),"sourceLocations",locations()));}
    EvidenceEnvelope add(String type,Object value){return add(type,SystemStateView.bytes(value),ref(ScientificReference.Kind.METHOD,"residue-fixture"));}
    EvidenceEnvelope add(String type,byte[] bytes,ScientificReference method){var e=SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"residue-fixture"),"input-"+sequence++,type,bytes,method,state.subject(),AT,List.of("Synthetic engineering only; no production authority"));inputs.add(e);return e;}
    void bind()throws Exception {
        bindingEnvelope=add("athena:residue-context-binding",binding);for(var entry:facts.entrySet())witness(entry.getKey(),entry.getValue());
        for(var spec:JSON.readTree(Files.readAllBytes(RESOURCE.resolve("SOURCES.json"))))add("athena:source-artifact",Files.readAllBytes(RESOURCE.resolve(spec.path("file").asText())),ref(ScientificReference.Kind.METHOD,"pinned-reference"));
        add("athena:source-artifact",Files.readAllBytes(RESOURCE.resolve("SOURCES.json")),ref(ScientificReference.Kind.METHOD,"pinned-reference"));
        var tuples=new LinkedHashMap<String,List<AtomReference>>();if(rama){tuples.put("PHI",refs("1_C","2_N","2_CA","2_C"));tuples.put("PSI",refs("2_N","2_CA","2_C","3_N"));if(identity.equals("PRO"))tuples.put("OMEGA_IN",refs("1_CA","1_C","2_N","2_CA"));}else tuples.put("CHI1",refs("2_N","2_CA","2_CB",identity.equals("SER")?"2_OG":identity.equals("THR")?"2_OG1":"2_CG1"));
        var atoms=tuples.values().stream().flatMap(Collection::stream).distinct().sorted().toList();request=new RuleRequest(state.binding(),manifest.key(),RuleRegistry.digest(manifest),atoms,List.of(residue(2)),List.of(),0.1,0,10000,10000);
        var plan=JSON.createObjectNode();plan.put("schema","athena-continuous-geometry-plan/1");plan.set("stateBinding",node(state.binding()));plan.put("coordinateUnit","ANGSTROM");plan.set("coordinateSourceReferences",node(state.sources()));plan.putArray("groups");var ops=plan.putArray("operations");tuples.forEach((name,tuple)->ops.add(node(Map.of("id",name,"kind","DIHEDRAL","atoms",tuple))));plan.putNull("radiusAssignmentReference");plan.set("sourceReferences",node(state.sources()));plan.putArray("limitations").add("Synthetic geometry");var pe=add("athena:continuous-geometry-plan",plan);
        var gm=RuleRegistry.decode(manifest.parameters().get("geometry").value().getBytes(java.nio.charset.StandardCharsets.UTF_8));var gr=new RuleRequest(state.binding(),gm.key(),RuleRegistry.digest(gm),atoms,List.of(),List.of(),0.1,0,10000,10000);var collector=RuleAnalyzers.collector(gm,gr);var raw=collector.analyze(state,List.of(pe),Map.of()).getFirst();add("athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method());
    }
    void witness(String category,JsonNode fact)throws Exception {
        var deps=List.of(original,protocol,coverageEnvelope,bindingEnvelope);var values=new TreeMap<String,String>();values.put("proposition","ATHENA.V09_V10.RESIDUE_CONTEXT_SOURCE_FACT/1");values.put("stateBinding",canonical(state.binding()));values.put("contextBinding",canonical(pin(bindingEnvelope)));values.put("category",category);values.put("factValues",canonical(fact));values.put("sourceProtocol",canonical(pin(protocol)));values.put("sourceLocations",canonical(locations()));values.put("preparationReferences",canonical(binding.get("preparationReferences")));
        var method=ref(ScientificReference.Kind.METHOD,"synthetic-residue-source");var i=new EvidenceInterpretation(ref(ScientificReference.Kind.EVIDENCE_INTERPRETATION,"residue-"+category+"-"+sequence),deps.stream().map(e->new EvidenceInterpretation.Input(e.reference(),e.payloadSha256())).toList(),method,Map.of(),List.of(state.subject()),SUPPORTED_PRESENT,values,List.of("Independent hand-authored synthetic exact selected state"),List.of("Not production source evidence"),Optional.empty(),AT);
        witnessEnvelopes.add(add("athena:event-source",new EvidenceExchange().encodeRecord(i),method));
    }
    List<AtomReference> refs(String... ids){return Arrays.stream(ids).map(map::get).toList();}
    void qualify(Path temp,boolean source)throws Exception {
        main=qualify(manifest,state,request.atoms(),request.first(),temp,"rv-main",Map.of());manifest=main.manifest();request=main.request();inputs.addAll(main.artifacts());
        if(source){var src=new ArrayList<RuleManifest.Source>();var bytes=new HashMap<String,byte[]>();for(var e:java.util.stream.Stream.concat(witnessEnvelopes.stream(),java.util.stream.Stream.of(original,protocol,coverageEnvelope,bindingEnvelope)).toList()){src.add(new RuleManifest.Source("sha256:"+e.payloadSha256(),e.payloadSha256(),"Exact synthetic source review only"));bytes.put(e.payloadSha256(),e.readPayload());}
            var sm=new RuleManifest("athena-rule/2","ATHENA.V09_V10.RESIDUE_CONTEXT_SOURCE_FACT","1.0.0","SYNTHETIC_RESIDUE_SOURCE",manifest.family(),manifest.tier(),"fixture.residue-source","1",SystemGraphCertificate.Status.NOT_EVALUATED,false,List.of(),manifest.requiredChemistry(),List.of(),manifest.measurementsProduced(),manifest.classificationStates(),manifest.parameters(),src,List.of(),List.of("Not production authority"),manifest.negativeCoverage());
            inputs.addAll(qualify(sm,state,request.atoms(),request.first(),temp,"rv-source",bytes).artifacts());}
    }
    static S1ResearchFixtures.Qualified qualify(RuleManifest base,SystemStateView state,List<AtomReference> atoms,List<ResidueId> selected,Path temp,String name,Map<String,byte[]> sources)throws Exception {
        var f=S1ResearchFixture.create(base,"valid",sources);var m=f.manifest();var request=new RuleRequest(state.binding(),m.key(),RuleRegistry.digest(m),atoms,selected,List.of(),0.1,0,10000,10000);
        var foundation=pipeline().run(new EvidenceSnapshotCatalog(Files.createDirectories(temp.resolve(name))),Optional.empty(),state,List.of(),Map.of(),List.of(),ref(ScientificReference.Kind.ACTIVITY,name),AT);
        var eligibility=f.run();var bytes=new TreeMap<String,byte[]>(f.bytes());
        var implementation=new RuleImplementationQualificationV2("athena-rule-implementation-qualification/2",m.key(),m.research().definitionSha256(),m.research().domain().sha256(),RuleRegistry.digest(m),List.of(f.raw()),List.of(f.raw()),List.of(new RuleImplementationQualification.Check("synthetic-positive",true,f.raw(),"engineering fixture only")),ResearchV2Fixtures.EXECUTOR,f.raw(),AT);
        for(var b:List.of(ResearchDocuments.encode(eligibility),ResearchDocuments.encode(implementation),SystemStateView.bytes(foundation.certificate()),SystemStateView.bytes(state.binding()),SystemStateView.bytes(request)))bytes.put(EvidenceExchange.sha256(b),b);
        ResearchArtifactReader reader=p->{var b=bytes.get(p.sha256());if(b==null)throw new java.io.IOException("missing fixture dependency");return b;};var receipt=RuleQualification.qualify(m,eligibility,implementation,foundation.certificate(),state,request,f.context(),reader,AT);
        var artifacts=new ArrayList<EvidenceEnvelope>();int i=0;for(var b:bytes.values())artifacts.add(S1ResearchFixtures.envelope(state,name,"source"+i++,"athena:source-artifact",b));
        artifacts.add(S1ResearchFixtures.envelope(state,name,"manifest","athena:rule-manifest",ResearchDocuments.encode(m)));artifacts.add(S1ResearchFixtures.envelope(state,name,"context","athena:rule-policy-context",ResearchDocuments.encode(f.context())));artifacts.add(S1ResearchFixtures.envelope(state,name,"receipt","athena:rule-qualification-receipt",ResearchDocuments.encode(receipt)));
        return new S1ResearchFixtures.Qualified(m,request,List.copyOf(artifacts));
    }
    SystemGraphAnalyzer.Finding evaluate()throws Exception {
        var copy=new ArrayList<>(inputs);var collector=RuleAnalyzers.collector(manifest,request);var raw=collector.analyze(state,copy,Map.of()).getFirst();
        copy.add(SystemQualificationPipeline.envelope(ref(ScientificReference.Kind.ACTIVITY,"residue-collected"),"measurement","athena:rule-measurements",raw.measurements().get("payload").getBytes(java.nio.charset.StandardCharsets.UTF_8),collector.method(),state.subject(),AT,List.of("Synthetic qualification")));
        return RuleAnalyzers.evaluator(manifest,request).analyze(state,copy,Map.of()).getFirst();
    }
}
