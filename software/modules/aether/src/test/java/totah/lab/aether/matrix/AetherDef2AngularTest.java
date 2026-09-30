package totah.lab.aether.matrix;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.integral.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherDef2AngularTest {
    @Test void boysFiveThroughEightAgainstIndependentQuadrature() throws Exception {
        byte[] bytes;try(var in=getClass().getResourceAsStream("/totah/lab/aether/reference/def2-boys.csv")){bytes=Objects.requireNonNull(in).readAllBytes();}
        assertEquals("51739d8e2c13806a7df0ee96e5f2d7804f50136a15f552d44ad0e72c8e737dfc",ContentHash.sha256(bytes));
        for(var line:new String(bytes,StandardCharsets.US_ASCII).lines().skip(1).toList()) {
            var c=line.split(",");double expected=Double.parseDouble(c[2]);
            assertEquals(expected,BoysFunction.value(Integer.parseInt(c[0]),Double.parseDouble(c[1])),Math.abs(expected)*5e-13,line);
        }
    }
    @Test void dShellRotationCovarianceIncludingMixedCartesianComponents() {
        var primitive=new PrimitiveGaussian(new Point3D(0,0,0),.8);
        var d=Arrays.stream(CartesianAngularMomentum.values()).filter(l->l.x()+l.y()+l.z()==2)
                .map(l->new ContractedGaussian(List.of(new GaussianTerm(primitive,1)),l)).toList();
        double c=StrictMath.cos(.513),s=StrictMath.sin(.513),r=StrictMath.sqrt(3);
        double[][] u={{c*c,2*c*s/r,0,s*s,0,0},{-r*c*s,c*c-s*s,0,r*c*s,0,0},
                {0,0,c,0,s,0},{s*s,-2*c*s/r,0,c*c,0,0},{0,0,-s,0,c,0},{0,0,0,0,0,1}};
        var point=new Point3D(.2,-.7,.4);var rotated=new Point3D(c*point.x()+s*point.y(),-s*point.x()+c*point.y(),point.z());
        for(int i=0;i<6;i++) {
            double sum=0;for(int j=0;j<6;j++)sum+=u[i][j]*AoGrid.evaluate(d.get(j),point);
            assertEquals(AoGrid.evaluate(d.get(i),rotated),sum,2e-15);
        }
        var overlap=OverlapMatrix.compute(d);
        for(int i=0;i<6;i++)for(int j=0;j<6;j++) {
            double sum=0;for(int a=0;a<6;a++)for(int b=0;b<6;b++)sum+=u[i][a]*u[j][b]*overlap.get(a,b);
            assertEquals(overlap.get(i,j),sum,3e-14);
        }
        double[][][][] eri=new double[6][6][6][6]; // Validation-only full tensor for independent AO transformation.
        for(int a=0;a<6;a++)for(int b=0;b<6;b++)for(int k=0;k<6;k++)for(int l=0;l<6;l++)
            eri[a][b][k][l]=ElectronRepulsionIntegral.between(d.get(a),d.get(b),d.get(k),d.get(l));
        var context=new totah.lab.aether.model.QuantumSystem(List.of(new totah.lab.aether.model.NuclearCenter(new Point3D(0,0,0),8)),0,1);
        var packed=new ElectronRepulsionCalculator(context,d).calculate();assertEquals(231,packed.uniqueQuartetCount());
        for(int a=0;a<6;a++)for(int b=0;b<6;b++)for(int k=0;k<6;k++)for(int l=0;l<6;l++) {
            double value=packed.get(a,b,k,l);assertEquals(eri[a][b][k][l],value,3e-14);
            for(double equivalent:new double[]{packed.get(b,a,k,l),packed.get(a,b,l,k),packed.get(b,a,l,k),packed.get(k,l,a,b),packed.get(l,k,a,b),packed.get(k,l,b,a),packed.get(l,k,b,a)})assertEquals(value,equivalent);
        }
        for(int[] ijkl:List.of(new int[]{0,0,0,0},new int[]{0,1,3,4},new int[]{1,2,4,5},new int[]{3,3,1,1})) {
            double sum=0;for(int a=0;a<6;a++)for(int b=0;b<6;b++)for(int k=0;k<6;k++)for(int l=0;l<6;l++)
                sum+=u[ijkl[0]][a]*u[ijkl[1]][b]*u[ijkl[2]][k]*u[ijkl[3]][l]*eri[a][b][k][l];
            assertEquals(eri[ijkl[0]][ijkl[1]][ijkl[2]][ijkl[3]],sum,3e-14);
        }
    }
}
