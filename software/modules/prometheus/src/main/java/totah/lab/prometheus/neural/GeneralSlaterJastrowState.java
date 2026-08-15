package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.GeneralMolecularCoulombHamiltonian;
import totah.lab.prometheus.molecular.MolecularFeatureBundle;
import totah.lab.prometheus.molecular.MolecularStateEvaluation;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.numerics.SecondOrderJet;
import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.ParameterGradient;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumAmplitude;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.StateGradient;
import totah.lab.prometheus.variational.StateLaplacian;

/** General real Slater(alpha) Slater(beta) times cusp-compatible electron-pair Jastrow state. */
public final class GeneralSlaterJastrowState implements DifferentiableQuantumState {
    public static final String REPRESENTATION_ID="general-slater-jastrow-atom-centered-v1";
    private final Molecule molecule;private final ParameterVector parameters;private final int orbitalCount;
    public GeneralSlaterJastrowState(Molecule molecule,ParameterVector parameters){this.molecule=Objects.requireNonNull(molecule);this.orbitalCount=Math.max(molecule.spin().alphaElectrons(),molecule.spin().betaElectrons());this.parameters=Objects.requireNonNull(parameters);if(parameters.values().size()!=orbitalCount+1)throw new IllegalArgumentException("one exponent per occupied orbital plus one Jastrow parameter required");if(parameters.values().stream().anyMatch(x->!Double.isFinite(x)||x<=0))throw new IllegalArgumentException("state parameters must be finite and positive");}
    public static GeneralSlaterJastrowState cuspInitialized(Molecule molecule){int count=Math.max(molecule.spin().alphaElectrons(),molecule.spin().betaElectrons());List<Double> p=new ArrayList<>();double exponent=molecule.nuclei().stream().mapToInt(n->n.charge().atomicNumber()).max().orElseThrow();for(int i=0;i<count;i++)p.add(exponent);p.add(.5);return new GeneralSlaterJastrowState(molecule,new ParameterVector(p));}
    public Molecule molecule(){return molecule;}public String representationId(){return REPRESENTATION_ID;}@Override public ParameterVector parameters(){return parameters;}@Override public GeneralSlaterJastrowState withParameters(ParameterVector p){return new GeneralSlaterJastrowState(molecule,p);}
    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates){return evaluate(coordinates).derivatives();}
    public MolecularStateEvaluation evaluate(QuantumCoordinates coordinates){return evaluateInternal(coordinates,Optional.empty());}
    public MolecularStateEvaluation evaluateWithLocalEnergy(QuantumCoordinates coordinates,GeneralMolecularCoulombHamiltonian h){if(!h.molecule().scientificIdentity().equals(molecule.scientificIdentity()))throw new IllegalArgumentException("Hamiltonian/state molecule mismatch");MolecularStateEvaluation base=evaluateInternal(coordinates,Optional.empty());return new MolecularStateEvaluation(base.logAbsoluteWavefunction(),base.sign(),base.derivatives(),base.features(),Optional.of(h.localEnergy(coordinates,base.derivatives())));}

    private MolecularStateEvaluation evaluateInternal(QuantumCoordinates coordinates,Optional<totah.lab.prometheus.molecular.LocalEnergyComponents> ignored){validateCoordinates(coordinates);int coordinateDimensions=3*coordinates.particles().size(),dimensions=coordinateDimensions+parameters.values().size();SecondOrderJet[][] xyz=new SecondOrderJet[coordinates.particles().size()][3];for(int i=0;i<xyz.length;i++){var e=coordinates.particles().get(i);xyz[i][0]=SecondOrderJet.variable(e.xBohr(),dimensions,3*i);xyz[i][1]=SecondOrderJet.variable(e.yBohr(),dimensions,3*i+1);xyz[i][2]=SecondOrderJet.variable(e.zBohr(),dimensions,3*i+2);}SecondOrderJet[] parameterJets=new SecondOrderJet[parameters.values().size()];for(int i=0;i<parameterJets.length;i++)parameterJets[i]=SecondOrderJet.variable(parameters.values().get(i),dimensions,coordinateDimensions+i);
        SecondOrderJet alpha=determinant(matrix(xyz,0,molecule.spin().alphaElectrons(),dimensions,parameterJets),dimensions);SecondOrderJet beta=determinant(matrix(xyz,molecule.spin().alphaElectrons(),molecule.spin().betaElectrons(),dimensions,parameterJets),dimensions);SecondOrderJet jastrow=SecondOrderJet.constant(0,dimensions);SecondOrderJet b=parameterJets[parameterJets.length-1];
        List<Double> ee=new ArrayList<>();for(int i=0;i<xyz.length;i++)for(int j=i+1;j<xyz.length;j++){SecondOrderJet r=distance(xyz[i][0].subtract(xyz[j][0]),xyz[i][1].subtract(xyz[j][1]),xyz[i][2].subtract(xyz[j][2]));double cusp=sameSpin(coordinates,i,j)?.25:.5;jastrow=jastrow.add(r.multiply(cusp).divide(r.multiply(b).add(1)));ee.add(r.value());}
        SecondOrderJet psi=alpha.multiply(beta).multiply(jastrow.exp());if(Math.abs(psi.value())<1e-14)throw new IllegalArgumentException("wavefunction node is not a valid derivative fixture");
        List<StateGradient.Vector3> gradients=new ArrayList<>();for(int i=0;i<xyz.length;i++)gradients.add(new StateGradient.Vector3(QuantumAmplitude.real(psi.gradient(3*i)),QuantumAmplitude.real(psi.gradient(3*i+1)),QuantumAmplitude.real(psi.gradient(3*i+2))));
        List<QuantumAmplitude> parameterDerivatives=new ArrayList<>();for(int i=0;i<parameterJets.length;i++)parameterDerivatives.add(QuantumAmplitude.real(psi.gradient(coordinateDimensions+i)));
        var derivative=new DifferentiableStateEvaluation(QuantumAmplitude.real(psi.value()),new StateGradient(gradients),new StateLaplacian(QuantumAmplitude.real(psi.laplacian(coordinateDimensions))),new ParameterGradient(parameterDerivatives),CanonicalHashing.sha256Hex(REPRESENTATION_ID+"|"+molecule.scientificIdentity()+"|"+coordinates+"|"+parameters.values()));
        MolecularFeatureBundle features=features(coordinates,ee);return new MolecularStateEvaluation(Math.log(Math.abs(psi.value())),psi.value()>0?1:-1,derivative,features,Optional.empty());}

    private SecondOrderJet[][] matrix(SecondOrderJet[][] xyz,int offset,int count,int dimensions,SecondOrderJet[] parameterJets){SecondOrderJet[][] m=new SecondOrderJet[count][count];for(int i=0;i<count;i++)for(int j=0;j<count;j++)m[i][j]=orbital(xyz[offset+i],j,dimensions,parameterJets[j]);return m;}
    private SecondOrderJet orbital(SecondOrderJet[] electron,int orbital,int dimensions,SecondOrderJet exponent){SecondOrderJet value=SecondOrderJet.constant(0,dimensions);for(var nucleus:molecule.nuclei()){var center=nucleus.position().inBohr();SecondOrderJet dx=electron[0].add(-center.x()),dy=electron[1].add(-center.y()),dz=electron[2].add(-center.z()),r=distance(dx,dy,dz);value=value.add(polynomial(orbital,dx,dy,dz,r,dimensions).multiply(r.multiply(exponent).multiply(-1).exp()).multiply(nucleus.charge().atomicNumber()));}return value;}
    private static SecondOrderJet polynomial(int orbital,SecondOrderJet x,SecondOrderJet y,SecondOrderJet z,SecondOrderJet r,int dimensions){return switch(orbital%10){case 0->SecondOrderJet.constant(1,dimensions);case 1->x;case 2->y;case 3->z;case 4->r;case 5->x.multiply(y);case 6->x.multiply(z);case 7->y.multiply(z);case 8->x.multiply(x).add(y.multiply(y)).add(z.multiply(z));default->x.multiply(x).subtract(y.multiply(y));};}
    private static SecondOrderJet determinant(SecondOrderJet[][] input,int dimensions){int n=input.length;if(n==0)return SecondOrderJet.constant(1,dimensions);SecondOrderJet[][] a=new SecondOrderJet[n][n];for(int i=0;i<n;i++)a[i]=input[i].clone();SecondOrderJet det=SecondOrderJet.constant(1,dimensions);int sign=1;for(int k=0;k<n;k++){int pivot=k;for(int i=k+1;i<n;i++)if(Math.abs(a[i][k].value())>Math.abs(a[pivot][k].value()))pivot=i;if(Math.abs(a[pivot][k].value())<1e-14)throw new IllegalArgumentException("singular Slater determinant");if(pivot!=k){var row=a[k];a[k]=a[pivot];a[pivot]=row;sign=-sign;}SecondOrderJet p=a[k][k];det=det.multiply(p);for(int i=k+1;i<n;i++){SecondOrderJet factor=a[i][k].divide(p);for(int j=k+1;j<n;j++)a[i][j]=a[i][j].subtract(factor.multiply(a[k][j]));}}return det.multiply(sign);}
    private MolecularFeatureBundle features(QuantumCoordinates c,List<Double> ee){List<List<Double>> en=new ArrayList<>();for(var e:c.particles()){List<Double> row=new ArrayList<>();for(var n:molecule.nuclei()){CartesianPosition p=n.position().inBohr();row.add(distance(e.xBohr(),e.yBohr(),e.zBohr(),p.x(),p.y(),p.z()));}en.add(row);}List<Double> nn=new ArrayList<>();for(int i=0;i<molecule.nuclei().size();i++)for(int j=i+1;j<molecule.nuclei().size();j++){CartesianPosition a=molecule.nuclei().get(i).position().inBohr(),b=molecule.nuclei().get(j).position().inBohr();nn.add(distance(a.x(),a.y(),a.z(),b.x(),b.y(),b.z()));}return new MolecularFeatureBundle(en,ee,nn);}
    private void validateCoordinates(QuantumCoordinates c){if(c.particles().size()!=molecule.electrons().value())throw new IllegalArgumentException("electron count mismatch");for(int i=0;i<c.particles().size();i++){SpinProjection expected=i<molecule.spin().alphaElectrons()?SpinProjection.ALPHA:SpinProjection.BETA;if(c.particles().get(i).spin()!=expected)throw new IllegalArgumentException("electrons must be ordered alpha then beta");}}
    private static boolean sameSpin(QuantumCoordinates c,int i,int j){return c.particles().get(i).spin()==c.particles().get(j).spin();}
    private static SecondOrderJet distance(SecondOrderJet x,SecondOrderJet y,SecondOrderJet z){return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt();}
    private static double distance(QuantumCoordinates.ParticleCoordinate a,QuantumCoordinates.ParticleCoordinate b){return distance(a.xBohr(),a.yBohr(),a.zBohr(),b.xBohr(),b.yBohr(),b.zBohr());}
    private static double distance(double ax,double ay,double az,double bx,double by,double bz){double x=ax-bx,y=ay-by,z=az-bz;return Math.sqrt(x*x+y*y+z*z);}
}
