package totah.lab.mettl7.triage;

import totah.lab.athena.ligand.screening.Mettl7bEnrichmentGate;

import java.util.Objects;

/**
 * METTL7-owned compatibility facade over Athena's existing implementation.
 * This is migration phase one: callers can move without copying the algorithm
 * or breaking the existing Athena public API.
 */
public final class Mettl7bEnrichmentPolicy {
    private final Mettl7bEnrichmentGate delegate;

    public Mettl7bEnrichmentPolicy() {
        this(new Mettl7bEnrichmentGate());
    }

    public Mettl7bEnrichmentPolicy(Mettl7bEnrichmentGate delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public Mettl7bEnrichmentGate.Result evaluate(Mettl7bEnrichmentGate.Cohort cohort,
                                                  Mettl7bEnrichmentGate.Evidence evidence) {
        return delegate.evaluate(cohort, evidence);
    }
}
