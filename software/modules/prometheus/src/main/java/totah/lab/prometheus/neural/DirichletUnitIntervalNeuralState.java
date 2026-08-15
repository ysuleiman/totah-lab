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

/** Lagaris-style trial state psi(x)=x(1-x)N(x), enforcing exact box boundaries. */
public final class DirichletUnitIntervalNeuralState implements DifferentiableQuantumState {
    private final FeedForwardNetwork network;

    public DirichletUnitIntervalNeuralState(FeedForwardNetwork network) {
        this.network=Objects.requireNonNull(network,"network");
    }

    @Override public String representationId() { return "lagaris-dirichlet-unit-interval-v1"; }
    @Override public ParameterVector parameters() { return network.parameters(); }
    @Override public DirichletUnitIntervalNeuralState withParameters(ParameterVector parameters) {
        return new DirichletUnitIntervalNeuralState(network.withParameters(parameters));
    }

    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        if(coordinates.particles().size()!=1) throw new IllegalArgumentException("one particle is required");
        double x=coordinates.particles().getFirst().xBohr(); NetworkEvaluation n=network.evaluate(x);
        double envelope=x*(1.0-x), firstEnvelope=1.0-2.0*x, secondEnvelope=-2.0;
        double value=envelope*n.value();
        double first=firstEnvelope*n.value()+envelope*n.inputFirstDerivative();
        double second=secondEnvelope*n.value()+2.0*firstEnvelope*n.inputFirstDerivative()
                +envelope*n.inputSecondDerivative();
        List<QuantumAmplitude> parameterGradient=new ArrayList<>();
        n.parameterGradient().forEach(derivative -> parameterGradient.add(QuantumAmplitude.real(envelope*derivative)));
        String identity=CanonicalHashing.sha256Hex(representationId()+"|"+x+"|"+parameters().values());
        return new DifferentiableStateEvaluation(QuantumAmplitude.real(value),
                new StateGradient(List.of(new StateGradient.Vector3(QuantumAmplitude.real(first),
                        QuantumAmplitude.real(0),QuantumAmplitude.real(0)))),
                new StateLaplacian(QuantumAmplitude.real(second)),
                new ParameterGradient(parameterGradient),identity);
    }
}
