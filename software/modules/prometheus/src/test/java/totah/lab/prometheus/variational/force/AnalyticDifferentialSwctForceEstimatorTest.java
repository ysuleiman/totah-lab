package totah.lab.prometheus.variational.force;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.CubicChebyshevGeometryEncoder;
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;

class AnalyticDifferentialSwctForceEstimatorTest {
    @Test void analyticEstimatorMatchesFrozenNumericalEstimatorAndUsesOneTraversal(){double radius=1.4;var state=new GeometryConditionedHydrogenMoleculeState(radius,CubicChebyshevGeometryEncoder.coldStart());var h=new HydrogenMoleculeHamiltonian(radius);var batches=new HydrogenMoleculeImportanceBatches(257,radius,1.15,41,53);
        var numerical=new HydrogenMoleculeSpaceWarpForceEstimator().evaluate(state,h,batches);var analytic=new AnalyticDifferentialSwctForceEstimator().evaluate(state,h,batches);
        assertThat(analytic.forceHartreePerBohr()).isCloseTo(numerical.forceHartreePerBohr(),org.assertj.core.data.Offset.offset(5e-5));
        assertThat(analytic.forceEstimatorVarianceHartree2PerBohr2()).isCloseTo(numerical.forceEstimatorVarianceHartree2PerBohr2(),org.assertj.core.data.Offset.offset(5e-5));
        assertThat(analytic.stateTraversals()).isEqualTo(257);assertThat(analytic.localEnergyEvaluations()).isEqualTo(257);assertThat(numerical.stateEvaluations()).isEqualTo(5L*257);}
}
