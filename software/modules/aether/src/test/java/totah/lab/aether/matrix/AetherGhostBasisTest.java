package totah.lab.aether.matrix;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherGhostBasisTest {
    private static QuantumSystem hydrogen(double x,int charge) {
        return new QuantumSystem(List.of(new NuclearCenter(new Point3D(x,0,0),1),new NuclearCenter(new Point3D(x+1.4,0,0),1)),charge,1);
    }
    @Test void ghostsSupplyOnlyBasisAndRequireExplicitScfEntryPoint() throws Exception {
        var real=hydrogen(0,0);var ghost=GhostBasis.withDonor(real,hydrogen(5,0));
        assertSame(real,ghost.system());assertEquals(2,ghost.system().nuclei().size());assertEquals(4,ghost.functions().size());
        assertEquals(2,OccupiedDensityCalculator.occupation(ghost.system(),ghost.functions().size()).electrons());
        assertEquals(ScientificStatus.SCREENING_ONLY,ghost.status());
        assertEquals(RhfScfResult.Status.UNSUPPORTED_SYSTEM,RhfScfCalculator.solve(real,ghost.functions(),ScfPolicy.DIIS).status());
        var result=RhfScfCalculator.solve(ghost);assertEquals(RhfScfResult.Status.CONVERGED,result.status());
        assertEquals(1/1.4,result.convergedState().orElseThrow().energy().nuclearHartree(),1e-15);
        assertTrue(result.receipt().protocol().contains(ghost.identity()));
    }
    @Test void ghostDonorChargeDoesNotAddElectronsButRealChargeChangesIdentity() throws Exception {
        var real=hydrogen(0,0);var neutral=GhostBasis.withDonor(real,hydrogen(5,0));
        assertEquals(neutral.identity(),GhostBasis.withDonor(real,hydrogen(5,2)).identity());
        assertNotEquals(neutral.identity(),GhostBasis.withDonor(hydrogen(0,2),hydrogen(5,0)).identity());
    }
    @Test void orderedGhostGeometryAndBasisAreBoundAndImmutable() throws Exception {
        var real=hydrogen(0,0);var ghosts=new ArrayList<>(List.of(new GhostCenter(1,new Point3D(5,0,0)),new GhostCenter(8,new Point3D(6,0,0))));
        var basis=GhostBasis.of(real,ghosts);ghosts.clear();assertEquals(2,basis.ghosts().size());assertEquals(8,basis.functions().size());
        assertThrows(UnsupportedOperationException.class,()->basis.functions().clear());
        assertThrows(UnsupportedOperationException.class,()->basis.ghosts().clear());
        assertNotEquals(basis.identity(),GhostBasis.of(real,basis.ghosts().reversed()).identity());
        assertThrows(IllegalArgumentException.class,()->basis.validate(hydrogen(.1,0),basis.functions()));
        assertThrows(IllegalArgumentException.class,()->basis.validate(real,basis.functions().reversed()));
    }
    @Test void invalidOrCoincidentGhostsFailClosed() {
        var real=hydrogen(0,0);var g=new GhostCenter(1,new Point3D(5,0,0));
        assertThrows(IllegalArgumentException.class,()->GhostBasis.of(real,List.of()));
        assertThrows(IllegalArgumentException.class,()->GhostBasis.of(real,List.of(g,g)));
        assertThrows(IllegalArgumentException.class,()->GhostBasis.of(real,List.of(new GhostCenter(1,new Point3D(0,0,0)))));
        assertThrows(IllegalArgumentException.class,()->GhostBasis.of(real,List.of(new GhostCenter(2,new Point3D(5,0,0)))));
        assertThrows(IllegalArgumentException.class,()->new GhostCenter(1,new Point3D(Double.NaN,0,0)));
    }
    @Test void fragmentIdentityAndClosedShellRequirementsFailClosed() {
        var a=new MolecularFragment("a",hydrogen(0,0));
        assertThrows(IllegalArgumentException.class,()->new FragmentPair(a,new MolecularFragment("a",hydrogen(5,0))));
        assertThrows(IllegalArgumentException.class,()->new FragmentPair(a,new MolecularFragment("b",hydrogen(0,0))));
        assertThrows(IllegalArgumentException.class,()->new MolecularFragment("",hydrogen(0,0)));
        assertThrows(IllegalArgumentException.class,()->new MolecularFragment("open",new QuantumSystem(hydrogen(0,0).nuclei(),0,3)));
    }
    @Test void componentSystemContextOrderingAndProtocolMismatchesFailClosed() throws Exception {
        var pair=new FragmentPair(new MolecularFragment("a",hydrogen(0,0)),new MolecularFragment("b",hydrogen(5,0)));
        var result=InteractionEnergyCalculator.calculate(pair);var components=new ArrayList<>(result.components());
        var c=components.get(1);components.set(1,new InteractionEnergyResult.Component(c.role(),c.calculation(),"wrong-context"));
        assertThrows(IllegalArgumentException.class,()->InteractionEnergyCalculator.assemble(pair,components,128));
        assertThrows(IllegalArgumentException.class,()->InteractionEnergyCalculator.assemble(pair,result.components().reversed(),128));
        assertThrows(IllegalArgumentException.class,()->InteractionEnergyCalculator.assemble(pair,result.components(),127));
        var moved=new FragmentPair(pair.a(),new MolecularFragment("b",hydrogen(6,0)));
        assertThrows(IllegalArgumentException.class,()->InteractionEnergyCalculator.assemble(moved,result.components(),128));
        var run=c.calculation();var r=run.receipt();
        var wrongProtocol=new RhfScfRun.Receipt(r.policy(),r.protocol()+";altered",r.systemHash(),r.basisGeometryHash(),r.initialDensityReceiptHash(),
                r.maximumIterations(),r.termination(),r.reason(),r.trajectory(),r.calculationHash(),r.resultHash(),r.receiptHash());
        var forged=new RhfScfRun(run.iterations(),wrongProtocol,run.performanceCounters(),run.plainResult());
        var changed=new ArrayList<>(result.components());changed.set(1,new InteractionEnergyResult.Component(c.role(),forged,c.basisContextIdentity()));
        assertThrows(IllegalArgumentException.class,()->InteractionEnergyCalculator.assemble(pair,changed,128));
    }
}
