package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.List;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;

/** Shared M13 quadrature equations, evaluated into caller-owned bounded blocks. Thread-confined. */
final class GridPointSource {
    private final QuantumSystem system;
    private final GridDefinition definition;
    private final List<double[]> radial,angular;
    private final double[][] distances;
    private final double[] d,partition;
    private final int size;
    GridPointSource(QuantumSystem system,GridDefinition definition)throws IOException {
        this.system=java.util.Objects.requireNonNull(system);this.definition=java.util.Objects.requireNonNull(definition);
        NuclearRepulsion.calculate(system);
        radial=MolecularGrid.rule("radial.csv",MolecularGrid.RADIAL_SHA256,definition.radialPoints());
        angular=MolecularGrid.rule("lebedev.csv",MolecularGrid.ANGULAR_SHA256,definition.angularPoints());
        int atoms=system.nuclei().size();size=Math.multiplyExact(atoms,Math.multiplyExact(radial.size(),angular.size()));
        distances=new double[atoms][atoms];d=new double[atoms];partition=new double[atoms];
        for(int a=0;a<atoms;a++)for(int b=0;b<a;b++)distances[a][b]=distance(system.nuclei().get(a).centerBohr(),system.nuclei().get(b).centerBohr());
    }
    int size(){return size;}
    void fill(int start,int count,double[] block) {
        if(start<0||count<0||start>(long)size-count||block.length<4L*count)throw new IllegalArgumentException("Grid block range");
        int atoms=system.nuclei().size();
        for(int k=0;k<count;k++) {
            int g=start+k,a=g/(radial.size()*angular.size());
            var center=system.nuclei().get(a).centerBohr();
            var radialPoint=radial.get((g/angular.size())%radial.size());var angularPoint=angular.get(g%angular.size());
            double u=radialPoint[0],r=u/(1-u),rw=radialPoint[1]/((1-u)*(1-u))*r*r*4*StrictMath.PI;
            double x=center.x()+r*angularPoint[0],y=center.y()+r*angularPoint[1],z=center.z()+r*angularPoint[2];
            var p=new Point3D(x,y,z);
            for(int b=0;b<atoms;b++){d[b]=distance(p,system.nuclei().get(b).centerBohr());partition[b]=1;}
            for(int b=0;b<atoms;b++)for(int c=0;c<b;c++) {
                double mu=(d[b]-d[c])/distances[b][c];
                if(!Double.isFinite(mu)||StrictMath.abs(mu)>1+1e-10)throw new ArithmeticException("Invalid Becke coordinate");
                mu=StrictMath.max(-1,StrictMath.min(1,mu));
                for(int j=0;j<3;j++)mu=1.5*mu-.5*mu*mu*mu;
                partition[b]*=.5*(1-mu);partition[c]*=.5*(1+mu);
            }
            double total=0;for(double v:partition)total+=v;
            if(!(total>0)||!Double.isFinite(total))throw new ArithmeticException("Singular Becke partition");
            double weight=rw*angularPoint[3]*(partition[a]/total);
            if(!Double.isFinite(weight)||weight<0)throw new ArithmeticException("Invalid quadrature weight");
            block[4*k]=x;block[4*k+1]=y;block[4*k+2]=z;block[4*k+3]=weight;
        }
    }
    String receiptHash() {
        var digest=new NumericalEvidenceHash("aether-grid-points-v1");double[] block=new double[2048];
        for(int start=0;start<size;start+=512) {
            int count=Math.min(512,size-start);fill(start,count,block);
            for(int i=0;i<4*count;i++)digest.add(block[i]);
        }
        return MolecularGrid.identity(system,definition,digest.finish());
    }
    private static double distance(Point3D a,Point3D b){double x=a.x()-b.x(),y=a.y()-b.y(),z=a.z()-b.z();return StrictMath.sqrt(x*x+y*y+z*z);}
}
