package totah.lab.mnemosyne;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Immutable scalar projection; source receipts and domain payloads remain authoritative elsewhere. */
public record Observation(ScientificReference reference, ScientificReference subject,
                          ScientificReference endpoint, ScientificReference method,
                          ScientificReference context, ScientificReference activity,
                          Provenance provenance, Availability availability, Optional<Scalar> value,
                          Uncertainty uncertainty, List<String> limitations) {
    /** Availability is not scientific validity, review approval or truth. */
    public enum Availability { PRESENT, UNAVAILABLE, FAILED_INVALID }

    public Observation {
        reference.require(OBSERVATION); subject.require(SUBJECT); endpoint.require(ENDPOINT);
        method.require(METHOD); context.require(CONTEXT); activity.require(ACTIVITY);
        Objects.requireNonNull(provenance); Objects.requireNonNull(availability);
        value = Objects.requireNonNull(value); Objects.requireNonNull(uncertainty);
        limitations = List.copyOf(limitations);
        if ((availability == Availability.PRESENT) != value.isPresent())
            throw new IllegalArgumentException("only PRESENT observations carry a scalar value");
        if (value.isEmpty() && !(uncertainty instanceof Unknown))
            throw new IllegalArgumentException("unavailable value cannot carry quantitative uncertainty");
        if (uncertainty instanceof Interval interval && !interval.lower().unit().equals(value.orElseThrow().unit()))
            throw new IllegalArgumentException("interval and value units differ");
        if (uncertainty instanceof ReportedError error && !error.error().unit().equals(value.orElseThrow().unit()))
            throw new IllegalArgumentException("error and value units differ; conversions must be explicit");
    }

    /** Original numeric spelling and unit are preserved; no implicit normalization or conversion. */
    public record Scalar(String text, String unit) {
        public Scalar {
            ScientificReference.text(text); ScientificReference.text(unit);
            new BigDecimal(text); // Reject NaN/infinity and unparseable numbers without binary rounding.
        }
        public BigDecimal decimal() { return new BigDecimal(text); }
    }

    public sealed interface Uncertainty permits Unknown, Interval, ReportedError { }
    public record Unknown(String reason) implements Uncertainty {
        public Unknown { ScientificReference.text(reason); }
    }
    /** Bounds of a reported interval; its descriptive type is retained, not assumed to be a confidence interval. */
    public record Interval(Scalar lower, Scalar upper, String description) implements Uncertainty {
        public Interval {
            Objects.requireNonNull(lower); Objects.requireNonNull(upper); ScientificReference.text(description);
            if (!lower.unit().equals(upper.unit()) || lower.decimal().compareTo(upper.decimal()) > 0)
                throw new IllegalArgumentException("invalid reported interval");
        }
    }
    /** Quantity identifies e.g. standard error or standard deviation; neither is invented from a point value. */
    public record ReportedError(ScientificReference quantity, Scalar error, String description) implements Uncertainty {
        public ReportedError {
            quantity.require(ENDPOINT); Objects.requireNonNull(error); ScientificReference.text(description);
            if (error.decimal().signum() < 0) throw new IllegalArgumentException("negative reported error");
        }
    }

    /** References verified domain receipts; it does not implement a second hashing or artifact store. */
    public record Provenance(ScientificReference source, ScientificReference artifact,
                             ScientificReference receipt, ScientificReference projectionMethod,
                             String locator, List<ScientificReference> derivedFrom) {
        public Provenance {
            source.require(SOURCE); artifact.require(ARTIFACT); receipt.require(RECEIPT);
            projectionMethod.require(METHOD); ScientificReference.text(locator);
            derivedFrom = List.copyOf(derivedFrom);
        }
    }
}
