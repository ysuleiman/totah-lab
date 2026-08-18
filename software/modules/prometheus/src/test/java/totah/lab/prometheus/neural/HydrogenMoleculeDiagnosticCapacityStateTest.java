package totah.lab.prometheus.neural;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

class HydrogenMoleculeDiagnosticCapacityStateTest {
    private static final ParameterVector BASE=new ParameterVector(List.of(.6,-.2,.1,.05,-.08));

    @Test void zeroInitializedDiagnosticFeaturesExactlyRecoverFrozenRepresentation() {
        var coordinates=coordinates(.31,-.22,.61,-.42,.37,-.53);
        var baseline=new HydrogenMoleculeCorrelatedState(1.0,BASE).evaluateWithDerivatives(coordinates);
        var backflow=new HydrogenMoleculeCorrelatedState(1.0,
                new ParameterVector(List.of(.6,-.2,.1,.05,-.08,0.0))).evaluateWithDerivatives(coordinates);
        var expanded=new HydrogenMoleculeCorrelatedState(1.0,
                new ParameterVector(List.of(.6,-.2,.1,.05,-.08,0.0,0.0,0.0))).evaluateWithDerivatives(coordinates);

        assertThat(backflow.value().real()).isEqualTo(baseline.value().real());
        assertThat(backflow.coordinateLaplacian().value().real()).isEqualTo(baseline.coordinateLaplacian().value().real());
        assertThat(expanded.value().real()).isEqualTo(baseline.value().real());
        assertThat(expanded.coordinateLaplacian().value().real()).isEqualTo(baseline.coordinateLaplacian().value().real());
    }

    @Test void diagnosticFeaturesRetainElectronExchangeSymmetry() {
        var expanded=new HydrogenMoleculeCorrelatedState(1.0,
                new ParameterVector(List.of(.6,-.2,.1,.05,-.08,.03,-.02,.04)));
        double forward=expanded.value(coordinates(.31,-.22,.61,-.42,.37,-.53)).real();
        double exchanged=expanded.value(coordinates(-.42,.37,-.53,.31,-.22,.61)).real();
        assertThat(exchanged).isEqualTo(forward);
    }

    private static QuantumCoordinates coordinates(double... xyz) {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,xyz[0],xyz[1],xyz[2],SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,xyz[3],xyz[4],xyz[5],SpinProjection.BETA)));
    }
}
