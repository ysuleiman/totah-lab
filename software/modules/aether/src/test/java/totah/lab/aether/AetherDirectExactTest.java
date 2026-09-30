package totah.lab.aether;

import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.integral.*;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

class AetherDirectExactTest {
    @Test void allPreparedSpdQuartetsRetainReferenceArithmetic()throws Exception {
        var system=DftFixtures.densities().get("h2o").system();
        var basis=Def2SvpBasis.load().forSystem(system);var plan=new PreparedRepulsion(basis);plan.beginBlock();
        for(int i=0;i<basis.size();i++)for(int j=0;j<=i;j++)for(int k=0;k<=i;k++)for(int l=0;l<=k;l++) {
            if(k*(k+1)/2+l>i*(i+1)/2+j)break;
            assertEquals(ElectronRepulsionIntegral.between(basis.get(i),basis.get(j),basis.get(k),basis.get(l)),plan.get(i,j,k,l),"quartet "+i+","+j+","+k+","+l);
        }
    }
    @Test void everyJkElementAndSymmetryMultiplicityForArbitraryDensity()throws Exception {
        var system=DftFixtures.densities().get("h2o").system();
        for(var family:BasisFamily.values()) {
            var basis=family.forSystem(system);int n=basis.size();var values=new ArrayList<Double>();
            for(int i=0;i<n;i++)for(int j=0;j<n;j++)values.add(Math.sin(i+j+1)/(n+1));
            var p=DensityMatrix.fromRowMajor(system,basis,values);var eri=new ElectronRepulsionCalculator(system,basis).calculate();
            var expected=JkCalculator.calculate(p,eri);var direct=new DirectExactJk(system,basis);
            var actual=direct.calculate(p,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE);
            try(var parallel=new DirectExactJk(system,basis,8)) {
                var concurrent=parallel.calculate(p,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE);
                for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                    assertEquals(actual.coulomb().get(i,j),concurrent.coulomb().get(i,j));
                    assertEquals(actual.exchange().orElseThrow().get(i,j),concurrent.exchange().orElseThrow().get(i,j));
                }
            }
            var only=direct.calculate(p,DirectExactJk.Contraction.COULOMB_ONLY);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                assertEquals(expected.coulomb().get(i,j),actual.coulomb().get(i,j),2e-12);
                assertEquals(expected.exchange().get(i,j),actual.exchange().orElseThrow().get(i,j),2e-12);
                assertEquals(actual.coulomb().get(i,j),only.coulomb().get(i,j));
            }
            long pairs=(long)n*(n+1)/2;
            assertEquals(pairs*(pairs+1)/2,actual.performance().uniqueQuartets());
            assertEquals(pairs*n*n,actual.performance().jAccumulations());
            assertEquals(pairs*n*n,actual.performance().kAccumulations());
            assertEquals(0,only.performance().kAccumulations());assertTrue(only.exchange().isEmpty());
            assertEquals(actual.receiptHash(),direct.calculate(p,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE).receiptHash());
            var reversed=new ArrayList<>(basis);Collections.reverse(reversed);var remapped=new ArrayList<Double>();
            for(int i=0;i<n;i++)for(int j=0;j<n;j++)remapped.add(p.get(n-1-i,n-1-j));
            var permutedDensity=DensityMatrix.fromRowMajor(system,reversed,remapped);
            var permuted=new DirectExactJk(system,reversed).calculate(permutedDensity,DirectExactJk.Contraction.COULOMB_AND_EXCHANGE);
            for(int i=0;i<n;i++)for(int j=0;j<n;j++) {
                assertEquals(actual.coulomb().get(i,j),permuted.coulomb().get(n-1-i,n-1-j),2e-12);
                assertEquals(actual.exchange().orElseThrow().get(i,j),permuted.exchange().orElseThrow().get(n-1-i,n-1-j),2e-12);
            }
            assertThrows(IllegalArgumentException.class,()->direct.calculate(permutedDensity,DirectExactJk.Contraction.COULOMB_ONLY));
        }
    }
}
