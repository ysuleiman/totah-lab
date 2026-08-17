package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class FermiNetSpatialJetTest {
    @Test void propagatesGradientAndLaplacianWithoutParameterSizedJets(){
        var x=FermiNetSpatialJet.variable(.7,3,0);
        var y=FermiNetSpatialJet.variable(-.4,3,1);
        var z=FermiNetSpatialJet.variable(.2,3,2);
        var value=x.multiply(x).add(y.multiply(y)).add(z.multiply(z))
                .sqrt().multiply(x.add(y).tanh()).exp();
        double[] point={.7,-.4,.2};double step=1e-4;
        assertEquals(function(point),value.value(),1e-15);
        double laplacian=0;
        for(int axis=0;axis<3;axis++){
            double[] plus=point.clone(),minus=point.clone();
            plus[axis]+=step;minus[axis]-=step;
            assertEquals((function(plus)-function(minus))/(2*step),
                    value.gradient(axis),2e-8);
            laplacian+=(function(plus)-2*function(point)+function(minus))
                    /(step*step);
        }
        assertEquals(laplacian,value.laplacian(),2e-7);
        assertEquals(3,value.dimensions());
    }

    @Test void rejectsMixedCoordinateDimensions(){
        assertThrows(IllegalArgumentException.class,()->
                FermiNetSpatialJet.constant(1,3).add(
                        FermiNetSpatialJet.constant(2,6)));
    }

    private static double function(double[] p){double r=Math.sqrt(
            p[0]*p[0]+p[1]*p[1]+p[2]*p[2]);return Math.exp(
                    r*Math.tanh(p[0]+p[1]));}
}
