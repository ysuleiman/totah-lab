package totah.lab.prometheus.potential.hybrid;

import java.util.Objects;
import totah.lab.prometheus.potential.ConservativePotential;
import totah.lab.prometheus.potential.PotentialEvaluation;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.baseline.BaselinePotential;
import totah.lab.prometheus.potential.delta.DeltaPotential;

/** Conservative composition E=Ebaseline+DeltaE, F=Fbaseline+DeltaF. */
public final class HybridPotential implements ConservativePotential {
    private final BaselinePotential baseline;
    private final DeltaPotential delta;
    public HybridPotential(BaselinePotential baseline, DeltaPotential delta) {
        this.baseline = Objects.requireNonNull(baseline); this.delta = Objects.requireNonNull(delta);
    }
    @Override public PotentialEvaluation evaluate(QuantumCoordinates coordinates) {
        PotentialEvaluation left = baseline.evaluate(coordinates), right = delta.evaluate(coordinates);
        double[][] forces = left.forces(), correction = right.forces();
        if (forces.length != correction.length) throw new IllegalStateException("potential atom counts differ");
        for (int atom=0; atom<forces.length; atom++) for (int axis=0; axis<3; axis++) forces[atom][axis] += correction[atom][axis];
        return new PotentialEvaluation(left.energy() + right.energy(), forces);
    }
}
