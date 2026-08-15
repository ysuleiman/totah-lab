package totah.lab.prometheus.variational.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class WeightedForceDiagnosticsTest {
    @Test void reportsRawAndSeparateThreeIqrClippedDiagnostic(){var samples=new ArrayList<WeightedForceDiagnostics.Sample>();
        for(int i=0;i<20;i++)samples.add(new WeightedForceDiagnostics.Sample(1,i%3));samples.add(new WeightedForceDiagnostics.Sample(1,100));
        var result=WeightedForceDiagnostics.summarize(samples);assertTrue(result.clippedSamples()>0);
        assertTrue(result.clipped().mean()<result.raw().mean());assertEquals(21,result.samples());}
}
