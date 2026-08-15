package totah.lab.prometheus.variational.force;

import java.util.ArrayList;
import java.util.List;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.GeneralMolecularCoulombHamiltonian;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.variational.GeneralMolecularSampleSource;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Independent central finite-difference SWCT oracle; never a production force estimator. */
public final class GeneralMolecularSwctFiniteDifferenceOracle {
    public Result evaluate(GeneralSlaterJastrowState state,GeneralMolecularSampleSource samples,double stepBohr){if(!(stepBohr>0))throw new IllegalArgumentException("positive finite-difference step required");List<GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce> result=new ArrayList<>();long[] traversals={0};for(int a=0;a<state.molecule().nuclei().size();a++){double[] f=new double[3];for(int axis=0;axis<3;axis++)f[axis]=-(energy(state,samples,a,axis,stepBohr,traversals)-energy(state,samples,a,axis,-stepBohr,traversals))/(2*stepBohr);result.add(new GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce(a,f[0],f[1],f[2],Double.NaN,Double.NaN,Double.NaN));}return new Result(result,traversals[0],stepBohr,"CENTRAL_FINITE_DIFFERENCE_SWCT_REFERENCE_ONLY");}
    private static double energy(GeneralSlaterJastrowState original,GeneralMolecularSampleSource samples,int nucleus,int axis,double delta,long[] traversals){Molecule moved=moveNucleus(original.molecule(),nucleus,axis,delta);var state=new GeneralSlaterJastrowState(moved,original.parameters());var h=new GeneralMolecularCoulombHamiltonian(moved);double[] norm={0},numerator={0};samples.forEach((weight,c)->{Transformation t=transform(original.molecule(),c,nucleus,axis,delta);var e=state.evaluateWithLocalEnergy(t.coordinates,h);double psi=e.derivatives().value().real(),w=weight*t.jacobian*psi*psi;norm[0]+=w;numerator[0]+=w*e.localEnergy().orElseThrow().totalHartree();traversals[0]++;});return numerator[0]/norm[0];}
    private static Molecule moveNucleus(Molecule molecule,int index,int axis,double delta){List<NuclearCenter> nuclei=new ArrayList<>();for(NuclearCenter n:molecule.nuclei()){CartesianPosition p=n.position().inBohr();double[] q={p.x(),p.y(),p.z()};if(n.orderedIndex()==index)q[axis]+=delta;nuclei.add(new NuclearCenter(n.orderedIndex(),n.element(),n.charge(),new CartesianPosition(q[0],q[1],q[2],totah.lab.prometheus.molecular.LengthUnit.BOHR)));}return new Molecule(molecule.moleculeId(),nuclei,molecule.charge(),molecule.electrons(),molecule.spin());}
    private static Transformation transform(Molecule molecule,QuantumCoordinates c,int nucleus,int axis,double delta){List<QuantumCoordinates.ParticleCoordinate> moved=new ArrayList<>();double jacobian=1;for(var e:c.particles()){var w=GeneralMolecularSpaceWarp.weight(molecule,e,nucleus);double[] q={e.xBohr(),e.yBohr(),e.zBohr()};q[axis]+=delta*w.value();jacobian*=1+delta*w.derivative(axis);moved.add(new QuantumCoordinates.ParticleCoordinate(e.particleIndex(),q[0],q[1],q[2],e.spin()));}if(!(jacobian>0))throw new IllegalArgumentException("non-positive general SWCT Jacobian");return new Transformation(new QuantumCoordinates(moved),jacobian);}
    public record Result(List<GeneralAnalyticDifferentialSwctForceEstimator.NuclearForce> forces,long stateTraversals,double stepBohr,String method){public Result{forces=List.copyOf(forces);}}
    private record Transformation(QuantumCoordinates coordinates,double jacobian){}
}
