package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.knowledge.ContextualTransformationEffects;
import totah.lab.athena.design.knowledge.ContextualTransformationEffects.*;
import totah.lab.athena.design.knowledge.MatchedPairExtractor.*;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic provenance revisions; not claims of compatibility between real resource releases. */
class ContextualProvenanceAuditTest {
    private final ContextualTransformationEffects service = new ContextualTransformationEffects();
    private final Context context = new Context("synthetic-target", "endpoint", "method", "assay",
            "conditions/v1", "dimensionless", "scale", "qualification/v1");

    private Pair pair() throws Exception {
        var backend = new OclMolecularBackend();
        var result = new OclMatchedPairExtractor().extract(List.of(
                new Source("a", "synthetic-dataset/v1", backend.decodeStructure("SMILES", "Oc1ccccc1"), List.of("oa")),
                new Source("b", "synthetic-dataset/v1", backend.decodeStructure("SMILES", "Nc1ccccc1"), List.of("ob"))), 1);
        assertEquals(1, result.pairs().size(), result.issues().toString());
        return result.pairs().getFirst();
    }
    private List<Measurement> measurements(Pair p) {
        return List.of(new Measurement(p.left().observationReferences().getFirst(), p.leftIdentity(), context,
                        "synthetic-study", "replicate-left", 5, 5, "="),
                new Measurement(p.right().observationReferences().getFirst(), p.rightIdentity(), context,
                        "synthetic-study", "replicate-right", 6, 6, "="));
    }
    private Source revision(Source source) {
        return new Source(source.id(), "synthetic-dataset/v2", source.graph(), source.observationReferences());
    }

    @Test void singleVersionProvenanceSurvivesAnalysisAndSummary() throws Exception {
        var p = pair();
        var observations = measurements(p);
        var analysis = service.analyze(List.of(p), observations, 0.3);
        assertTrue(analysis.rejections().isEmpty());
        assertEquals(1, analysis.effects().size());
        var effect = analysis.effects().getFirst();
        assertEquals(p, effect.pair());
        assertEquals(observations.getFirst(), effect.left());
        assertEquals(observations.getLast(), effect.right());
        assertTrue(p.algorithm().contains(OclMolecularBackend.VERSION));
        assertEquals(OclMatchedPairExtractor.ALGORITHM, p.algorithm());
        var summary = service.summarize(analysis.effects(), p.transformation(), context,
                Optional.of(p.leftFragment().chemicalContext()));
        assertEquals(analysis.effects(), summary.effects());
        assertEquals(context, summary.scientificContext());
        assertEquals(ContextualTransformationEffects.METHOD, summary.method());
        assertEquals(1, summary.pairCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"algorithm", "dataset", "left-dataset", "right-dataset"})
    void mixedVersionDeduplicationMustNotSelectLineageByInputOrder(String changed) throws Exception {
        var p = pair();
        var revised = new Pair((changed.equals("dataset") || changed.equals("left-dataset")) ? revision(p.left()) : p.left(),
                (changed.equals("dataset") || changed.equals("right-dataset")) ? revision(p.right()) : p.right(), p.leftIdentity(), p.rightIdentity(),
                p.leftFragment(), p.rightFragment(), p.coreCorrespondence(),
                changed.equals("algorithm") ? "synthetic-unqualified-extractor/vNext" : p.algorithm());
        assertNotEquals(p, revised);
        var forward = service.analyze(List.of(p, revised), measurements(p), 0.3);
        var reversed = service.analyze(List.of(revised, p), measurements(p).reversed(), 0.3);
        assertTrue(forward.effects().isEmpty());
        assertEquals(List.of(new Rejection(measurements(p).getFirst().reference(), measurements(p).getLast().reference(),
                "CONFLICTING_PROVENANCE")), forward.rejections());
        assertEquals(forward, service.analyze(List.of(p, revised, p, revised), measurements(p), 0.3));
        var summary = service.summarize(forward.effects(), p.transformation(), context, Optional.empty());
        assertEquals(0, summary.pairCount());
        assertTrue(summary.mean().isEmpty());
        assertEquals(forward, reversed, "versioned lineage must be invariant under replay ordering");
    }

    @ParameterizedTest
    @ValueSource(strings = {"algorithm", "dataset", "left-dataset", "right-dataset"})
    void directSummaryOfSeparateBatchesMustNotRestoreConflictingSupport(String changed) throws Exception {
        var p = pair();
        var revised = new Pair((changed.equals("dataset") || changed.equals("left-dataset")) ? revision(p.left()) : p.left(),
                (changed.equals("dataset") || changed.equals("right-dataset")) ? revision(p.right()) : p.right(),
                p.leftIdentity(), p.rightIdentity(), p.leftFragment(), p.rightFragment(), p.coreCorrespondence(),
                changed.equals("algorithm") ? "synthetic-unqualified-extractor/vNext" : p.algorithm());
        // Each batch is individually eligible; merging their real analysis outputs must
        // not bypass the abstention applied when those same pairs are analyzed together.
        var first = service.analyze(List.of(p), measurements(p), 0.3);
        var second = service.analyze(List.of(revised), measurements(p), 0.3);
        assertEquals(1, first.effects().size());
        assertEquals(1, second.effects().size());
        var together = service.analyze(List.of(p, revised), measurements(p), 0.3);
        assertTrue(together.effects().isEmpty());
        assertEquals("CONFLICTING_PROVENANCE", together.rejections().getFirst().reason());
        var merged = List.of(first.effects().getFirst(), second.effects().getFirst());
        for (var filter : List.of(Optional.<String>empty(), Optional.of(p.leftFragment().chemicalContext()))) {
            for (var effects : List.of(merged, merged.reversed())) {
                var error = assertThrows(IllegalArgumentException.class,
                        () -> service.summarize(effects, p.transformation(), context, filter));
                assertEquals("CONFLICTING_PROVENANCE: duplicate summary observations", error.getMessage());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> service.summarize(
                List.of(merged.getFirst(), merged.getFirst(), merged.getLast()), p.transformation(), context, Optional.empty()));
        var outsideSelection = service.summarize(merged, p.transformation(), context, Optional.of("unseen-context"));
        assertEquals(0, outsideSelection.pairCount());
        assertTrue(outsideSelection.mean().isEmpty());
    }

    @Test void directSummaryOfIdenticalBatchesRetainsOneEmpiricalComparison() throws Exception {
        var p = pair();
        var effect = service.analyze(List.of(p), measurements(p), 0.3).effects().getFirst();
        var summary = service.summarize(List.of(effect, effect), p.transformation(), context, Optional.empty());
        assertEquals(1, summary.pairCount());
        assertEquals(1, summary.studyCount());
        assertEquals(1, summary.exactStudyCount());
        assertEquals(1, summary.mean().orElseThrow());
        assertTrue(summary.studyStandardDeviation().isEmpty());
    }

    @Test void identicalProvenanceStillDeduplicatesWithoutAbstaining() throws Exception {
        var p = pair();
        var single = service.analyze(List.of(p), measurements(p), 0.3);
        assertEquals(single, service.analyze(List.of(p, p, p), measurements(p).reversed(), 0.3));
        assertEquals(1, single.effects().size());
        assertTrue(single.rejections().isEmpty());
    }

    @Test void conflictDoesNotSuppressIndependentEvidence() throws Exception {
        var p = pair();
        var conflict = new Pair(p.left(), p.right(), p.leftIdentity(), p.rightIdentity(),
                p.leftFragment(), p.rightFragment(), p.coreCorrespondence(), "synthetic-conflicting-version");
        var backend = new OclMolecularBackend();
        var independent = new OclMatchedPairExtractor().extract(List.of(
                new Source("c", "synthetic-dataset/v1", backend.decodeStructure("SMILES", "OC1CCCCC1"), List.of("oc")),
                new Source("d", "synthetic-dataset/v1", backend.decodeStructure("SMILES", "NC1CCCCC1"), List.of("od"))), 1)
                .pairs().getFirst();
        var observations = new java.util.ArrayList<>(measurements(p));
        observations.addAll(measurements(independent));
        var result = service.analyze(List.of(p, independent, conflict), observations, 0.3);
        assertEquals(service.analyze(List.of(independent), measurements(independent), 0.3).effects(), result.effects());
        assertEquals(1, result.rejections().size());
        assertEquals("CONFLICTING_PROVENANCE", result.rejections().getFirst().reason());
        assertEquals(result, service.analyze(List.of(conflict, independent, p), observations.reversed(), 0.3));
    }

    @Test void qualificationMismatchReceiptSurvivesReplayWithoutAnEstimate() throws Exception {
        var p = pair();
        var observations = measurements(p);
        var original = observations.getLast();
        var revisedContext = new Context(context.target(), context.endpoint(), context.method(), context.assay(),
                context.conditions(), context.units(), context.scale(), "qualification/v2");
        var revised = new Measurement(original.reference(), original.moleculeIdentity(), revisedContext,
                original.study(), original.replicate(), original.lower(), original.upper(), original.relation());
        var result = service.analyze(List.of(p), List.of(observations.getFirst(), revised), 0.3);
        assertTrue(result.effects().isEmpty());
        assertEquals(List.of(new Rejection(observations.getFirst().reference(), revised.reference(),
                "INCOMPARABLE_CONTEXT_OR_STUDY")), result.rejections());
        assertEquals(result, service.analyze(List.of(p), List.of(revised, observations.getFirst()), 0.3));
        var summary = service.summarize(result.effects(), p.transformation(), context, Optional.empty());
        assertTrue(summary.mean().isEmpty());
        assertEquals(0, summary.pairCount());
    }
}
