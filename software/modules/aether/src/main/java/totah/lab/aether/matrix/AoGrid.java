package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.CartesianAngularMomentum;
import totah.lab.aether.provenance.ContentHash;

/** AO values reuse primitive and contraction normalization exactly; indexed immutable evidence. */
public final class AoGrid {
    private final MolecularGrid grid;private final List<ContractedGaussian> basis;private final double[] values;private final String basisHash,hash;
    public AoGrid(MolecularGrid grid,List<ContractedGaussian> basis) {
        this.grid=java.util.Objects.requireNonNull(grid);this.basis=List.copyOf(basis);basisHash=IntegralMatrixData.basisGeometryHash(this.basis);
        values=new double[Math.multiplyExact(grid.size(),basis.size())];var digest=new NumericalEvidenceHash("aether-AO-grid-v1");
        for(int g=0;g<grid.size();g++)for(int i=0;i<basis.size();i++) {
            double value=evaluate(basis.get(i),new totah.lab.gaia.geometry.Point3D(grid.x(g),grid.y(g),grid.z(g)));
            values[g*basis.size()+i]=value;digest.add(value);
        }
        hash=ContentHash.sha256((basis.stream().anyMatch(f->f.angularMomentum().x()+f.angularMomentum().y()+f.angularMomentum().z()==2) ? "aether-AO-grid-v2;normalized-s/p/d;ordered-primitives;StrictMath\n" : "aether-AO-grid-v1;normalized-s/p;ordered-primitives;StrictMath\n")+basisHash+"\n"+grid.receiptHash()+"\n"+digest.finish());
    }
    static double evaluate(ContractedGaussian f,totah.lab.gaia.geometry.Point3D point) {
        return evaluate(f,point,null);
    }
    /** Exact, point-independent products in the same left-to-right order as the reference AO sum. */
    static double[] prepare(ContractedGaussian f) {
        double[] products=new double[f.terms().size()];
        for(int i=0;i<products.length;i++){var term=f.terms().get(i);products[i]=term.coefficient()*f.angularMomentum().normalization(term.primitive());}
        return products;
    }
    static double evaluate(ContractedGaussian f,totah.lab.gaia.geometry.Point3D point,double[] products) {
            var center=f.terms().getFirst().primitive().centerBohr();
            double x=point.x()-center.x(),y=point.y()-center.y(),z=point.z()-center.z(),r2=x*x+y*y+z*z;
            double polynomial=switch(f.angularMomentum()){case S->1;case PX->x;case PY->y;case PZ->z;default->StrictMath.pow(x,f.angularMomentum().x())*StrictMath.pow(y,f.angularMomentum().y())*StrictMath.pow(z,f.angularMomentum().z());};
            double value=0;
            for(int i=0;i<f.terms().size();i++) {
                var term=f.terms().get(i);
                double product=products==null?term.coefficient()*f.angularMomentum().normalization(term.primitive()):products[i];
                value+=product*StrictMath.exp(-term.primitive().exponent()*r2);
            }
            value*=f.normalization()*polynomial;if(!Double.isFinite(value))throw new ArithmeticException("Nonfinite AO value");
        return value;
    }
    public double get(int point,int ao){if(point<0||point>=grid.size()||ao<0||ao>=basis.size())throw new IndexOutOfBoundsException();return values[point*basis.size()+ao];}
    public int basisSize(){return basis.size();}public MolecularGrid grid(){return grid;}
    public List<ContractedGaussian> functions(){return basis;}public String basisGeometryHash(){return basisHash;}public String receiptHash(){return hash;}
}
