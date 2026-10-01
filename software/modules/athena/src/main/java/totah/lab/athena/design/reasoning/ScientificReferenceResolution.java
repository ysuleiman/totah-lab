package totah.lab.athena.design.reasoning;

import totah.lab.athena.design.generation.MolecularDesignTree.Reference;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.mnemosyne.Observation;
import totah.lab.mnemosyne.ReferenceResolver;
import totah.lab.mnemosyne.Review;
import totah.lab.mnemosyne.ScientificReference;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;

/** Resolves a pinned projection through the existing source-review registry and an injected source authority. */
public final class ScientificReferenceResolution implements ReferenceResolver.Authority<SourceDecision> {
    /** Host must delegate to its existing verified source importer; no parsing or source I/O is moved here. */
    @FunctionalInterface public interface SourceVerifier { void verify(SourceReceipt receipt) throws IOException; }
    private final Path registry;
    private final Reference evidence;
    private final Observation expected;
    private final Review reviewed;
    private final SourceVerifier verifier;

    public ScientificReferenceResolution(Path registry, Reference evidence, Observation expected, Review reviewed, SourceVerifier verifier) {
        this.registry = java.util.Objects.requireNonNull(registry); this.evidence = java.util.Objects.requireNonNull(evidence);
        this.expected = java.util.Objects.requireNonNull(expected); this.reviewed = java.util.Objects.requireNonNull(reviewed);
        this.verifier = java.util.Objects.requireNonNull(verifier);
        if (!reviewed.observation().equals(expected.reference())) throw new IllegalArgumentException("review projection mismatch");
    }
    @Override public boolean supports(ScientificReference requested) {
        return references(expected, reviewed.reference()).stream().anyMatch(r -> r.kind() == requested.kind()
                && r.namespace().equals(requested.namespace()) && r.version().equals(requested.version()));
    }
    @Override public Optional<SourceDecision> find(ScientificReference requested) throws IOException {
        if (!references(expected, reviewed.reference()).contains(requested)) return Optional.empty();
        try { return Optional.of(new DesignGrammarJsonCodec().readSourceReview(registry, evidence)); }
        catch (NoSuchFileException missing) { throw missing; }
        catch (IOException invalid) { throw new ReferenceResolver.Conflict("source-review authority rejected record", invalid); }
    }
    @Override public ScientificReference identityOf(SourceDecision record, ScientificReference.Kind kind, String namespace) throws IOException {
        var projected = ScientificObservationAdapter.project(record.receipt(), expected.reference(), expected.activity());
        var review = ScientificObservationAdapter.review(projected, record, reviewed.policy(), reviewed.recordedAt());
        return references(projected, review.reference()).stream().filter(r -> r.kind() == kind && r.namespace().equals(namespace))
                .findFirst().orElseThrow(() -> new ReferenceResolver.Conflict("domain record has no requested identity axis"));
    }
    @Override public ReferenceResolver.Verification verify(SourceDecision record) throws IOException {
        try { verifier.verify(record.receipt()); }
        catch (IOException invalid) { throw new ReferenceResolver.Conflict("source importer verification failed", invalid); }
        var projected = ScientificObservationAdapter.project(record.receipt(), expected.reference(), expected.activity());
        if (!projected.equals(expected) || !ScientificObservationAdapter.review(projected, record, reviewed.policy(), reviewed.recordedAt()).equals(reviewed))
            throw new ReferenceResolver.Conflict("authoritative source/review differs from pinned projection");
        return new ReferenceResolver.Verification(List.of(projected.provenance().artifact()),
                "Source-review registry decoded; host source importer reverified embedded raw bytes and source semantics; historical linkage only");
    }
    private static List<ScientificReference> references(Observation observation, ScientificReference review) {
        return List.of(observation.provenance().source(), observation.provenance().artifact(), observation.provenance().receipt(), review);
    }
}
