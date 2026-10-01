package totah.lab.mnemosyne;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Explicit policy judgment, not an automatic inference engine or an unqualified truth edge. */
public record Assessment(ScientificReference reference, Optional<ScientificReference> observation,
                         Optional<ScientificReference> review, ScientificReference proposition,
                         ScientificReference criterion, ScientificReference policy,
                         ScientificReference context, Outcome outcome, List<String> reasons,
                         Instant recordedAt) {
    public enum Outcome { SUPPORTS, CONTRADICTS, UNRESOLVED, FAILED_INVALID, NOT_MEASURED }
    public Assessment {
        reference.require(ASSESSMENT); proposition.require(PROPOSITION); criterion.require(CRITERION);
        policy.require(POLICY); context.require(CONTEXT); Objects.requireNonNull(outcome);
        observation = Objects.requireNonNull(observation); review = Objects.requireNonNull(review);
        observation.ifPresent(r -> r.require(OBSERVATION)); review.ifPresent(r -> r.require(REVIEW));
        reasons = List.copyOf(reasons); Objects.requireNonNull(recordedAt);
        if (reasons.isEmpty()) throw new IllegalArgumentException("assessment reasons required");
        reasons.forEach(ScientificReference::text);
        if (outcome == Outcome.NOT_MEASURED) {
            if (observation.isPresent() || review.isPresent())
                throw new IllegalArgumentException("NOT_MEASURED is coverage, not a fabricated observation");
        } else if (observation.isEmpty() || review.isEmpty()) {
            throw new IllegalArgumentException("measured assessments require observation and review");
        }
    }
}
