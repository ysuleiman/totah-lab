package totah.lab.prometheus.potential.delta.training;

import java.util.Objects;

/** Fit entry point enforcing structural authorization before numerical training. */
public final class DeltaModelTrainer {
    public void requirePreflight(BasisPreflightResult preflight){if(!Objects.requireNonNull(preflight).pass())throw new IllegalStateException("BASIS_PREFLIGHT_PASS is false; fitting is prohibited");}
}
