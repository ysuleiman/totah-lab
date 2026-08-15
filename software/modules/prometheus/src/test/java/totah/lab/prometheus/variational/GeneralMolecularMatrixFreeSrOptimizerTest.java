package totah.lab.prometheus.variational;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;

final class GeneralMolecularMatrixFreeSrOptimizerTest {
    @Test void generalStateFeedsBoundedBlockMatrixFreeSr(){Molecule h=new Molecule("H",List.of(new NuclearCenter(0,"H",new NuclearCharge(1),new CartesianPosition(0,0,0,LengthUnit.BOHR))),new MolecularCharge(0),new ElectronCount(1),new SpinSector(1,0,2));var state=GeneralSlaterJastrowState.cuspInitialized(h);List<QuantumCoordinates> points=List.of(c(.4,0,0),c(.8,.1,0),c(1.2,-.1,.2),c(1.8,.2,-.1));GeneralMolecularSampleSource source=consumer->{for(var point:points)consumer.accept(1.0,point);};var config=new GeneralMolecularMatrixFreeSrOptimizer.Configuration(.01,1e-2,.05,2,100,1e-10,1e-12);var result=new GeneralMolecularMatrixFreeSrOptimizer().oneIteration(state,new GeneralMolecularCoulombHamiltonian(h),source,config);assertEquals("BLOCK_PRECONDITIONED_MATRIX_FREE_SR",result.optimizer());assertEquals(points.size(),result.initialStateEvaluations());assertTrue(result.streamedOperatorPasses()>0);assertTrue(result.relativeTrueResidual()<1e-10);}
    private static QuantumCoordinates c(double x,double y,double z){return new QuantumCoordinates(List.of(new QuantumCoordinates.ParticleCoordinate(0,x,y,z,SpinProjection.ALPHA)));}
}
