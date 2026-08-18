package totah.lab.prometheus.neural;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.force.HydrogenMoleculeSpaceWarp;

class GeometryConditionedHydrogenMoleculeDirectionalEvaluatorTest {
    @Test void totalAndBareDirectionsMatchIndependentCentralDifferences(){double radius=1.4,step=2e-6;var parameters=CubicChebyshevGeometryEncoder.coldStart();var coordinates=coordinates();double[] velocity=new double[2];for(int i=0;i<2;i++)velocity[i]=HydrogenMoleculeSpaceWarp.weightAndDerivative(coordinates.particles().get(i),radius/2).weightAtPositiveNucleus()-.5;
        var actual=new GeometryConditionedHydrogenMoleculeDirectionalEvaluator().evaluate(radius,parameters,coordinates,velocity);
        var totalPlus=new GeometryConditionedHydrogenMoleculeState(radius+step,parameters).evaluateWithDerivatives(HydrogenMoleculeSpaceWarp.transform(coordinates,radius,step).coordinates());
        var totalMinus=new GeometryConditionedHydrogenMoleculeState(radius-step,parameters).evaluateWithDerivatives(HydrogenMoleculeSpaceWarp.transform(coordinates,radius,-step).coordinates());
        var barePlus=new GeometryConditionedHydrogenMoleculeState(radius+step,parameters).evaluateWithDerivatives(coordinates);var bareMinus=new GeometryConditionedHydrogenMoleculeState(radius-step,parameters).evaluateWithDerivatives(coordinates);
        assertThat(actual.valueDirectionalDerivatives()[0]).isCloseTo((totalPlus.value().real()-totalMinus.value().real())/(2*step),org.assertj.core.data.Offset.offset(2e-9));
        assertThat(actual.laplacianDirectionalDerivatives()[0]).isCloseTo((totalPlus.coordinateLaplacian().value().real()-totalMinus.coordinateLaplacian().value().real())/(2*step),org.assertj.core.data.Offset.offset(3e-7));
        assertThat(actual.valueDirectionalDerivatives()[1]).isCloseTo((barePlus.value().real()-bareMinus.value().real())/(2*step),org.assertj.core.data.Offset.offset(2e-9));
        assertThat(actual.laplacianDirectionalDerivatives()[1]).isCloseTo((barePlus.coordinateLaplacian().value().real()-bareMinus.coordinateLaplacian().value().real())/(2*step),org.assertj.core.data.Offset.offset(3e-7));}
    private static QuantumCoordinates coordinates(){return new QuantumCoordinates(List.of(new QuantumCoordinates.ParticleCoordinate(0,.21,-.13,.48,SpinProjection.ALPHA),new QuantumCoordinates.ParticleCoordinate(1,-.31,.26,-.57,SpinProjection.BETA)));}

    @Test void directionalBundleIsExactlyInvariantToTransverseReflections(){double radius=1.4;var parameters=CubicChebyshevGeometryEncoder.coldStart();var original=coordinates();double[] velocity=velocities(original,radius);var evaluator=new GeometryConditionedHydrogenMoleculeDirectionalEvaluator();var expected=evaluator.evaluate(radius,parameters,original,velocity);
        for(var reflected:List.of(reflect(original,true),reflect(original,false))){var actual=evaluator.evaluate(radius,parameters,reflected,velocities(reflected,radius));assertThat(Double.doubleToRawLongBits(actual.value())).isEqualTo(Double.doubleToRawLongBits(expected.value()));assertThat(Double.doubleToRawLongBits(actual.laplacian())).isEqualTo(Double.doubleToRawLongBits(expected.laplacian()));assertThat(actual.valueDirectionalDerivatives()).containsExactly(expected.valueDirectionalDerivatives());assertThat(actual.laplacianDirectionalDerivatives()).containsExactly(expected.laplacianDirectionalDerivatives());}}
    private static double[] velocities(QuantumCoordinates coordinates,double radius){double[] result=new double[2];for(int i=0;i<2;i++)result[i]=HydrogenMoleculeSpaceWarp.weightAndDerivative(coordinates.particles().get(i),radius/2).weightAtPositiveNucleus()-.5;return result;}
    private static QuantumCoordinates reflect(QuantumCoordinates coordinates,boolean x){return new QuantumCoordinates(coordinates.particles().stream().map(p->new QuantumCoordinates.ParticleCoordinate(p.particleIndex(),x?-p.xBohr():p.xBohr(),x?p.yBohr():-p.yBohr(),p.zBohr(),p.spin())).toList());}
}
