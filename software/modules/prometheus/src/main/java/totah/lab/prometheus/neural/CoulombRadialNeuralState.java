package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.ParameterGradient;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumAmplitude;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.StateGradient;
import totah.lab.prometheus.variational.StateLaplacian;

/**
 * Physics-informed hydrogenic state psi(r)=exp(-Zr)[1+r^2 N(r)].
 * The envelope enforces the nuclear cusp and physical asymptotic decay while
 * the Prometheus-owned network represents a smooth, trainable radial correction.
 */
public final class CoulombRadialNeuralState implements DifferentiableQuantumState {
    private static final double ORIGIN_TOLERANCE=1e-14;
    private final int nuclearCharge;
    private final FeedForwardNetwork network;

    public CoulombRadialNeuralState(int nuclearCharge,FeedForwardNetwork network) {
        if(nuclearCharge<1) throw new IllegalArgumentException("nuclearCharge must be positive");
        this.nuclearCharge=nuclearCharge;
        this.network=Objects.requireNonNull(network,"network");
    }

    @Override public String representationId() { return "coulomb-radial-neural-state-v1"; }
    @Override public ParameterVector parameters() { return network.parameters(); }
    @Override public CoulombRadialNeuralState withParameters(ParameterVector parameters) {
        return new CoulombRadialNeuralState(nuclearCharge,network.withParameters(parameters));
    }

    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        if(coordinates.particles().size()!=1) throw new IllegalArgumentException("one electron is required");
        var particle=coordinates.particles().getFirst();
        double x=particle.xBohr(),y=particle.yBohr(),z=particle.zBohr();
        double r=Math.sqrt(x*x+y*y+z*z);
        NetworkEvaluation n=network.evaluate(r);
        double exponential=Math.exp(-nuclearCharge*r);
        double correction=1.0+r*r*n.value();
        double correctionFirst=2.0*r*n.value()+r*r*n.inputFirstDerivative();
        double correctionSecond=2.0*n.value()+4.0*r*n.inputFirstDerivative()
                +r*r*n.inputSecondDerivative();
        double radialFirst=exponential*(correctionFirst-nuclearCharge*correction);
        double radialSecond=exponential*(correctionSecond-2.0*nuclearCharge*correctionFirst
                +nuclearCharge*nuclearCharge*correction);
        double value=exponential*correction;
        double gx=0.0,gy=0.0,gz=0.0,laplacian;
        if(r>ORIGIN_TOLERANCE) {
            gx=radialFirst*x/r; gy=radialFirst*y/r; gz=radialFirst*z/r;
            laplacian=radialSecond+2.0*radialFirst/r;
        } else {
            // The Coulombic wavefunction has a directional cusp at the nucleus.
            // Its Cartesian gradient and Laplacian are therefore not defined at r=0.
            laplacian=Double.NEGATIVE_INFINITY;
        }
        List<QuantumAmplitude> parameterGradient=new ArrayList<>();
        double parameterEnvelope=exponential*r*r;
        n.parameterGradient().forEach(derivative ->
                parameterGradient.add(QuantumAmplitude.real(parameterEnvelope*derivative)));
        String identity=CanonicalHashing.sha256Hex(representationId()+"|"+nuclearCharge+"|"+x+"|"+y+"|"+z
                +"|"+parameters().values());
        return new DifferentiableStateEvaluation(QuantumAmplitude.real(value),
                new StateGradient(List.of(new StateGradient.Vector3(QuantumAmplitude.real(gx),
                        QuantumAmplitude.real(gy),QuantumAmplitude.real(gz)))),
                new StateLaplacian(QuantumAmplitude.real(laplacian)),new ParameterGradient(parameterGradient),identity);
    }
}
