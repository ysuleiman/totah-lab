package totah.lab.athena.design.reasoning;

import totah.lab.aether.provenance.ContentHash;
import totah.lab.mnemosyne.EvidenceHistory;
import totah.lab.mnemosyne.Observation;
import totah.lab.mnemosyne.Review;
import totah.lab.mnemosyne.ScientificReference;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;
import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;

/** Adapter only: existing verified source receipts and Athena qualification remain authoritative. */
public final class ScientificObservationAdapter {
    private ScientificObservationAdapter() { }

    /**
     * Project the host-verified frozen PubChem receipt. The host must still use Hermes readVerified;
     * this method neither fetches a source nor replaces its importer or the subsequent source review.
     * Observation/run IDs are supplied separately, never inferred from the molecular identity.
     */
    public static Observation project(SourceReceipt source, ScientificReference observation, ScientificReference activity) {
        if (!source.errors().isEmpty() || source.method() == null || source.system() == null
                || !source.provider().equals("PubChem") || !source.unit().equals("g/mol")
                || !source.importer().equals(new totah.lab.athena.design.generation.MolecularDesignTree.Reference("PubChemMolecularWeightImporter", "1"))
                || !source.endpoint().equals(new totah.lab.athena.design.generation.MolecularDesignTree.Reference("pubchem:MolecularWeight", "1"))
                || source.valueKind() != DesignKnowledge.ValueKind.QUANTITATIVE
                || source.uncertaintyKind() != UncertaintyKind.POINT_UNKNOWN)
            throw new IllegalArgumentException("unsupported molecular-weight source projection");
        // Length-prefixed fields preserve complete method/context identity without delimiter ambiguity.
        String method = encode(source.method().name(), source.method().version(), encodeMap(source.method().parameters()));
        String context = encode(source.system().id(), source.system().version(), source.context(), encodeMap(source.conditions()));
        var provenance = new Observation.Provenance(
                ref(SOURCE, source.provider(), source.identifier(), source.sourceVersion()),
                ref(ARTIFACT, "sha256", source.sha256(), "1"),
                ref(RECEIPT, "athena-source-receipt", source.identifier(), source.sha256()),
                ref(METHOD, "athena-projection", "PubChemMolecularWeight", "1"),
                source.uri() + "#MolecularWeight", List.of());
        return new Observation(observation, ref(SUBJECT, "PubChem", source.identifier(), source.sourceVersion()),
                ref(ENDPOINT, "athena-endpoint", source.endpoint().id(), source.endpoint().version()),
                ref(METHOD, "athena-method", source.method().name(), ContentHash.sha256(method)),
                ref(CONTEXT, "athena-source-context", source.system().id(), ContentHash.sha256(context)),
                activity, provenance, Observation.Availability.PRESENT,
                Optional.of(new Observation.Scalar(source.valueText(), source.unit())),
                new Observation.Unknown(source.uncertaintyText().isBlank()
                        ? "No uncertainty reported by source (POINT_UNKNOWN)" : source.uncertaintyText()),
                List.of(source.claimBoundary(), "Source warnings=" + source.warnings(),
                        "Source use=" + new TreeMap<>(source.sourceUse()), "Source importer=" + source.importer()));
    }

    /** Project an explicit existing domain review, including rejected source decisions. */
    public static Review review(Observation observation, SourceDecision decision,
                                ScientificReference policy, Instant recordedAt) {
        requireProjection(observation, decision);
        var source = decision.review();
        return new Review(ref(REVIEW, "athena-review", source.reference().id(), source.reference().version()),
                observation.reference(), ref(AGENT, "athena-agent", source.reviewer().id(), source.reviewer().version()),
                ref(METHOD, "athena-review-process", source.process().id(), source.process().version()),
                policy, observation.context(), decision.evidence() != null ? Review.Decision.ACCEPTED : Review.Decision.REJECTED,
                decision.reasons().isEmpty() ? List.of(source.reason()) : decision.reasons(),
                Instant.parse(source.reviewedAt()), recordedAt, List.of(observation.provenance().receipt()),
                List.of(String.valueOf(source.limitations()), String.valueOf(source.uncertaintyNote())));
    }

    /** Cross-layer linkage: does not rewrite the historical Evidence or embed its graph in Mnemosyne. */
    public record Binding(ScientificReference observation, ScientificReference review, SourceDecision decision) {
        public Binding {
            observation.require(OBSERVATION); review.require(REVIEW);
            if (decision == null || decision.evidence() == null)
                throw new IllegalArgumentException("qualified source decision required");
        }
        public DesignKnowledge.Evidence evidence() { return decision.evidence(); }
    }

    public static Binding bind(EvidenceHistory history, ScientificReference observationId, ScientificReference reviewId,
                               Instant knownAt, Instant effectiveAt, SourceDecision decision) {
        var observation = history.observations().get(observationId);
        var review = history.reviews().get(reviewId);
        if (observation == null || review == null || !history.admissible(reviewId, knownAt, effectiveAt)
                || decision.evidence() == null
                || !review.equals(review(observation, decision, review.policy(), review.recordedAt())))
            throw new IllegalArgumentException("binding requires the current admissible, matching domain review");
        return new Binding(observationId, reviewId, decision);
    }

    private static void requireProjection(Observation observation, SourceDecision decision) {
        if (!observation.equals(project(decision.receipt(), observation.reference(), observation.activity())))
            throw new IllegalArgumentException("observation differs from source projection");
    }
    private static ScientificReference ref(ScientificReference.Kind kind, String namespace, String id, String version) {
        return new ScientificReference(kind, namespace, id, version);
    }
    private static String encode(String... values) {
        var result = new StringBuilder();
        for (var value : values) result.append(value.length()).append(':').append(value);
        return result.toString();
    }
    private static String encodeMap(java.util.Map<String, String> values) {
        var result = new StringBuilder();
        new TreeMap<>(values).forEach((k, v) -> result.append(encode(k, v)));
        return result.toString();
    }
}
