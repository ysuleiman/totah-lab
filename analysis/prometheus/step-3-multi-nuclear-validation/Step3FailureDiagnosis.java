import java.util.List;
import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.variational.*;

/** Non-scientific reduced diagnostic; never registered as validation evidence. */
public final class Step3FailureDiagnosis {
    public static void main(String[] args) {
        double a=1.8897261254578281;
        Molecule molecule=new Molecule("step3-failure-diagnostic",List.of(
                new NuclearCenter(0,"O",new NuclearCharge(8),new CartesianPosition(0,0,0,LengthUnit.BOHR)),
                new NuclearCenter(1,"H",new NuclearCharge(1),new CartesianPosition(.95*a,0,0,LengthUnit.BOHR)),
                new NuclearCenter(2,"H",new NuclearCharge(1),new CartesianPosition(-.24587809*a,.91762953*a,0,LengthUnit.BOHR))),
                new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1));
        var state=GeneralSlaterJastrowState.cuspInitialized(molecule);
        var source=new GeneralMolecularImportanceBatches(molecule,512,4.0,101,64);
        long[] valid={0};
        try { source.forEach((weight,coordinates)->{state.evaluateWithLocalEnergy(coordinates,new GeneralMolecularCoulombHamiltonian(molecule));valid[0]++;});System.out.println("STATE_EVALUATION_OK count="+valid[0]); }
        catch(RuntimeException exception){System.out.println("STATE_EVALUATION_FAILED count="+valid[0]+" cause="+exception.getClass().getName()+": "+exception.getMessage());return;}
        try { new GeneralMolecularMatrixFreeSrOptimizer().oneIteration(state,new GeneralMolecularCoulombHamiltonian(molecule),source,new GeneralMolecularMatrixFreeSrOptimizer.Configuration(.02,.01,.10,2,200,1e-10,1e-12));System.out.println("SR_ITERATION_OK"); }
        catch(RuntimeException exception){System.out.println("SR_ITERATION_FAILED cause="+exception.getClass().getName()+": "+exception.getMessage());}
    }
}
