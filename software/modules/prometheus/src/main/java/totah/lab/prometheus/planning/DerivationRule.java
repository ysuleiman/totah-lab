package totah.lab.prometheus.planning;

import java.util.Objects;

import totah.lab.prometheus.evidence.CalculationType;

/** A deterministic scientific value obtainable from an authoritative artifact without a new calculation. */
public record DerivationRule(CalculationType sourceType, String derivedValue, String method) {
    public DerivationRule {
        Objects.requireNonNull(sourceType, "sourceType");
        derivedValue = require(derivedValue, "derivedValue");
        method = require(method, "method");
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must be non-blank");
        return value;
    }
}
