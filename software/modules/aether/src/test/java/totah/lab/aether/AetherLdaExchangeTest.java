package totah.lab.aether;

import java.util.*;
import org.junit.jupiter.api.Test;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.*;

class AetherLdaExchangeTest {
    @Test void independentAoDensityPartitionAndExchangeIntermediates()throws Exception {
        var densities=DftFixtures.densities();String previous="";XcIntegration.Result result=null;var errors=new TreeMap<String,Double>();int entries=0;
        for(var line:DftFixtures.read("dft-exchange.csv").lines().skip(1).toList()) {
            var c=line.split(",");if(!c[0].equals(previous)){var p=densities.get(c[0]);result=XcIntegration.evaluate(new AoGrid(MolecularGrid.build(p.system(),new GridDefinition(80,302)),p.functions()),p,LdaFunctional.EXCHANGE);previous=c[0];}
            int i=Integer.parseInt(c[2]),j=Integer.parseInt(c[3]);double actual=switch(c[1]) {
                case "vxc"->result.potential().get(i,j);case "electrons"->result.integratedElectrons();case "exc"->result.energyHartree();
                case "ao"->result.ao().get(i,j);case "rho"->result.densityAt(i);case "weight"->result.ao().grid().point(i).volumeWeightBohr3();
                case "x"->result.ao().grid().point(i).coordinateBohr().x();case "y"->result.ao().grid().point(i).coordinateBohr().y();case "z"->result.ao().grid().point(i).coordinateBohr().z();default->throw new AssertionError(c[1]);
            };
            double expected=Double.parseDouble(c[4]),error=Math.abs(actual-expected);errors.merge(c[1],error,Math::max);entries++;
            assertEquals(expected,actual,1e-9*Math.max(1,Math.abs(expected)),c[0]+" "+c[1]+" "+i+","+j);
        }
        System.out.println("LDA_EXCHANGE_GATE entries="+entries+" errors="+errors);
    }
    @Test void uniformExchangeConstantsAndDerivative() {
        for(double rho:new double[]{1e-250,1e-12,.001,.1,1,100,1e100}) {
            var v=LdaFunctional.EXCHANGE.evaluate(rho);double expected=-.75*Math.cbrt(3/Math.PI)*Math.cbrt(rho);
            assertEquals(expected,v.energyPerElectron(),Math.abs(expected)*1e-14);assertEquals(4*expected/3,v.potential(),Math.abs(expected)*1e-14);
            if(rho>1e-100) { // rho*epsilon underflows in the extreme-tail analytic case.
                double h=rho*1e-5;double derivative=((rho+h)*LdaFunctional.EXCHANGE.evaluate(rho+h).energyPerElectron()-(rho-h)*LdaFunctional.EXCHANGE.evaluate(rho-h).energyPerElectron())/(2*h);
                assertEquals(v.potential(),derivative,Math.abs(v.potential())*1e-8);
            }
        }
        assertEquals(0,LdaFunctional.EXCHANGE.evaluate(0).potential());
        assertThrows(IllegalArgumentException.class,()->LdaFunctional.EXCHANGE.evaluate(-1));assertThrows(IllegalArgumentException.class,()->LdaFunctional.EXCHANGE.evaluate(Double.NaN));
    }
    @Test void quadratureIntegratesAnalyticRadialAndAngularMoments()throws Exception {
        var atom=new QuantumSystem(List.of(new NuclearCenter(new Point3D(0,0,0),1)),1,1);
        for(int[] definition:new int[][]{{40,110},{80,302},{120,590},{160,974}}) {
            var grid=MolecularGrid.build(atom,new GridDefinition(definition[0],definition[1]));double norm=0,x2=0,xy=0;
            for(int g=0;g<grid.size();g++){var q=grid.point(g);var p=q.coordinateBohr();double f=q.volumeWeightBohr3()*Math.exp(-p.x()*p.x()-p.y()*p.y()-p.z()*p.z());norm+=f;x2+=f*p.x()*p.x();xy+=f*p.x()*p.y();}
            assertEquals(Math.pow(Math.PI,1.5),norm,definition[0]==40?2e-6:1e-10);assertEquals(Math.pow(Math.PI,1.5)/2,x2,definition[0]==40?2e-5:1e-10);assertEquals(0,xy,1e-14);
        }
    }
    @Test void incompatibleGridDensityAndInvalidDefinitionsFailClosed()throws Exception {
        var densities=DftFixtures.densities();var h=densities.get("h2");var water=densities.get("h2o");
        var ao=new AoGrid(MolecularGrid.build(h.system(),new GridDefinition(40,110)),h.functions());
        assertThrows(IllegalArgumentException.class,()->XcIntegration.evaluate(ao,water,LdaFunctional.EXCHANGE));
        assertThrows(IllegalArgumentException.class,()->new GridDefinition(41,110));assertThrows(IllegalArgumentException.class,()->new GridDefinition(40,111));
        var negative=DensityMatrix.fromRowMajor(h.system(),h.functions(),List.of(-1.,0.,0.,-1.));
        assertThrows(ArithmeticException.class,()->XcIntegration.evaluate(ao,negative,LdaFunctional.EXCHANGE));
    }
}
