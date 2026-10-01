package totah.lab.mnemosyne;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** One attributed admissibility decision about an unchanged observation, under a scoped policy. */
public record Review(ScientificReference reference, ScientificReference observation,
                     ScientificReference reviewer, ScientificReference process,
                     ScientificReference policy, ScientificReference scope, Decision decision,
                     List<String> reasons, Instant reviewedAt, Instant recordedAt,
                     List<ScientificReference> qualifications, List<String> limitations) {
    public enum Decision { ACCEPTED, REJECTED }
    public Review {
        reference.require(REVIEW); observation.require(OBSERVATION); reviewer.require(AGENT);
        process.require(METHOD); policy.require(POLICY); scope.require(CONTEXT);
        Objects.requireNonNull(decision); Objects.requireNonNull(reviewedAt); Objects.requireNonNull(recordedAt);
        reasons = List.copyOf(reasons); qualifications = List.copyOf(qualifications); limitations = List.copyOf(limitations);
        if (reasons.isEmpty()) throw new IllegalArgumentException("review reasons required");
        reasons.forEach(ScientificReference::text);
        if (recordedAt.isBefore(reviewedAt)) throw new IllegalArgumentException("review recorded before it occurred");
    }
}
