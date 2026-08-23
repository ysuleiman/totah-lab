package totah.lab.prometheus.potential.delta;

import java.util.Objects;
import totah.lab.prometheus.potential.PotentialEvaluation;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.delta.model.LinearDeltaModel;

/** Stable runtime facade for a frozen delta model. */
public final class DeltaPotentialModel implements DeltaPotential {
    private final LinearDeltaModel model;
    public DeltaPotentialModel(LinearDeltaModel model){this.model=Objects.requireNonNull(model);}
    @Override public PotentialEvaluation evaluate(QuantumCoordinates coordinates){return model.evaluate(coordinates);}
}
