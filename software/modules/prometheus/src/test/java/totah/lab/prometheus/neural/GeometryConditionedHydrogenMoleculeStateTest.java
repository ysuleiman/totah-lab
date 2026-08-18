package totah.lab.prometheus.neural;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class GeometryConditionedHydrogenMoleculeStateTest {
    private static final ParameterVector COLD=CubicChebyshevGeometryEncoder.coldStart();

    @Test void coldSharedStateExactlyMatchesFixedGeometryState() {
        var coordinates=coordinates(.35,-.22,.92,-.41,.37,-.81);
        for(double radius:new double[]{.8,1.4,3,6}) {
            var shared=new GeometryConditionedHydrogenMoleculeState(radius,COLD).evaluateWithDerivatives(coordinates);
            var fixed=new HydrogenMoleculeCorrelatedState(radius,new ParameterVector(List.of(1d,0d,0d,0d,0d)))
                    .evaluateWithDerivatives(coordinates);
            assertEquals(fixed.value().real(),shared.value().real(),1e-14);
            assertEquals(fixed.coordinateLaplacian().value().real(),shared.coordinateLaplacian().value().real(),1e-12);
        }
    }

    @Test void sharedParameterAndGeometryDerivativesMatchIndependentFiniteDifferences() {
        List<Double> values=new ArrayList<>(COLD.values());
        for(int i=0;i<values.size();i++) values.set(i,values.get(i)+(i%5-2)*.003);
        var parameters=new ParameterVector(values);double radius=1.4,step=1e-6;
        var coordinates=coordinates(.35,-.22,.92,-.41,.37,-.81);
        var state=new GeometryConditionedHydrogenMoleculeState(radius,parameters);
        var bundle=state.evaluateWithGeometryDerivatives(coordinates);
        for(int index=0;index<parameters.values().size();index++) {
            List<Double> minus=new ArrayList<>(values),plus=new ArrayList<>(values);
            minus.set(index,minus.get(index)-step);plus.set(index,plus.get(index)+step);
            double fd=(new GeometryConditionedHydrogenMoleculeState(radius,new ParameterVector(plus)).value(coordinates).real()
                    -new GeometryConditionedHydrogenMoleculeState(radius,new ParameterVector(minus)).value(coordinates).real())/(2*step);
            assertEquals(fd,bundle.stateEvaluation().parameterGradient().derivatives().get(index).real(),2e-7);
        }
        double logFd=(Math.log(state.atGeometry(radius+step).value(coordinates).real())
                -Math.log(state.atGeometry(radius-step).value(coordinates).real()))/(2*step);
        assertEquals(logFd,bundle.geometryLogDerivative(),2e-7);
    }

    @Test void exchangeAndNuclearInterchangeSymmetryAreExact() {
        var state=new GeometryConditionedHydrogenMoleculeState(1.4,COLD);
        double original=state.value(coordinates(.3,-.2,.9,-.4,.5,-.8)).real();
        assertEquals(original,state.value(coordinates(-.4,.5,-.8,.3,-.2,.9)).real(),1e-14);
        assertEquals(original,state.value(coordinates(.3,-.2,-.9,-.4,.5,.8)).real(),1e-14);
        assertTrue(Double.isFinite(original));
    }

    private static QuantumCoordinates coordinates(double... xyz) {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,xyz[0],xyz[1],xyz[2],SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,xyz[3],xyz[4],xyz[5],SpinProjection.BETA)));
    }
}
