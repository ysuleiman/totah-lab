import java.util.List;
import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.numerics.*;
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
        auditMoments(state,molecule,source);
        reflectOptimizerStatistics(state,molecule,source);
        try { new GeneralMolecularMatrixFreeSrOptimizer().oneIteration(state,new GeneralMolecularCoulombHamiltonian(molecule),source,new GeneralMolecularMatrixFreeSrOptimizer.Configuration(.02,.01,.10,2,200,1e-10,1e-12));System.out.println("SR_ITERATION_OK"); }
        catch(RuntimeException exception){System.out.println("SR_ITERATION_FAILED cause="+exception.getClass().getName()+": "+exception.getMessage());}
    }

    private static void auditMoments(GeneralSlaterJastrowState state,Molecule molecule,GeneralMolecularImportanceBatches source){int p=state.parameters().values().size();double[] rawW={0},rawE={0},onlineW={0},onlineE={0};double[] rawO=new double[p],rawOe=new double[p],mean=new double[p],co=new double[p];double[][] rawOo=new double[p][p],cov=new double[p][p];source.forEach((weight,coordinates)->{var e=state.evaluateWithLocalEnergy(coordinates,new GeneralMolecularCoulombHamiltonian(molecule));double psi=e.derivatives().value().real(),w=weight*psi*psi,local=e.localEnergy().orElseThrow().totalHartree();double[] o=new double[p];for(int i=0;i<p;i++)o[i]=e.derivatives().parameterGradient().derivatives().get(i).real()/psi;rawW[0]+=w;rawE[0]+=w*local;for(int i=0;i<p;i++){rawO[i]+=w*o[i];rawOe[i]+=w*o[i]*local;for(int j=0;j<p;j++)rawOo[i][j]+=w*o[i]*o[j];}double old=onlineW[0],next=old+w,ratio=w/next,energyDelta=local-onlineE[0];double[] delta=new double[p];for(int i=0;i<p;i++)delta[i]=o[i]-mean[i];for(int i=0;i<p;i++){co[i]+=w*old/next*delta[i]*energyDelta;for(int j=0;j<p;j++)cov[i][j]+=w*old/next*delta[i]*delta[j];mean[i]+=ratio*delta[i];}onlineE[0]+=ratio*energyDelta;onlineW[0]=next;});List<FixedPreconditioners.Block> blocks=new java.util.ArrayList<>();double[] rawMean=new double[p],gradient=new double[p];for(int i=0;i<p;i++){rawMean[i]=rawO[i]/rawW[0];gradient[i]=2*(rawOe[i]/rawW[0]-rawMean[i]*(rawE[0]/rawW[0]));}for(int block=0;block<p;block+=2){int j=Math.min(block+1,p-1);double na=rawOo[block][block]/rawW[0]-sq(rawMean[block])+.01,nb=rawOo[block][j]/rawW[0]-rawMean[block]*rawMean[j],nc=rawOo[j][j]/rawW[0]-sq(rawMean[j])+.01;double wa=cov[block][block]/onlineW[0]+.01,wb=cov[block][j]/onlineW[0],wc=cov[j][j]/onlineW[0]+.01;System.out.println("BLOCK "+block+" naive=["+na+","+nb+","+nc+"] det="+(na*nc-nb*nb)+" stable=["+wa+","+wb+","+wc+"] det="+(wa*wc-wb*wb));blocks.add(new FixedPreconditioners.Block(block,j+1,new double[][]{{na,nb},{nb,nc}}));}System.out.println("WEIGHT="+rawW[0]+" ENERGY naive="+(rawE[0]/rawW[0])+" stable="+onlineE[0]);var pre=FixedPreconditioners.blocks(p,blocks);var operator=new StreamingCovarianceOperator(p,consumer->source.forEach((weight,coordinates)->{var e=state.evaluateWithLocalEnergy(coordinates,new GeneralMolecularCoulombHamiltonian(molecule));double psi=e.derivatives().value().real();double[] centered=new double[p];for(int i=0;i<p;i++)centered[i]=e.derivatives().parameterGradient().derivatives().get(i).real()/psi-rawMean[i];consumer.accept(weight*psi*psi/rawW[0],centered);}),.01);double[] rhs=new double[p];for(int i=0;i<p;i++)rhs[i]=-gradient[i];double[] z=pre.apply(rhs),ap=operator.apply(z);double alpha=dot(rhs,z)/dot(z,ap);double[] x=new double[p];for(int i=0;i<p;i++)x[i]=alpha*z[i];double[] ax=operator.apply(x),r=new double[p];for(int i=0;i<p;i++)r[i]=rhs[i]-ax[i];double[] rz=pre.apply(r);System.out.println("PCG1 initialRMz="+dot(rhs,z)+" curvature="+dot(z,ap)+" alpha="+alpha+" rhs="+java.util.Arrays.toString(rhs)+" trueResidual="+java.util.Arrays.toString(r)+" preconditioned="+java.util.Arrays.toString(rz)+" rMz="+dot(r,rz));}
    private static double dot(double[] a,double[] b){double s=0;for(int i=0;i<a.length;i++)s+=a[i]*b[i];return s;}
    private static double sq(double x){return x*x;}
    private static void reflectOptimizerStatistics(GeneralSlaterJastrowState state,Molecule molecule,GeneralMolecularImportanceBatches source){try{var method=GeneralMolecularMatrixFreeSrOptimizer.class.getDeclaredMethod("statistics",GeneralSlaterJastrowState.class,GeneralMolecularCoulombHamiltonian.class,GeneralMolecularSampleSource.class);method.setAccessible(true);Object statistics=method.invoke(null,state,new GeneralMolecularCoulombHamiltonian(molecule),source);var covarianceMethod=statistics.getClass().getDeclaredMethod("covariance");covarianceMethod.setAccessible(true);double[][] covariance=(double[][])covarianceMethod.invoke(statistics);for(int i=0;i<covariance.length;i++)System.out.println("OPT_COV "+i+" "+java.util.Arrays.toString(covariance[i]));}catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}}
}
