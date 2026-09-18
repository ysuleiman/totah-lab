package totah.lab.athena.fragment.quantum;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import static org.junit.jupiter.api.Assertions.*;
/** Tests the existing frozen contract, without weakening it or running SCF. */
class M181ChemistryGateTest {
 @TempDir Path temporary;
 @Test void ruleAssignedInputCannotBeLaunderedIntoFrozenM18()throws Exception {
  var e=M18Fixtures.water(true).chemistry().get("ligand");
  var exception=assertThrows(IllegalArgumentException.class,()->new QuantumEnvironment.ChemistryEvidence(
    QuantumEnvironment.ChargeStatus.ASSIGNED_BY_FROZEN_RULE,e.structureHash(),e.source(),e.protonationAssignment(),e.formalCharges(),true,true,1));
  assertTrue(exception.getMessage().contains("reserved"));
 }
 @Test void neutralCapsConserveVerifiedCharge()throws Exception {
  var water=M18Fixtures.water(true);
  var e=M18Fixtures.environment(M18Fixtures.ethane(),water.state().ligand(),false,true,QuantumEnvironment.InteractionClass.dispersion);
  var f=QuantumFragmentBuilder.receptorUnits(e,List.of(M18Fixtures.RESIDUE),true,"M181_CHARGE_CONTROL");
  assertTrue(f.quantum().isPresent());
  int retained=f.retained().stream().mapToInt(a->a.formalCharge().orElseThrow()).sum();
  int cap=f.caps().stream().mapToInt(a->a.formalCharge()).sum();
  int deleted=f.deleted().stream().mapToInt(a->a.formalCharge().orElseThrow()).sum();
  assertEquals(0,retained+deleted);assertEquals(0,cap);
  assertEquals(retained+cap,f.formalCharge().orElseThrow());
  assertEquals(retained+cap,f.quantum().orElseThrow().system().molecularCharge());
 }
 @Test void incompleteTopologyCannotBecomeAvailableFromChargeAlone()throws Exception {
  var e=M18Fixtures.water(true);var v=e.chemistry().get("ligand");
  var incomplete=new QuantumEnvironment.ChemistryEvidence(v.status(),v.structureHash(),v.source(),v.protonationAssignment(),v.formalCharges(),false,true,1);
  var changed=new QuantumEnvironment(e.state(),e.annotations(),e.classAnnotations(),Map.of("ligand",incomplete,"receptor",e.chemistry().get("receptor")),e.waterResidues(),e.cofactorResidues(),true,true);
  var plan=FragmentCalculationPlan.pairs(changed,FragmentSelection.select(changed)).getFirst();
  assertTrue(plan.left().unavailableReasons().contains("INCOMPLETE_TOPOLOGY_EVIDENCE"));
  var row=FragmentFeatureService.calculate(plan,temporary,x->{throw new AssertionError("SCF entered");});
  assertTrue(row.rhfCp().isEmpty());assertTrue(row.pbeCp().isEmpty());assertTrue(row.d3Delta().isEmpty());
 }
 @Test void partialFormalChargeMapDoesNotDefaultMissingAtomsToZero()throws Exception {
  var e=M18Fixtures.water(true);var v=e.chemistry().get("ligand");
  var missing=new QuantumEnvironment.ChemistryEvidence(v.status(),v.structureHash(),v.source(),v.protonationAssignment(),FormalChargeAssignments.EMPTY,true,true,1);
  var changed=new QuantumEnvironment(e.state(),e.annotations(),e.classAnnotations(),Map.of("ligand",missing,"receptor",e.chemistry().get("receptor")),e.waterResidues(),e.cofactorResidues(),true,true);
  var f=QuantumFragmentBuilder.whole(changed,"ligand","missing");assertTrue(f.quantum().isEmpty());assertTrue(f.formalCharge().isEmpty());
 }
 @Test void all22FrozenPlansRemainFailClosed()throws Exception {
  var method=M18InterfaceProbe.class.getDeclaredMethod("mettl7",String.class,Path.class);method.setAccessible(true);
  for(String system:List.of("7A","7B")) {
   var e=(QuantumEnvironment)method.invoke(null,system,temporary);
   var plans=FragmentCalculationPlan.pairs(e,FragmentSelection.select(e));assertEquals(system.equals("7A")?15:7,plans.size());
   for(var plan:plans) {
    assertTrue(plan.left().quantum().isEmpty());assertTrue(plan.right().quantum().isEmpty());
    var f=FragmentFeatureService.calculate(plan,temporary,x->{throw new AssertionError("SCF entered");});
    assertTrue(f.rhfCp().isEmpty());assertTrue(f.pbeCp().isEmpty());assertTrue(f.d3Delta().isEmpty());
   }
   if(system.equals("7B"))assertTrue(plans.stream().filter(p->p.right().id().equals("residue:A:199:null")).findFirst().orElseThrow().right().unavailableReasons().contains("NO_HEAVY_FRAGMENT_ATOMS"));
  }
 }
}
