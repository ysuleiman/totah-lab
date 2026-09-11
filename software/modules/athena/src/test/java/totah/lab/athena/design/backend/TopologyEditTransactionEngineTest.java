package totah.lab.athena.design.backend;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TopologyEditTransactionEngineTest {
 @Test void replacementAndReplayPreserveProtectedStereoAndCharge() throws Exception {
  CanonicalIdentityService ids=g->new CanonicalIdentityService.Result(g.atoms().stream().map(MolecularGraph.Atom::id).sorted().toList()+"/"+g.bonds().size(),null);
  var engine=new TopologyEditTransactionEngine(ids);var p=parent();var f=ring("x","y","z");
  var e=new TopologyEdit("ring",TopologyEdit.Type.RING_REPLACEMENT,Set.of("a","b","c"),f,List.of(at("L","x","lx"),at("R","z","rz")),Set.of(),List.of(),Map.of("a","x","b","y","c","z"),TopologyEdit.StereoDisposition.EXPLICITLY_REPLACED);
  var auth=auth(TopologyEdit.Type.RING_REPLACEMENT,Set.of("a","b","c"));var r=engine.apply(p,e,auth);
  assertEquals(1,r.product().atom("L").orElseThrow().formalCharge());assertEquals("R",r.product().atom("R").orElseThrow().stereochemistry());
  assertEquals(r.receipt(),engine.replay(p,e,auth,r.receipt()).receipt());assertEquals("x",r.receipt().parentToChildAtomLineage().get("a"));
 }
 @Test void linkerClosureOpeningAndScaffoldReplacementAreBounded() throws Exception {
  CanonicalIdentityService ids=g->new CanonicalIdentityService.Result(g.atoms().stream().map(MolecularGraph.Atom::id).sorted().toList()+"/"+g.bonds().size(),null);var engine=new TopologyEditTransactionEngine(ids);var p=parent();
  var linker=new TopologyEdit("link",TopologyEdit.Type.LINKER_REPLACEMENT,Set.of("m"),new MolecularGraph(List.of(atom("q","N",0,"UNSPECIFIED")),List.of(),Map.of()),List.of(at("L","q","lq"),at("R","q","rq")),Set.of(),List.of(),Map.of("m","q"),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  assertNotNull(engine.apply(p,linker,auth(TopologyEdit.Type.LINKER_REPLACEMENT,Set.of("m"))).product());
  var close=new TopologyEdit("close",TopologyEdit.Type.BOUNDED_RING_CLOSURE,Set.of(),null,List.of(),Set.of(),List.of(new TopologyEdit.Closure("L","R",MolecularGraph.BondOrder.SINGLE,"closeBond")),Map.of(),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  var closed=engine.apply(p,close,auth(TopologyEdit.Type.BOUNDED_RING_CLOSURE,Set.of("L","R")));assertTrue(closed.product().bond("closeBond").isPresent());
  var open=new TopologyEdit("open",TopologyEdit.Type.BOUNDED_RING_OPENING,Set.of(),null,List.of(),Set.of("ab"),List.of(),Map.of(),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  assertTrue(engine.apply(p,open,new TopologyEditTransactionEngine.Authorization(Set.of(TopologyEdit.Type.BOUNDED_RING_OPENING),Set.of(),Set.of("L","R"),Set.of())).product().bond("ab").isEmpty());
  var scaffold=new TopologyEdit("core",TopologyEdit.Type.SCAFFOLD_CORE_REPLACEMENT,Set.of("a","b","c"),ring("x","y","z"),List.of(at("L","x","lx"),at("R","z","rz")),Set.of(),List.of(),Map.of("a","x"),TopologyEdit.StereoDisposition.ENUMERATION_REQUIRED);
  assertNotNull(engine.apply(p,scaffold,auth(TopologyEdit.Type.SCAFFOLD_CORE_REPLACEMENT,Set.of("a","b","c"))).receipt());
 }
 @Test void rejectsUnmappedReplacementNonRingOpeningAndOutOfBoundsClosure() throws Exception {
  CanonicalIdentityService ids=g->new CanonicalIdentityService.Result(g.atoms().size()+"/"+g.bonds().size(),null);var engine=new TopologyEditTransactionEngine(ids);var p=parent();
  var unmapped=new TopologyEdit("bad",TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,Set.of("m"),new MolecularGraph(List.of(atom("q","C",0,"UNSPECIFIED")),List.of(),Map.of()),List.of(),Set.of(),List.of(),Map.of(),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  assertThrows(IllegalArgumentException.class,()->engine.apply(p,unmapped,auth(TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,Set.of("m"))));
  var openBridge=new TopologyEdit("bridge",TopologyEdit.Type.BOUNDED_RING_OPENING,Set.of(),null,List.of(),Set.of("Lt"),List.of(),Map.of(),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  assertThrows(IllegalArgumentException.class,()->engine.apply(p,openBridge,new TopologyEditTransactionEngine.Authorization(Set.of(TopologyEdit.Type.BOUNDED_RING_OPENING),Set.of(),Set.of("L","R"),Set.of())));
  var duplicateClosure=new TopologyEdit("dup",TopologyEdit.Type.BOUNDED_RING_CLOSURE,Set.of(),null,List.of(),Set.of(),List.of(new TopologyEdit.Closure("L","m",MolecularGraph.BondOrder.SINGLE,"new")),Map.of(),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  assertThrows(IllegalArgumentException.class,()->engine.apply(p,duplicateClosure,auth(TopologyEdit.Type.BOUNDED_RING_CLOSURE,Set.of("L","m"))));
 }
 @Test void indexedReplacementPreservesUnaffectedAromaticStereoAndCharge() throws Exception {
  CanonicalIdentityService ids=g->new CanonicalIdentityService.Result(g.atoms().stream().map(MolecularGraph.Atom::id).sorted().toList()+"/"+g.bonds().size(),null);
  var aromaticParent=new MolecularGraph(List.of(
    new MolecularGraph.Atom("ar1","C",null,0,0,true,"UNSPECIFIED",null,Map.of()),
    new MolecularGraph.Atom("ar2","N",null,0,0,true,"UNSPECIFIED",null,Map.of()),
    atom("side","C",0,"UNSPECIFIED"),atom("stereo","C",1,"R")),
    List.of(new MolecularGraph.Bond("ar","ar1","ar2",MolecularGraph.BondOrder.AROMATIC,true,"UNSPECIFIED",Map.of()),
      b("link","ar2","side"),b("tail","side","stereo")),Map.of());
  var replacement=new MolecularGraph(List.of(atom("oxygen","O",-1,"UNSPECIFIED")),List.of(),Map.of());
  var edit=new TopologyEdit("indexed",TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,Set.of("side"),replacement,
    List.of(new TopologyEdit.Attachment("ar2","oxygen",MolecularGraph.BondOrder.SINGLE,"new-link"),
      new TopologyEdit.Attachment("stereo","oxygen",MolecularGraph.BondOrder.SINGLE,"new-tail")),Set.of(),List.of(),
    Map.of("side","oxygen"),TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);
  var authorization=new TopologyEditTransactionEngine.Authorization(Set.of(TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT),
    Set.of("side"),Set.of("ar1","ar2","stereo"),Set.of("ar"));
  var result=new TopologyEditTransactionEngine(ids).apply(aromaticParent,edit,authorization);
  assertTrue(result.product().bond("ar").orElseThrow().aromatic());
  assertEquals(MolecularGraph.BondOrder.AROMATIC,result.product().bond("ar").orElseThrow().order());
  assertEquals("R",result.product().atom("stereo").orElseThrow().stereochemistry());
  assertEquals(1,result.product().atom("stereo").orElseThrow().formalCharge());
  assertEquals(-1,result.product().atom("oxygen").orElseThrow().formalCharge());
  assertEquals("oxygen",result.receipt().parentToChildAtomLineage().get("side"));
 }
 @Test void lineageRejectsMissingSourceMissingTargetAndDuplicateTarget() {
  var engine=engine();var p=parent();
  assertLineageRejected(engine,p,Map.of("missing","x"),"source missing");
  assertLineageRejected(engine,p,Map.of("m","missing"),"target missing");
  assertLineageRejected(engine,p,replacement(Map.of("m","x","t","x"),Set.of("m","t")),Set.of("m","t"),"multiple lineage sources");
 }
 @Test void lineageRejectsPreservedConflictAndOutOfDomainMappings() {
  var engine=engine();var p=parent();
  assertLineageRejected(engine,p,Map.of("L","L"),"preserved identity");
  assertLineageRejected(engine,p,Map.of("t","x"),"outside replaced region");
  assertLineageRejected(engine,p,replacement(Map.of("m","L"),Set.of("m")),Set.of("m"),"outside replacement fragment");
 }
 @Test void validLineageIsImmutableAndDeterministic() throws Exception {
  var engine=engine();var p=parent();var edit=replacement(Map.of("m","x"));var authorization=auth(TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,Set.of("m"));
  var first=engine.apply(p,edit,authorization);var second=engine.apply(p,edit,authorization);
  assertEquals(first.receipt(),second.receipt());
  assertEquals("x",first.receipt().parentToChildAtomLineage().get("m"));
  assertThrows(UnsupportedOperationException.class,()->first.receipt().parentToChildAtomLineage().put("m","bad"));
 }
 private static TopologyEditTransactionEngine engine(){return new TopologyEditTransactionEngine(g->new CanonicalIdentityService.Result(g.atoms().stream().map(MolecularGraph.Atom::id).sorted().toList()+"/"+g.bonds().size(),null));}
 private static TopologyEdit replacement(Map<String,String>lineage){return replacement(lineage,Set.of("m"));}
 private static TopologyEdit replacement(Map<String,String>lineage,Set<String>removed){return new TopologyEdit("lineage",TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,removed,new MolecularGraph(List.of(atom("x","C",0,"UNSPECIFIED")),List.of(),Map.of()),List.of(at("L","x","lx"),at("R","x","rx")),Set.of(),List.of(),lineage,TopologyEdit.StereoDisposition.PRESERVE_UNAFFECTED);}
 private static void assertLineageRejected(TopologyEditTransactionEngine engine,MolecularGraph parent,Map<String,String>lineage,String message){assertLineageRejected(engine,parent,replacement(lineage),Set.of("m","t"),message);}
 private static void assertLineageRejected(TopologyEditTransactionEngine engine,MolecularGraph parent,TopologyEdit edit,Set<String>permitted,String message){try{engine.apply(parent,edit,auth(TopologyEdit.Type.INDEXED_SUBGRAPH_REPLACEMENT,permitted));fail("expected rejection");}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains(message),expected.getMessage());}catch(Exception unexpected){fail(unexpected);}}
 private static TopologyEditTransactionEngine.Authorization auth(TopologyEdit.Type t,Set<String>s){return new TopologyEditTransactionEngine.Authorization(Set.of(t),s,Set.of("L","R"),Set.of());}
 private static TopologyEdit.Attachment at(String p,String f,String id){return new TopologyEdit.Attachment(p,f,MolecularGraph.BondOrder.SINGLE,id);}
 private static MolecularGraph parent(){return new MolecularGraph(List.of(atom("L","N",1,"S"),atom("R","C",0,"R"),atom("a","C",0,"UNSPECIFIED"),atom("b","C",0,"UNSPECIFIED"),atom("c","C",0,"UNSPECIFIED"),atom("m","C",0,"UNSPECIFIED"),atom("t","C",0,"UNSPECIFIED")),List.of(b("La","L","a"),b("ab","a","b"),b("bc","b","c"),b("ca","c","a"),b("cR","c","R"),b("Lm","L","m"),b("mR","m","R"),b("Lt","L","t")),Map.of());}
 private static MolecularGraph ring(String x,String y,String z){return new MolecularGraph(List.of(atom(x,"C",0,"UNSPECIFIED"),atom(y,"N",0,"UNSPECIFIED"),atom(z,"C",0,"UNSPECIFIED")),List.of(b("xy",x,y),b("yz",y,z),b("zx",z,x)),Map.of());}
 private static MolecularGraph.Atom atom(String id,String e,int q,String s){return new MolecularGraph.Atom(id,e,null,q,0,false,s,null,Map.of());}
 private static MolecularGraph.Bond b(String id,String x,String y){return new MolecularGraph.Bond(id,x,y,MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",Map.of());}
}
