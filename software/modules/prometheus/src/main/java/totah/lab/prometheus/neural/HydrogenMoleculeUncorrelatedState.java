package totah.lab.prometheus.neural;

import java.util.List;

import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Uncorrelated H2 baseline: a doubly occupied cusp-safe molecular-orbital product without r12. */
public final class HydrogenMoleculeUncorrelatedState implements DifferentiableQuantumState {
    private final HydrogenMoleculeCorrelatedState delegate;
    public HydrogenMoleculeUncorrelatedState(double bondLengthBohr,double localization) {
        delegate=new HydrogenMoleculeCorrelatedState(bondLengthBohr,
                new ParameterVector(List.of(localization,0.0,0.0,0.0,0.0)),false);
    }
    @Override public String representationId() { return "h2-uncorrelated-covalent-baseline-v1"; }
    @Override public ParameterVector parameters() { return delegate.parameters(); }
    @Override public HydrogenMoleculeUncorrelatedState withParameters(ParameterVector parameters) {
        return new HydrogenMoleculeUncorrelatedState(delegate.bondLengthBohr(),parameters.values().getFirst());
    }
    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        return delegate.evaluateWithDerivatives(coordinates);
    }
}
