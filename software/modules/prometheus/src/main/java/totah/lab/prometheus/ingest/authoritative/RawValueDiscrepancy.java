package totah.lab.prometheus.ingest.authoritative;

import java.util.Objects;

import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;

/** Numerical comparison between independently parsed raw and historical values. */
public record RawValueDiscrepancy(
        String field,
        double authoritativeValue,
        double historicalValue,
        double absoluteDifference,
        FieldSourceProvenance authoritativeSource,
        FieldSourceProvenance historicalSource) {
    public RawValueDiscrepancy {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(authoritativeSource, "authoritativeSource");
        Objects.requireNonNull(historicalSource, "historicalSource");
    }

    /** Compares two sourced scalar fields without discarding either provenance chain. */
    public static RawValueDiscrepancy compare(
            String field, RecoveredField<Double> authoritative, RecoveredField<Double> historical) {
        double authoritativeValue = authoritative.value().orElseThrow();
        double historicalValue = historical.value().orElseThrow();
        return new RawValueDiscrepancy(
                field,
                authoritativeValue,
                historicalValue,
                Math.abs(authoritativeValue - historicalValue),
                authoritative.provenance().getFirst(),
                historical.provenance().getFirst());
    }
}
