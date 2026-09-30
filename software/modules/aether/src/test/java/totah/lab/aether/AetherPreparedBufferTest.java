package totah.lab.aether;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import totah.lab.aether.basis.*;
import totah.lab.aether.integral.*;
import totah.lab.gaia.geometry.Point3D;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AetherPreparedBufferTest {
    @Test void bufferInvalidationPreservesValuesAcrossContextEvictionAndShellBoundaries() {
        var basis=new ArrayList<ContractedGaussian>();
        for(int center=0;center<4;center++) {
            var terms=new ArrayList<GaussianTerm>();
            for(int p=0;p<9;p++)terms.add(new GaussianTerm(new PrimitiveGaussian(
                    new Point3D(.3*center,.17*center*center,-.2*center),
                    StrictMath.pow(1.13+.02*center,p+1)),1.0/(p+1)));
            basis.add(new ContractedGaussian(terms,CartesianAngularMomentum.PX));
        }
        // Four distinct centers and nine primitives give 9^4 = 6561 contexts,
        // exercising eviction of the bounded 4096-context cache within one quartet.
        double expected=ElectronRepulsionIntegral.between(basis.get(3),basis.get(2),basis.get(1),basis.get(0));
        var prepared=new PreparedRepulsion(basis);
        for(int block=0;block<3;block++) {
            prepared.beginBlock();
            try {
                assertEquals(expected,prepared.get(3,2,1,0));
                assertEquals(expected,prepared.get(3,2,1,0));
            } finally {prepared.endBlock();}
        }
    }
}
