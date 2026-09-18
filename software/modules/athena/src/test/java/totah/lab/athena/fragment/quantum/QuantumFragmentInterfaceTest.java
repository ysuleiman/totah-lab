package totah.lab.athena.fragment.quantum;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class QuantumFragmentInterfaceTest {
    @TempDir Path cache;
    @Test void waterSelectionPreservesSourceIdentityAndExplicitChemistry()throws Exception {
        var environment=M18Fixtures.water(true);var selection=FragmentSelection.select(environment);
        assertEquals(List.of(M18Fixtures.RESIDUE),selection.waterIncluded());assertTrue(selection.directContactResidues().isEmpty());
        var plans=FragmentCalculationPlan.pairs(environment,selection);assertEquals(1,plans.size());var plan=plans.getFirst();
        assertEquals(FragmentCalculationPlan.Channel.LIGAND_WATER,plan.channel());assertTrue(plan.left().quantum().isPresent());assertTrue(plan.right().quantum().isPresent());
        assertEquals(0,plan.left().formalCharge().orElseThrow());assertEquals(3,plan.left().retained().size());
        assertEquals(plan.receiptHash(),FragmentCalculationPlan.pairs(environment,FragmentSelection.select(environment)).getFirst().receiptHash());
        var original=environment.state().ligand().getChains().getFirst().residues().getFirst().getAtoms();
        for(int i=0;i<original.size();i++)assertSame(original.get(i).getPosition(),plan.left().retained().get(i).originalAngstrom());
    }
    @Test void cappingIsIndependentGeometricConstructionWithCorrectValence()throws Exception {
        var water=M18Fixtures.water(true);var receptor=M18Fixtures.ethane();
        var e=M18Fixtures.environment(receptor,water.state().ligand(),false,true,QuantumEnvironment.InteractionClass.dispersion);
        var f=QuantumFragmentBuilder.receptorUnits(e,List.of(M18Fixtures.RESIDUE),true,"methane-cap-control");
        assertTrue(f.quantum().isPresent(),f.unavailableReasons().toString());assertEquals(4,f.retained().size());assertEquals(4,f.deleted().size());assertEquals(1,f.caps().size());
        var cap=f.caps().getFirst();assertEquals(.45,cap.capAngstrom().x(),1e-14);assertEquals(0,cap.capAngstrom().y());assertEquals(0,cap.capAngstrom().z());
        assertEquals(1.09,cap.capAngstrom().distance(cap.retainedOriginalAngstrom()),1e-14);
        assertEquals(QuantumEnvironment.ChargeStatus.ASSIGNED_BY_FROZEN_RULE,f.chargeStatus());assertEquals(5,f.quantum().orElseThrow().system().nuclei().size());
        var moved=M18Fixtures.environment(M18Fixtures.transform(receptor),M18Fixtures.transform(water.state().ligand()),false,true,QuantumEnvironment.InteractionClass.dispersion);
        var other=QuantumFragmentBuilder.receptorUnits(moved,List.of(M18Fixtures.RESIDUE),true,"methane-cap-control");
        assertEquals(new Point3D(2,.45-3,1).distance(other.caps().getFirst().capAngstrom()),0,1e-14);
    }
    @Test void missingFormalChargeNeverUsesPartialChargeOrZero()throws Exception {
        var e=M18Fixtures.water(false);var plan=FragmentCalculationPlan.pairs(e,FragmentSelection.select(e)).getFirst();
        var result=FragmentFeatureService.calculate(plan,cache,x->{throw new AssertionError("Ambiguous input must not run SCF");});
        assertTrue(result.rhfCp().isEmpty());assertTrue(result.pbeCp().isEmpty());assertTrue(result.d3Delta().isEmpty());
        assertEquals(FragmentPhysicalFeatures.Validity.OUT_OF_VALIDATED_DOMAIN,result.validity());
        var json=new ObjectMapper().readTree(FragmentFeatureJson.feature(result));
        assertTrue(json.get("PBE_CP_INTERACTION_ENERGY").isNull());assertFalse(json.get("PBE_AVAILABLE").asBoolean());
        var aggregates=FragmentFeatureService.aggregate(List.of(result));assertTrue(aggregates.stream().allMatch(a->a.sumPbeCp().isEmpty()));
        assertThrows(IllegalArgumentException.class,()->FragmentFeatureService.aggregate(List.of(result,result)));
    }
    @Test void evidenceCannotBeAppliedToAnotherGeometry()throws Exception {
        var original=M18Fixtures.water(true);var moved=M18Fixtures.environment(M18Fixtures.transform(original.state().receptor()),original.state().ligand(),true,true,QuantumEnvironment.InteractionClass.hydrogen_bond);
        assertThrows(IllegalArgumentException.class,()->new QuantumEnvironment(moved.state(),List.of(),moved.classAnnotations(),original.chemistry(),moved.waterResidues(),Set.of(),true,true));
        assertThrows(IllegalArgumentException.class,()->FragmentCalculationPlan.pairs(moved,FragmentSelection.select(original)));
    }
    @Test void mixedLabelsCannotUpgradeSulfurAromaticOrPiPi()throws Exception {
        var original=M18Fixtures.water(true);
        var labels=List.of(new QuantumEnvironment.ClassAnnotation(M18Fixtures.RESIDUE,QuantumEnvironment.InteractionClass.sulfur_aromatic,"Synthetic taxonomy adversary"),
                new QuantumEnvironment.ClassAnnotation(M18Fixtures.RESIDUE,QuantumEnvironment.InteractionClass.hydrogen_bond,"Synthetic taxonomy adversary"));
        var e=new QuantumEnvironment(original.state(),List.of(),labels,original.chemistry(),original.waterResidues(),Set.of(),true,true);
        var plan=FragmentCalculationPlan.pairs(e,FragmentSelection.select(e)).getFirst();
        assertEquals(FragmentPhysicalFeatures.Validity.INSUFFICIENT_EVIDENCE,FragmentPhysicalFeatures.validity(plan));
        assertEquals(2,plan.constituentClasses().size());
    }
    @Test void explicitChargedAmmoniumIsSupportedWithoutRoundingPartialCharges()throws Exception {
        var water=M18Fixtures.water(true);
        var ammonium=M18Fixtures.structure("L",1,"AMMONIUM",List.of(
                M18Fixtures.atom("N",totah.lab.gaia.chemistry.Element.N,0,0,0,1),
                M18Fixtures.atom("H1",totah.lab.gaia.chemistry.Element.H,.6,.6,.6,2),
                M18Fixtures.atom("H2",totah.lab.gaia.chemistry.Element.H,.6,-.6,-.6,3),
                M18Fixtures.atom("H3",totah.lab.gaia.chemistry.Element.H,-.6,.6,-.6,4),
                M18Fixtures.atom("H4",totah.lab.gaia.chemistry.Element.H,-.6,-.6,.6,5)),new int[][]{{0,1},{0,2},{0,3},{0,4}});
        var e=M18Fixtures.environment(water.state().receptor(),ammonium,true,true,QuantumEnvironment.InteractionClass.ionic);
        e=new QuantumEnvironment(e.state(),e.annotations(),e.classAnnotations(),Map.of("receptor",e.chemistry().get("receptor"),"ligand",M18Fixtures.evidence(ammonium,Map.of("N",1))),e.waterResidues(),Set.of(),true,true);
        var fragment=QuantumFragmentBuilder.whole(e,"ligand","ammonium");
        assertTrue(fragment.quantum().isPresent(),fragment.unavailableReasons().toString());
        assertEquals(1,fragment.quantum().orElseThrow().system().molecularCharge());assertEquals(1,fragment.formalCharge().orElseThrow());
    }
    @Test void inconsistentValenceAndUnprovenTopologyFailClosed()throws Exception {
        var water=M18Fixtures.water(true);var ligand=water.state().ligand();
        var incomplete=new totah.lab.gaia.structure.Structure(ligand.getChains());
        var e=M18Fixtures.environment(water.state().receptor(),incomplete,true,true,QuantumEnvironment.InteractionClass.hydrogen_bond);
        var fragment=QuantumFragmentBuilder.whole(e,"ligand","missing-bonds");
        assertTrue(fragment.quantum().isEmpty());assertTrue(fragment.unavailableReasons().stream().anyMatch(x->x.startsWith("INCOMPLETE_OR_UNSUPPORTED_VALENCE")));
    }
    @Test void secondShellDoesNotAutomaticallyEnterPairCalculations()throws Exception {
        var water=M18Fixtures.water(true);
        var first=new totah.lab.gaia.structure.Residue("MODEL",149,List.of(M18Fixtures.atom("C1",totah.lab.gaia.chemistry.Element.C,0,0,0,1)));
        var second=new totah.lab.gaia.structure.Residue("MODEL",150,List.of(M18Fixtures.atom("C2",totah.lab.gaia.chemistry.Element.C,-3,0,0,2)));
        var receptor=new totah.lab.gaia.structure.Structure(List.of(new totah.lab.gaia.structure.Chain("R",List.of(first,second))));
        var e=M18Fixtures.environment(receptor,water.state().ligand(),false,false,QuantumEnvironment.InteractionClass.dispersion);
        var selection=FragmentSelection.select(e);assertEquals(List.of(M18Fixtures.RESIDUE),selection.directContactResidues());
        assertEquals(List.of(new totah.lab.gaia.structure.ResidueId("R",150,null)),selection.secondShellResidues());
        assertEquals(1,FragmentCalculationPlan.pairs(e,selection).size());
        var cluster=FragmentCalculationPlan.localCluster(e,selection,List.of(selection.secondShellResidues().getFirst(),M18Fixtures.RESIDUE));
        assertEquals(2,cluster.right().retained().size());assertTrue(cluster.right().quantum().isEmpty());
        assertThrows(IllegalArgumentException.class,()->FragmentCalculationPlan.localCluster(e,selection,List.of(new totah.lab.gaia.structure.ResidueId("R",999,null))));
    }
}
