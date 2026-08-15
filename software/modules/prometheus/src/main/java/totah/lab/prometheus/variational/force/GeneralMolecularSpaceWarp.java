package totah.lab.prometheus.variational.force;

import java.util.Objects;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** General normalized Filippi-Umrigar r^-4 space-warp weights and Cartesian divergences. */
public final class GeneralMolecularSpaceWarp {
    private GeneralMolecularSpaceWarp() { }
    public static Weight weight(Molecule molecule,QuantumCoordinates.ParticleCoordinate electron,int nucleus){Objects.requireNonNull(molecule);Objects.requireNonNull(electron);if(nucleus<0||nucleus>=molecule.nuclei().size())throw new IllegalArgumentException("nucleus index out of range");int n=molecule.nuclei().size();double[] f=new double[n],dx=new double[n],dy=new double[n],dz=new double[n];double sum=0,sx=0,sy=0,sz=0;for(int a=0;a<n;a++){CartesianPosition p=molecule.nuclei().get(a).position().inBohr();double x=electron.xBohr()-p.x(),y=electron.yBohr()-p.y(),z=electron.zBohr()-p.z(),r2=x*x+y*y+z*z;if(!(r2>1e-16))throw new IllegalArgumentException("SWCT singular at nucleus");f[a]=1/(r2*r2);double factor=-4/(r2*r2*r2);dx[a]=factor*x;dy[a]=factor*y;dz[a]=factor*z;sum+=f[a];sx+=dx[a];sy+=dy[a];sz+=dz[a];}double w=f[nucleus]/sum,den=sum*sum;return new Weight(w,(dx[nucleus]*sum-f[nucleus]*sx)/den,(dy[nucleus]*sum-f[nucleus]*sy)/den,(dz[nucleus]*sum-f[nucleus]*sz)/den);}
    public record Weight(double value,double dElectronX,double dElectronY,double dElectronZ){public Weight{if(!Double.isFinite(value)||value<0||value>1||!Double.isFinite(dElectronX)||!Double.isFinite(dElectronY)||!Double.isFinite(dElectronZ))throw new IllegalArgumentException("invalid general SWCT weight");}public double derivative(int axis){return switch(axis){case 0->dElectronX;case 1->dElectronY;case 2->dElectronZ;default->throw new IllegalArgumentException("axis must be 0..2");};}}
}
