package totah.lab.prometheus.potential.baseline;

import java.util.Objects;
import totah.lab.prometheus.potential.Potential;
import totah.lab.prometheus.potential.PotentialEvaluation;
import totah.lab.prometheus.potential.QuantumCoordinates;

/** Typed boundary around an externally supplied, frozen Amber evaluator. */
public final class AmberBaselinePotential implements BaselinePotential {
    private final Potential delegate;
    public AmberBaselinePotential(Potential delegate) { this.delegate = Objects.requireNonNull(delegate); }
    @Override public PotentialEvaluation evaluate(QuantumCoordinates coordinates) { return delegate.evaluate(coordinates); }
}
