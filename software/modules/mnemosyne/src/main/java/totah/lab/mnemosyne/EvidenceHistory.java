package totah.lab.mnemosyne;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/**
 * Bounded immutable working set for append/conflict and historical-view semantics.
 * Not persistence, a domain registry, a distributed writer or a replacement for domain lifecycle authority.
 */
public final class EvidenceHistory {
    private final Map<ScientificReference, Observation> observations;
    private final Map<ScientificReference, Review> reviews;
    private final Map<ScientificReference, Assessment> assessments;
    private final Map<ScientificReference, ReviewChange> changes;

    private final Map<ScientificReference, DiscoveryDescription> descriptions;
    private final Map<ScientificReference, DiscoveryDescription.Withdrawal> withdrawals;

    public EvidenceHistory() { this(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()); }
    private EvidenceHistory(Map<ScientificReference, Observation> observations, Map<ScientificReference, Review> reviews,
                            Map<ScientificReference, Assessment> assessments, Map<ScientificReference, ReviewChange> changes,
                            Map<ScientificReference, DiscoveryDescription> descriptions,
                            Map<ScientificReference, DiscoveryDescription.Withdrawal> withdrawals) {
        this.observations = Map.copyOf(observations); this.reviews = Map.copyOf(reviews);
        this.assessments = Map.copyOf(assessments); this.changes = Map.copyOf(changes);
        this.descriptions = Map.copyOf(descriptions); this.withdrawals = Map.copyOf(withdrawals);
    }
    public Map<ScientificReference, Observation> observations() { return observations; }
    public Map<ScientificReference, Review> reviews() { return reviews; }
    public Map<ScientificReference, Assessment> assessments() { return assessments; }
    public Map<ScientificReference, ReviewChange> changes() { return changes; }
    public EvidenceHistory append(Observation observation) {
        var next = add(observations, observation.reference(), observation);
        return next == observations ? this : new EvidenceHistory(next, reviews, assessments, changes, descriptions, withdrawals);
    }
    public EvidenceHistory append(Review review) {
        require(observations, review.observation());
        var next = add(reviews, review.reference(), review);
        return next == reviews ? this : new EvidenceHistory(observations, next, assessments, changes, descriptions, withdrawals);
    }
    public EvidenceHistory append(Assessment assessment) {
        if (assessments.containsKey(assessment.reference())) {
            add(assessments, assessment.reference(), assessment); return this;
        }
        if (assessment.observation().isPresent()) {
            var observation = require(observations, assessment.observation().orElseThrow());
            var review = require(reviews, assessment.review().orElseThrow());
            if (!review.observation().equals(observation.reference()) || !review.scope().equals(assessment.context())
                    || review.recordedAt().isAfter(assessment.recordedAt()))
                throw new IllegalArgumentException("assessment review/observation/context/time mismatch");
            boolean scientificFinding = assessment.outcome() == Assessment.Outcome.SUPPORTS
                    || assessment.outcome() == Assessment.Outcome.CONTRADICTS
                    || assessment.outcome() == Assessment.Outcome.UNRESOLVED;
            if (scientificFinding && (observation.availability() != Observation.Availability.PRESENT
                    || !admissible(review.reference(), assessment.recordedAt(), assessment.recordedAt())))
                throw new IllegalArgumentException("finding requires an admissible present observation");
        }
        return new EvidenceHistory(observations, reviews, add(assessments, assessment.reference(), assessment), changes, descriptions, withdrawals);
    }

    /** Historical records are never removed. Retraction/supersession only changes scoped admissibility. */
    public record ReviewChange(ScientificReference reference, ScientificReference review,
                               Optional<ScientificReference> replacement, ScientificReference agent,
                               String reason, Instant effectiveAt, Instant recordedAt) {
        public ReviewChange {
            reference.require(REVIEW_CHANGE); review.require(REVIEW); agent.require(AGENT);
            replacement = Objects.requireNonNull(replacement); replacement.ifPresent(r -> r.require(REVIEW));
            ScientificReference.text(reason); Objects.requireNonNull(effectiveAt); Objects.requireNonNull(recordedAt);
            if (recordedAt.isBefore(effectiveAt)) throw new IllegalArgumentException("change recorded before effective time");
            if (replacement.filter(review::equals).isPresent()) throw new IllegalArgumentException("self supersession");
        }
    }
    public EvidenceHistory append(ReviewChange change) {
        if (changes.containsKey(change.reference())) { add(changes, change.reference(), change); return this; }
        var old = require(reviews, change.review());
        if (old.recordedAt().isAfter(change.recordedAt()) || old.reviewedAt().isAfter(change.effectiveAt()))
            throw new IllegalArgumentException("change precedes reviewed decision");
        if (changes.values().stream().anyMatch(c -> c.review().equals(change.review())))
            throw new IllegalArgumentException("review already withdrawn; revise the replacement instead");
        change.replacement().ifPresent(id -> {
            var replacement = require(reviews, id);
            if (!replacement.observation().equals(old.observation()) || !replacement.scope().equals(old.scope())
                    || !replacement.reviewer().equals(old.reviewer())
                    || replacement.recordedAt().isAfter(change.recordedAt())
                    || replacement.reviewedAt().isAfter(change.effectiveAt())
                    || !replacement.reviewedAt().isAfter(old.reviewedAt()))
                throw new IllegalArgumentException("replacement must be a later review of the same scoped observation by the same reviewer");
        });
        return new EvidenceHistory(observations, reviews, assessments, add(changes, change.reference(), change), descriptions, withdrawals);
    }

    public Map<ScientificReference, DiscoveryDescription> descriptions() { return descriptions; }
    public Map<ScientificReference, DiscoveryDescription.Withdrawal> withdrawals() { return withdrawals; }

    public EvidenceHistory append(DiscoveryDescription description) throws IOException {
        var observation = require(observations, description.observation());
        if (!new EvidenceExchange().contentDigest(observation).equals(description.observationSha256()))
            throw new IllegalArgumentException("discovery observation digest mismatch");
        var next = add(descriptions, description.reference(), description);
        return next == descriptions ? this : new EvidenceHistory(observations, reviews, assessments, changes, next, withdrawals);
    }
    public EvidenceHistory append(DiscoveryDescription.Withdrawal withdrawal) {
        if (withdrawals.containsKey(withdrawal.reference())) { add(withdrawals, withdrawal.reference(), withdrawal); return this; }
        var old = require(descriptions, withdrawal.description());
        if (!old.agent().equals(withdrawal.agent()) || old.recordedAt().isAfter(withdrawal.recordedAt()))
            throw new IllegalArgumentException("withdrawal attribution/time mismatch");
        if (withdrawals.values().stream().anyMatch(w -> w.description().equals(old.reference())))
            throw new IllegalArgumentException("description already withdrawn");
        withdrawal.replacement().ifPresent(id -> {
            var replacement = require(descriptions, id);
            if (!replacement.observation().equals(old.observation()) || !replacement.agent().equals(old.agent())
                    || !replacement.recordedAt().isAfter(old.recordedAt()) || replacement.recordedAt().isAfter(withdrawal.recordedAt()))
                throw new IllegalArgumentException("replacement must be a later description of the same observation by the same agent");
        });
        return new EvidenceHistory(observations, reviews, assessments, changes, descriptions,
                add(withdrawals, withdrawal.reference(), withdrawal));
    }

    public boolean admissible(ScientificReference reviewId, Instant knownAt, Instant effectiveAt) {
        var review = require(reviews, reviewId);
        return review.decision() == Review.Decision.ACCEPTED && !review.recordedAt().isAfter(knownAt)
                && !review.reviewedAt().isAfter(effectiveAt)
                && changes.values().stream().noneMatch(c -> c.review().equals(reviewId)
                    && !c.recordedAt().isAfter(knownAt) && !c.effectiveAt().isAfter(effectiveAt));
    }
    /** Original interpretation as known then; later retractions do not rewrite historical assessments. */
    public List<Assessment> assessmentsAsOf(Instant knownAt) {
        return assessments.values().stream().filter(a -> !a.recordedAt().isAfter(knownAt))
                .sorted(java.util.Comparator.comparing((Assessment a) -> a.recordedAt())
                        .thenComparing(a -> a.reference().toString())).toList();
    }
    private static <T> T require(Map<ScientificReference, T> records, ScientificReference id) {
        var value = records.get(id);
        if (value == null) throw new IllegalArgumentException("unresolved reference: " + id);
        return value;
    }
    private static <T> Map<ScientificReference, T> add(Map<ScientificReference, T> records, ScientificReference id, T value) {
        var existing = records.get(id);
        if (existing != null) {
            if (!existing.equals(value)) throw new IllegalArgumentException("immutable identity conflict: " + id);
            return records;
        }
        var next = new LinkedHashMap<>(records); next.put(id, value); return next;
    }
}
