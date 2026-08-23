package totah.lab.prometheus.potential;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.potential.baseline.AmberBaselinePotential;
import totah.lab.prometheus.potential.delta.DeltaPotential;
import totah.lab.prometheus.potential.hybrid.HybridPotential;

class HybridPotentialTest {
 @Test void addsBaselineAndDeltaWithoutCouplingThem(){QuantumCoordinates q=new QuantumCoordinates(new double[][]{{0,0,0}});AmberBaselinePotential baseline=new AmberBaselinePotential(ignored->new PotentialEvaluation(3,new double[][]{{1,2,3}}));DeltaPotential delta=ignored->new PotentialEvaluation(-.5,new double[][]{{.1,.2,.3}});PotentialEvaluation result=new HybridPotential(baseline,delta).evaluate(q);assertThat(result.energy()).isEqualTo(2.5);assertThat(result.forces()[0]).containsExactly(1.1,2.2,3.3);}
}
