package totah.lab.aether.matrix;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import totah.lab.aether.PbeTestSupport;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherPbeTest {
    @TempDir Path cache;
    static java.util.stream.Stream<String> cases(){return Arrays.stream(System.getProperty("aether.pbe.cases","h2-native-120-590 h2o-native-120-590 nh3-native-120-590 ch4-native-120-590 co-native-120-590 n2-native-120-590 h2s-native-120-590 ph3-native-120-590 hcl-native-120-590 ch3cl-native-120-590 dms-native-120-590 trimethylsulfonium-native-120-590 chlorobenzene-native-120-590").split(" "));}
    @Test void functionalAgainstLibxc()throws Exception {
        double max=0;int count=0;
        for(var line:PbeTestSupport.reference("functional").lines().skip(1).toList()) {
            var c=Arrays.stream(line.split(",")).mapToDouble(Double::parseDouble).toArray();var v=PbeFunctional.evaluate(c[0],c[1]);
            double[] values={v.exchangeDensity(),v.correlationDensity(),v.vrho(),v.vsigma()};
            for(int i=0;i<4;i++){double error=Math.abs(values[i]-c[i+2]);double bound=i<2?Math.abs(c[i+2]):c[i+4];max=Math.max(max,error/Math.max(1,bound));assertEquals(c[i+2],values[i],1e-10*Math.max(1,bound),line+" field="+i);count++;}
        }
        System.out.println("M15 FUNCTIONAL entries="+count+" maxScaledError="+max);
    }
    @Test void failClosedAndZeroGradient(){
        assertThrows(IllegalArgumentException.class,()->PbeFunctional.evaluate(-1,0));
        assertThrows(IllegalArgumentException.class,()->PbeFunctional.evaluate(1,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->PbeFunctional.evaluate(1,-1));
        assertEquals(0,PbeFunctional.evaluate(0,0).energyDensity());assertTrue(Double.isFinite(PbeFunctional.evaluate(1,0).vsigma()));
    }
    @ParameterizedTest @MethodSource("cases") void derivativesDensityAndCompleteVxc(String stem)throws Exception {
        var parts=stem.split("-");var system=PbeTestSupport.system(parts[0],parts[1]);var basis=BasisFamily.DEF2_SVP.forSystem(system);
        String ref=PbeTestSupport.reference(stem);var density=PbeTestSupport.density(ref,system,basis);int n=basis.size();double maxAo=0,maxRho=0,maxSigma=0;int entries=0;
        var rows=PbeTestSupport.reference(stem+"-ao").lines().skip(1).toList();
        for(int k=0;k<rows.size();k+=n) {
            var first=Arrays.stream(rows.get(k).split(",")).mapToDouble(Double::parseDouble).toArray();var point=new Point3D(first[1],first[2],first[3]);
            double[] a=new double[n],dx=new double[n],dy=new double[n],dz=new double[n];
            for(int i=0;i<n;i++) {
                var c=Arrays.stream(rows.get(k+i).split(",")).mapToDouble(Double::parseDouble).toArray();var d=AoFirstDerivatives.evaluate(basis.get(i),point);
                a[i]=AoGrid.evaluate(basis.get(i),point);dx[i]=d.x();dy[i]=d.y();dz[i]=d.z();double[] actual={a[i],dx[i],dy[i],dz[i]};
                for(int q=0;q<4;q++){double error=Math.abs(actual[q]-c[5+q]);if(q>0)maxAo=Math.max(maxAo,error);assertEquals(c[5+q],actual[q],1e-8+1e-10*Math.abs(c[5+q]),stem+" AO="+i+" point="+k/n);entries++;}
            }
            var r=DensityGradient.evaluate(density,a,dx,dy,dz,0);double[] actual={r.rho(),r.x(),r.y(),r.z(),r.sigma()};
            for(int i=0;i<5;i++){if(i<4)maxRho=Math.max(maxRho,Math.abs(actual[i]-first[9+i]));else maxSigma=Math.max(maxSigma,Math.abs(actual[i]-first[9+i]));assertEquals(first[9+i],actual[i],1e-8+1e-10*Math.abs(first[9+i]),stem+" density "+i);}
        }
        double maxMatrix=0,energyError=0;try(var grid=new BlockedPbe(system,basis,new GridDefinition(Integer.parseInt(parts[2]),Integer.parseInt(parts[3])),256,8)) {
            var value=grid.evaluate(density);
            for(var line:ref.lines().skip(1).toList()) {
                var c=line.split(",");double expected=Double.parseDouble(c[3]);double actual;
                switch(c[0]){case "Vxc"->actual=value.potential().get(Integer.parseInt(c[1]),Integer.parseInt(c[2]));case "Exc"->actual=value.energyHartree();case "electrons"->actual=value.integratedElectrons();default->{continue;}}
                double error=Math.abs(actual-expected);if(c[0].equals("Vxc"))maxMatrix=Math.max(maxMatrix,error);if(c[0].equals("Exc"))energyError=error;
                assertEquals(expected,actual,1e-8,stem+" "+line);
            }
        }
        System.out.println("M15 INTERMEDIATE "+stem+" entries="+entries+" maxAOderivative="+maxAo+" maxRhoGradient="+maxRho+" maxSigma="+maxSigma+" maxExc="+energyError+" maxVxc="+maxMatrix);
    }
    @Test void cachedJOnlyMatchesBothFrozenJPaths()throws Exception {
        var system=PbeTestSupport.system("h2o","native");var basis=BasisFamily.DEF2_SVP.forSystem(system);
        var p=PbeTestSupport.density(PbeTestSupport.reference("h2o-native-120-590"),system,basis);
        var eri=new ElectronRepulsionCalculator(system,basis).calculate();var packed=PackedCoulomb.calculate(p,eri);
        var created=EriDiskCache.openOrCreate(cache,basis,2);
        try(var cached=new CachedCoulomb(system,created.cache());var direct=new DirectExactJk(system,basis,2)) {
            var a=cached.calculate(p);var b=direct.calculate(p,DirectExactJk.Contraction.COULOMB_ONLY);assertTrue(b.exchange().isEmpty());
            for(int i=0;i<p.size();i++)for(int j=0;j<p.size();j++){assertEquals(packed.coulomb().get(i,j),a.coulomb().get(i,j),1e-11);assertEquals(b.coulomb().get(i,j),a.coulomb().get(i,j),1e-11);}
            assertEquals(a.coulomb().receiptHash(),cached.calculate(p).coulomb().receiptHash());
        }
    }
}
