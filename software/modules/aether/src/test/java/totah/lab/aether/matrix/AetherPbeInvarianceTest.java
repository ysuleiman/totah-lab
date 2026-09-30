package totah.lab.aether.matrix;

import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.PbeTestSupport;
import totah.lab.aether.basis.BasisFamily;
import static org.junit.jupiter.api.Assertions.*;

class AetherPbeInvarianceTest {
    @Test void blocksWorkersPermutationAndProvenance()throws Exception {
        var system=PbeTestSupport.system("h2o","native");var basis=BasisFamily.DEF2_SVP.forSystem(system);
        var p=PbeTestSupport.density(PbeTestSupport.reference("h2o-native-120-590"),system,basis);int n=p.size();
        BlockedPbe.Result reference;
        try(var grid=new BlockedPbe(system,basis,new GridDefinition(40,110),128,1)) {
            reference=grid.evaluate(p);assertEquals(reference.receiptHash(),grid.evaluate(p).receiptHash());
            assertThrows(IllegalArgumentException.class,()->grid.evaluate(DensityMatrix.fromRowMajor(PbeTestSupport.system("h2o","translated"),basis,Collections.nCopies(n*n,0.0))));
        }
        try(var grid=new BlockedPbe(system,basis,new GridDefinition(40,110),256,8)) {
            var other=grid.evaluate(p);assertEquals(reference.energyHartree(),other.energyHartree(),1e-10);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++)assertEquals(reference.potential().get(i,j),other.potential().get(i,j),1e-10);
            var reversed=new ArrayList<>(basis);Collections.reverse(reversed);
            var values=new ArrayList<Double>();for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add(p.get(n-1-i,n-1-j));
            var permuted=DensityMatrix.fromRowMajor(system,reversed,values);assertThrows(IllegalArgumentException.class,()->grid.evaluate(permuted));
            try(var changed=new BlockedPbe(system,reversed,new GridDefinition(40,110),256,8)) {
                var result=changed.evaluate(permuted);assertEquals(reference.energyHartree(),result.energyHartree(),1e-10);
                for(int i=0;i<n;i++)for(int j=0;j<n;j++)assertEquals(reference.potential().get(i,j),result.potential().get(n-1-i,n-1-j),1e-10);
            }
        }
    }
    @Test void completePotentialIsEnergyDerivativeAndZeroDensityIsZero()throws Exception {
        var system=PbeTestSupport.system("h2o","native");var basis=BasisFamily.DEF2_SVP.forSystem(system);
        var p=PbeTestSupport.density(PbeTestSupport.reference("h2o-native-120-590"),system,basis);int n=p.size();
        try(var grid=new BlockedPbe(system,basis,new GridDefinition(40,110),256,2)) {
            var value=grid.evaluate(p);double derivative=0;for(int i=0;i<n;i++)for(int j=0;j<n;j++)derivative+=p.get(i,j)*value.potential().get(i,j);
            double h=1e-5;var plus=new ArrayList<Double>();var minus=new ArrayList<Double>();
            for(int i=0;i<n;i++)for(int j=0;j<n;j++){plus.add((1+h)*p.get(i,j));minus.add((1-h)*p.get(i,j));}
            double numeric=(grid.evaluate(DensityMatrix.fromRowMajor(system,basis,plus)).energyHartree()-grid.evaluate(DensityMatrix.fromRowMajor(system,basis,minus)).energyHartree())/(2*h);
            assertEquals(derivative,numeric,1e-7);
            var zero=grid.evaluate(DensityMatrix.fromRowMajor(system,basis,Collections.nCopies(n*n,0.0)));
            assertEquals(0,zero.energyHartree());assertEquals(0,zero.integratedElectrons());for(int i=0;i<n;i++)for(int j=0;j<n;j++)assertEquals(0,zero.potential().get(i,j));
            assertThrows(ArithmeticException.class,()->grid.evaluate(DensityMatrix.fromRowMajor(system,basis,Collections.nCopies(n*n,-1.0))));
        }
    }
    @Test void positiveSemidefiniteDensityAtANodeUsesConservativeRoundoffBound()throws Exception {
        var system=PbeTestSupport.system("h2o","native");var basis=BasisFamily.DEF2_SVP.forSystem(system).subList(0,3);
        var random=new Random(1521);double[] zero=new double[3];
        for(int sample=0;sample<100;sample++) {
            double[] c={.1+random.nextDouble(),.1+random.nextDouble(),.1+random.nextDouble()};
            double[] a={random.nextDouble(),random.nextDouble(),0};a[2]=-(a[0]*c[0]+a[1]*c[1])/c[2];
            var entries=new ArrayList<Double>();for(int i=0;i<3;i++)for(int j=0;j<3;j++)entries.add(2*c[Math.min(i,j)]*c[Math.max(i,j)]);
            var p=DensityMatrix.fromRowMajor(system,basis,entries);var value=DensityGradient.evaluate(p,a,zero,zero,zero,0);
            assertTrue(value.rho()>=0);assertEquals(0,value.rho(),1e-14);
        }
    }
    @Test void finiteInputsCannotProduceSilentNonfiniteFunctionalEvidence(){
        assertThrows(ArithmeticException.class,()->PbeFunctional.evaluate(1e-14,Double.MAX_VALUE));
        assertThrows(ArithmeticException.class,()->new PbeFunctional.Value(0,0,Double.NaN,0));
    }
}
