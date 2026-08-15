package totah.lab.prometheus.molecular;

import java.util.Objects;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** General nonrelativistic Born-Oppenheimer Coulomb Hamiltonian in atomic units. */
public final class GeneralMolecularCoulombHamiltonian {
    private final Molecule molecule;
    public GeneralMolecularCoulombHamiltonian(Molecule molecule){this.molecule=Objects.requireNonNull(molecule);}
    public Molecule molecule(){return molecule;}
    public LocalEnergyComponents localEnergy(QuantumCoordinates electrons,DifferentiableStateEvaluation state){
        if(electrons.particles().size()!=molecule.electrons().value())throw new IllegalArgumentException("electron count mismatch");double psi=state.value().real();if(Math.abs(psi)<1e-14)throw new IllegalArgumentException("local energy undefined at wavefunction node");
        double kinetic=-.5*state.coordinateLaplacian().value().real()/psi,en=0,ee=0,nn=0;
        for(var e:electrons.particles())for(var n:molecule.nuclei()){CartesianPosition p=n.position().inBohr();en-=n.charge().atomicNumber()/distance(e.xBohr(),e.yBohr(),e.zBohr(),p.x(),p.y(),p.z());}
        for(int i=0;i<electrons.particles().size();i++)for(int j=i+1;j<electrons.particles().size();j++){var a=electrons.particles().get(i);var b=electrons.particles().get(j);ee+=1/distance(a.xBohr(),a.yBohr(),a.zBohr(),b.xBohr(),b.yBohr(),b.zBohr());}
        for(int i=0;i<molecule.nuclei().size();i++)for(int j=i+1;j<molecule.nuclei().size();j++){var a=molecule.nuclei().get(i);var b=molecule.nuclei().get(j);CartesianPosition ap=a.position().inBohr(),bp=b.position().inBohr();nn+=a.charge().atomicNumber()*b.charge().atomicNumber()/distance(ap.x(),ap.y(),ap.z(),bp.x(),bp.y(),bp.z());}
        return new LocalEnergyComponents(kinetic,en,ee,nn);
    }
    private static double distance(double ax,double ay,double az,double bx,double by,double bz){double dx=ax-bx,dy=ay-by,dz=az-bz,r=Math.sqrt(dx*dx+dy*dy+dz*dz);if(!(r>1e-12))throw new IllegalArgumentException("Coulomb singularity at coincident charged particles");return r;}
}
