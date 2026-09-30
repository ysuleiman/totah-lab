package totah.lab.aether;

import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

class AetherLdaCorrelationTest {
    @Test void libxcScalarValuesBothBranchesAndDerivative()throws Exception {
        for(var line:DftFixtures.read("lda-scalar.csv").lines().skip(1).toList()) {
            var c=line.split(",");var f=LdaFunctional.valueOf(c[0]);double rho=Double.parseDouble(c[1]);var v=f.evaluate(rho);
            assertEquals(Double.parseDouble(c[2]),v.energyPerElectron(),1e-12,c[0]+" epsilon");assertEquals(Double.parseDouble(c[3]),v.potential(),1e-12,c[0]+" potential");
            double h=rho*1e-7;double derivative=((rho+h)*f.evaluate(rho+h).energyPerElectron()-(rho-h)*f.evaluate(rho-h).energyPerElectron())/(2*h);
            assertEquals(v.potential(),derivative,1e-7*Math.max(1,Math.abs(v.potential())));
        }
        assertEquals(0,LdaFunctional.EXCHANGE_PZ81.evaluate(0).potential());
        assertTrue(Double.isFinite(LdaFunctional.EXCHANGE_PZ81.evaluate(Double.MIN_VALUE).potential()));
    }
    @Test void independentFixedDensityPzEnergyAndEveryPotentialEntry()throws Exception {
        var densities=DftFixtures.densities();String previous="";XcIntegration.Result r=null;var errors=new TreeMap<String,Double>();int entries=0;
        for(var line:DftFixtures.read("dft-pz.csv").lines().skip(1).toList()) {
            var c=line.split(",");if(!c[0].equals(previous)){var p=densities.get(c[0]);r=XcIntegration.evaluate(new AoGrid(MolecularGrid.build(p.system(),new GridDefinition(80,302)),p.functions()),p,LdaFunctional.EXCHANGE_PZ81);previous=c[0];}
            double actual=switch(c[1]){case "exc"->r.energyHartree();case "electrons"->r.integratedElectrons();case "vxc"->r.potential().get(Integer.parseInt(c[2]),Integer.parseInt(c[3]));default->throw new AssertionError();};
            double expected=Double.parseDouble(c[4]);errors.merge(c[1],Math.abs(actual-expected),Math::max);entries++;assertEquals(expected,actual,1e-9,c[0]+" "+c[1]);
        }
        System.out.println("PZ81_REFERENCE entries="+entries+" errors="+errors);
    }
}
