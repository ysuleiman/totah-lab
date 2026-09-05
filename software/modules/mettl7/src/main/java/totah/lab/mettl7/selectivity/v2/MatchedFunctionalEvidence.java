package totah.lab.mettl7.selectivity.v2;

import java.util.Objects;

/** Matched A/B evidence only; structural priors deliberately live outside this record. */
public record MatchedFunctionalEvidence(
        String identifier,
        boolean mettl7aEffect,
        boolean mettl7bEffect,
        boolean mettl7aRetained,
        boolean mettl7bRetained,
        boolean productiveInA,
        boolean productiveInB,
        boolean directBindingEstablished,
        String provenance) {
    public MatchedFunctionalEvidence {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(provenance, "provenance");
    }
}
