package totah.lab.prometheus.variational.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.neural.CubicChebyshevGeometryEncoder;
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.HydrogenMoleculeNuclearForceEstimator;

final class FrozenForceControlEstimatorTest {
    @Test void directTraceMatchesExistingEstimatorWithoutChangingState(){double r=1.4;
        var state=new GeometryConditionedHydrogenMoleculeState(r,CubicChebyshevGeometryEncoder.coldStart());
        var h=new HydrogenMoleculeHamiltonian(r);var batches=new HydrogenMoleculeImportanceBatches(200,r,1.15,43,32);
        var expected=new HydrogenMoleculeNuclearForceEstimator().evaluate(state,h,batches);
        var trace=new DirectHfPulayForceTrace().evaluate(state,h,batches,value->{ });
        assertEquals(expected.forceHartreePerBohr(),trace.forceHartreePerBohr(),1e-13);
        assertEquals(200,trace.stateEvaluations());assertEquals(CubicChebyshevGeometryEncoder.coldStart(),state.parameters());}

    @Test void correlatedFiniteDifferenceIsDeterministicAndPaired(){double r=1.4;
        var state=new GeometryConditionedHydrogenMoleculeState(r,CubicChebyshevGeometryEncoder.coldStart());
        var batches=new HydrogenMoleculeImportanceBatches(200,r,1.15,43,32);
        var first=new CorrelatedSamplingFiniteDifferenceForceEstimator().evaluate(state,batches);
        var second=new CorrelatedSamplingFiniteDifferenceForceEstimator().evaluate(state,batches);
        assertEquals(first.forceHartreePerBohr(),second.forceHartreePerBohr());assertEquals(400,first.stateEvaluations());
        assertEquals(200,first.pairedSamples());assertTrue(Double.isFinite(first.forceHartreePerBohr()));}
}
