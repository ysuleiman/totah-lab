package totah.lab.daedalus.system;
import org.junit.jupiter.api.*;
import java.util.*;
import java.nio.file.*;
import totah.lab.athena.design.backend.*;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.system.*;
import static totah.lab.athena.design.backend.MolecularValidationService.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;
import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.daedalus.system.AthenaScientificRulesAcceptanceTest.system;

class DimensionalValidationAcceptanceTest {
 static final OclMolecularBackend OLD=new OclMolecularBackend(),NEW=OclMolecularBackend.forChemicalStateValidation();
 static MolecularGraph graph(String name)throws Exception {return ChemicalRoleAcceptanceTest.chemical(name).graph();}
 static MolecularGraph change(MolecularGraph g,int index,int h,String stereo,int charge){var atoms=new ArrayList<>(g.atoms());var a=atoms.get(index);atoms.set(index,new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),charge,h,a.aromatic(),stereo,a.coordinates(),a.properties()));return new MolecularGraph(atoms,g.bonds(),g.properties());}
 static void pass(Result r,Dimension d){assertEquals(SUPPORTED_PRESENT,r.dimensions().get(d).status(),r.toString());}
 @TestFactory java.util.stream.Stream<DynamicTest> chargedNeutralPairs(){return java.util.stream.Stream.of("methylamine","methylammonium","pyridine","pyridinium","acetamide").map(n->DynamicTest.dynamicTest(n,()->{
  var g=graph(n);var before=SystemStateView.bytes(g);var r=NEW.validateDimensions(g,NeutralityPolicy.OBSERVE_ONLY);
  for(var d:List.of(Dimension.TOPOLOGY_VALENCE,Dimension.SUPPLIED_H_STATE,Dimension.STEREOCHEMISTRY,Dimension.OCL_COORDINATE_COMPATIBILITY))pass(r,d);
  assertEquals(NOT_EVALUATED,r.dimensions().get(Dimension.NET_NEUTRALITY).status());
  var neutral=NEW.validateDimensions(g,NeutralityPolicy.REQUIRE_COMPONENT_NEUTRAL);
  assertEquals(r.netFormalCharge()==0?SUPPORTED_PRESENT:ABSENT_FALSE,neutral.dimensions().get(Dimension.NET_NEUTRALITY).status());
  for(var d:Dimension.values())if(d!=Dimension.NET_NEUTRALITY)assertEquals(r.dimensions().get(d),neutral.dimensions().get(d));
  assertEquals(g,NEW.sanitize(g,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)).graph());assertTrue(NEW.validate(g).valid());
  var state=system(List.of(g),true,false);var legacy=new SystemGraphValidation(OLD,OLD,OLD).validate(state);var corrected=new SystemGraphValidation(NEW,NEW,NeutralityPolicy.OBSERVE_ONLY).validate(state);
  for(var c:List.of(SystemGraphCertificate.Capability.GRAPH_TRANSFORMATIONS,SystemGraphCertificate.Capability.INTERACTION_TYPING,SystemGraphCertificate.Capability.HBOND_ANALYSIS)) {
   assertEquals(SystemGraphCertificate.Status.QUALIFIED,corrected.capabilities().get(c).status());
   assertEquals(r.netFormalCharge()==0?SystemGraphCertificate.Status.QUALIFIED:SystemGraphCertificate.Status.FAILED,legacy.capabilities().get(c).status());
  }
  if(r.netFormalCharge()!=0)assertTrue(r.evidence().messages().toString().contains("unbalanced atom charge"));
  assertArrayEquals(before,SystemStateView.bytes(g));
 }));}
 @Test void suppliedHContradictionDoesNotBecomeStereoFailure()throws Exception {var g=change(graph("methylammonium"),1,1,"UNSPECIFIED",1);var r=NEW.validateDimensions(g,NeutralityPolicy.OBSERVE_ONLY);assertEquals(FAILED,r.dimensions().get(Dimension.SUPPLIED_H_STATE).status());pass(r,Dimension.STEREOCHEMISTRY);assertThrows(MolecularBackendException.class,()->OLD.validate(g));assertTrue(NEW.validate(g).valid());assertThrows(MolecularBackendException.class,()->NEW.sanitize(g,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)));}
 @Test void emptyAndMalformedTopologyFail()throws Exception {
  var empty=new MolecularGraph(List.of(),List.of(),Map.of());assertEquals(FAILED,NEW.validateDimensions(empty,NeutralityPolicy.OBSERVE_ONLY).dimensions().get(Dimension.TOPOLOGY_VALENCE).status());
  var g=graph("methylammonium");var bad=new MolecularGraph(g.atoms(),List.of(new MolecularGraph.Bond("bad","missing","a0",MolecularGraph.BondOrder.SINGLE,false,"NONE",Map.of())),Map.of());assertEquals(FAILED,NEW.validateDimensions(bad,NeutralityPolicy.OBSERVE_ONLY).dimensions().get(Dimension.TOPOLOGY_VALENCE).status());
 }
 @Test void chargedOvervalenceFails()throws Exception {var g=graph("methylammonium");var bonds=List.of(new MolecularGraph.Bond("bad","a0","a1",MolecularGraph.BondOrder.TRIPLE,false,"NONE",Map.of()),new MolecularGraph.Bond("extra","a0","x",MolecularGraph.BondOrder.TRIPLE,false,"NONE",Map.of()));var atoms=new ArrayList<>(g.atoms());atoms.add(new MolecularGraph.Atom("x","C",null,0,0,false,"NONE",new MolecularGraph.Coordinates(5,0,0),Map.of()));var bad=new MolecularGraph(atoms,bonds,Map.of());assertEquals(FAILED,NEW.validateDimensions(bad,NeutralityPolicy.OBSERVE_ONLY).dimensions().get(Dimension.TOPOLOGY_VALENCE).status());}
 @Test void unsupportedStereoDoesNotCertifyValence()throws Exception {var g=change(graph("methylammonium"),1,0,"UNSUPPORTED_DESCRIPTOR",1);var r=NEW.validateDimensions(g,NeutralityPolicy.OBSERVE_ONLY);assertEquals(UNSUPPORTED,r.dimensions().get(Dimension.STEREOCHEMISTRY).status());assertEquals(UNKNOWN_INCONCLUSIVE,r.dimensions().get(Dimension.TOPOLOGY_VALENCE).status());}
 @Test void closeCoordinatesRemainIndependentFailure()throws Exception {
  var g=graph("acetamide");var atoms=new ArrayList<>(g.atoms());var n=atoms.get(3);atoms.set(3,new MolecularGraph.Atom(n.id(),n.element(),null,1,0,false,"NONE",atoms.get(0).coordinates(),Map.of()));
  var r=NEW.validateDimensions(new MolecularGraph(atoms,g.bonds(),Map.of()),NeutralityPolicy.OBSERVE_ONLY);
  assertEquals(FAILED,r.dimensions().get(Dimension.OCL_COORDINATE_COMPATIBILITY).status());
  assertTrue(r.evidence().messages().toString().contains("too close"));
 }
 @Test void graphMutationAndMissingCoordinateConsumerComparison()throws Exception {
  var g=graph("methylamine");var atoms=g.atoms().stream().map(a->new MolecularGraph.Atom(a.id(),a.element(),a.isotope(),a.formalCharge(),a.explicitHydrogens(),a.aromatic(),a.stereochemistry(),null,a.properties())).toList();
  var missing=new MolecularGraph(atoms,g.bonds(),g.properties());var policy=new MolecularSanitizer.SanitizationPolicy(Set.of(),true);
  assertEquals(OLD.sanitize(missing,policy).graph(),NEW.sanitize(missing,policy).graph());
  assertEquals(UNKNOWN_INCONCLUSIVE,NEW.validateDimensions(missing,NeutralityPolicy.OBSERVE_ONLY).dimensions().get(Dimension.OCL_COORDINATE_COMPATIBILITY).status());
 }
 @Test void chargedTetrahedralStereoCannotPassByCatchingCharge()throws Exception {
  var g=OLD.decodeStructure("SMILES","C[N@+](CC)(CCC)CCCC");
  var r=NEW.validateDimensions(g,NeutralityPolicy.OBSERVE_ONLY);
  assertNotEquals(SUPPORTED_PRESENT,r.dimensions().get(Dimension.STEREOCHEMISTRY).status());
  assertThrows(MolecularBackendException.class,()->NEW.validate(g));
 }
 @Test void correctedCertificatesBindPolicyAndPreserveInput()throws Exception {
  var state=system(List.of(graph("methylammonium")),true,false);
  var directory=Files.createTempDirectory("athena-dimensional-catalog-");
  var validator=new SystemGraphValidation(NEW,NEW,NeutralityPolicy.REQUIRE_COMPONENT_NEUTRAL);
  var catalog=new totah.lab.mnemosyne.EvidenceSnapshotCatalog(directory);
  var published=new SystemQualificationPipeline(validator).run(catalog,Optional.empty(),state,List.of(),Map.of(),List.of(),AthenaScientificRulesAcceptanceTest.ref(totah.lab.mnemosyne.ScientificReference.Kind.ACTIVITY,"dimensional-test"),AthenaScientificRulesAcceptanceTest.T);
  assertTrue(published.certificate().analyzers().contains(validator.methodReference()));
  assertTrue(published.certificate().checks().stream().anyMatch(c->c.dimension().equals("NET_NEUTRALITY")&&c.status()==SystemGraphCertificate.Status.FAILED));
  assertEquals(SystemGraphCertificate.Status.QUALIFIED,published.certificate().capabilities().get(SystemGraphCertificate.Capability.GRAPH_TRANSFORMATIONS).status());
  assertFalse(catalog.read(published.catalogSnapshot()).orElseThrow().history().envelopes().isEmpty());
 }
 @Test void policyAndLegacyMethodIdentitiesRemainSeparate(){assertEquals("system-graph-validation/1",new SystemGraphValidation(OLD,OLD,OLD).methodReference().id());assertNotEquals(new SystemGraphValidation(NEW,NEW,NeutralityPolicy.OBSERVE_ONLY).methodReference(),new SystemGraphValidation(NEW,NEW,NeutralityPolicy.REQUIRE_COMPONENT_NEUTRAL).methodReference());}
 public static void main(String[] args)throws Exception {var out=new TreeMap<String,Object>();for(var n:List.of("methylamine","methylammonium","pyridine","pyridinium","acetamide"))out.put(n,NEW.validateDimensions(graph(n),NeutralityPolicy.OBSERVE_ONLY));Files.write(Path.of(args[0]),SystemStateView.bytes(out));}
}
