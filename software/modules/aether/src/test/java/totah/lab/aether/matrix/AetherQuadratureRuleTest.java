package totah.lab.aether.matrix;

import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.Sto3gBasis;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherQuadratureRuleTest {
    @Test void radialMomentsThroughGaussExactnessDegree()throws Exception {
        for(int n:new int[]{40,80,120,160}) {
            var rule=MolecularGrid.rule("radial.csv",MolecularGrid.RADIAL_SHA256,n);
            for(var p:rule){assertTrue(p[0]>0&&p[0]<1);assertTrue(p[1]>0);}
            for(int k=0;k<2*n;k++){double sum=0;for(var p:rule)sum+=p[1]*Math.pow(p[0],k);assertEquals(1.0/(k+1),sum,1e-14,"radial "+n+" degree "+k);}
        }
    }
    @Test void angularMomentsThroughDeclaredLebedevOrder()throws Exception {
        for(int n:new int[]{110,302,590,974}) {
            var rule=MolecularGrid.rule("lebedev.csv",MolecularGrid.ANGULAR_SHA256,n);int degree=new GridDefinition(40,n).angularOrder();
            for(var p:rule){assertEquals(1,p[0]*p[0]+p[1]*p[1]+p[2]*p[2],2e-15);assertTrue(p[3]>0);}
            for(int k=0;k<=degree;k++)for(int axis=0;axis<3;axis++) {
                double sum=0;for(var p:rule)sum+=p[3]*Math.pow(p[axis],k);
                assertEquals(k%2==0?1.0/(k+1):0,sum,2e-14,"angular "+n+" degree "+k);
            }
            // Mixed spherical moments: <x^(2a)y^(2b)> = (2a-1)!!(2b-1)!!/(2a+2b+1)!!.
            for(int a=1;a<=degree/2;a++)for(int b=1;2*(a+b)<=degree;b++) {
                double sum=0;for(var p:rule)sum+=p[3]*Math.pow(p[0],2*a)*Math.pow(p[1],2*b);
                assertEquals(oddProduct(2*a-1)*oddProduct(2*b-1)/oddProduct(2*a+2*b+1),sum,2e-14);
            }
        }
    }
    private static double oddProduct(int n){double p=1;for(int i=1;i<=n;i+=2)p*=i;return p;}
    @Test void integratedExchangeMatrixIsEnergyDerivative()throws Exception {
        var system=new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),1),new NuclearCenter(new Point3D(0,0,1.4),1)),0,1);
        var basis=Sto3gBasis.load().forSystem(system);var ao=new AoGrid(MolecularGrid.build(system,new GridDefinition(40,110)),basis);
        var zero=XcIntegration.evaluate(ao,DensityMatrix.fromRowMajor(system,basis,List.of(0.,0.,0.,0.)),LdaFunctional.EXCHANGE_PZ81);
        assertEquals(0,zero.integratedElectrons());assertEquals(0,zero.energyHartree());
        for(int i=0;i<2;i++)for(int j=0;j<2;j++)assertEquals(0,zero.potential().get(i,j));
        var p=DensityMatrix.fromRowMajor(system,basis,List.of(1.,.2,.2,.8));var reference=XcIntegration.evaluate(ao,p,LdaFunctional.EXCHANGE);double h=1e-6;
        for(int i=0;i<2;i++)for(int j=i;j<2;j++) {
            var plus=new java.util.ArrayList<Double>();var minus=new java.util.ArrayList<Double>();
            for(int a=0;a<2;a++)for(int b=0;b<2;b++){boolean changed=(a==i&&b==j)||(a==j&&b==i);plus.add(p.get(a,b)+(changed?h:0));minus.add(p.get(a,b)-(changed?h:0));}
            double derivative=(XcIntegration.evaluate(ao,DensityMatrix.fromRowMajor(system,basis,plus),LdaFunctional.EXCHANGE).energyHartree()
                    -XcIntegration.evaluate(ao,DensityMatrix.fromRowMajor(system,basis,minus),LdaFunctional.EXCHANGE).energyHartree())/(2*h);
            assertEquals((i==j?1:2)*reference.potential().get(i,j),derivative,1e-8);
        }
    }
}
