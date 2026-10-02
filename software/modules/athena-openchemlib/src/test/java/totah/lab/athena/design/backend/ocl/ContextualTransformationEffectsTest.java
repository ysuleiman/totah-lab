package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.knowledge.*;
import totah.lab.athena.design.knowledge.ContextualTransformationEffects.*;
import totah.lab.athena.design.knowledge.MatchedPairExtractor.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContextualTransformationEffectsTest {
    private final Context context=new Context("target","IC50","binding","assay","protocol-v1","dimensionless","pIC50","review-1");
    private final ContextualTransformationEffects service=new ContextualTransformationEffects();
    private Pair pair() throws Exception {
        var backend=new OclMolecularBackend();
        var refs=List.of("a","b","a2","b2","a3","b3");
        return new OclMatchedPairExtractor().extract(List.of(
                new Source("left","test",backend.decodeStructure("SMILES","Oc1ccccc1"),refs),
                new Source("right","test",backend.decodeStructure("SMILES","Nc1ccccc1"),refs)),8).pairs().getFirst();
    }
    private Measurement m(String ref,String identity,Context c,String study,double lo,double hi,String relation) {
        return new Measurement(ref,identity,c,study,ref,lo,hi,relation);
    }
    @Test void genericKnowledgeBytecodeHasNoTargetToolkitOrWorkflowDependency() throws Exception {
        var classes=new ArrayDeque<Class<?>>();classes.add(ContextualTransformationEffects.class);classes.add(MatchedPairExtractor.class);
        while(!classes.isEmpty()) {
            var type=classes.removeFirst();classes.addAll(List.of(type.getDeclaredClasses()));
            try(var input=type.getResourceAsStream("/"+type.getName().replace('.','/')+".class")) {
                assertNotNull(input);String bytecode=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
                for(String forbidden:List.of("totah/lab/mettl7","totah/lab/daedalus","com/actelion","Mettl7","METTL7"))
                    assertFalse(bytecode.contains(forbidden),type.getName()+" depends on "+forbidden);
            }
        }
    }
    @Test void measuredDirectionIsNotBenefitAndSymmetryDoesNotInflateSupport() throws Exception {
        var p=pair(); var a=m("a",p.leftIdentity(),context,"s1",5,5,"=");var b=m("b",p.rightIdentity(),context,"s1",6,6,"=");
        var result=service.analyze(List.of(p,p),List.of(a,b),0.3);
        assertEquals(1,result.effects().size());assertEquals(Direction.INCREASE,result.effects().getFirst().direction());
        var summary=service.summarize(result.effects(),p.transformation(),context,Optional.empty());
        assertEquals(1,summary.mean().orElseThrow());assertEquals(1,summary.pairCount());assertEquals(1,summary.studyCount());
        assertTrue(summary.studyStandardDeviation().isEmpty());
    }
    @Test void censoredIntervalsAreRetainedButNotInsertedIntoPointMean() throws Exception {
        var p=pair();var a=m("a",p.leftIdentity(),context,"s1",5,5,"=");
        var b=m("b",p.rightIdentity(),context,"s1",4,Double.POSITIVE_INFINITY,">=");
        var result=service.analyze(List.of(p),List.of(a,b),0.3);var e=result.effects().getFirst();
        assertEquals(-1,e.lower());assertEquals(Double.POSITIVE_INFINITY,e.upper());assertEquals(Direction.UNRESOLVED,e.direction());
        assertTrue(service.summarize(result.effects(),p.transformation(),context,Optional.empty()).mean().isEmpty());
    }
    @Test void endpointAssayUnitsConditionsAndStudyCannotBePooled() throws Exception {
        var p=pair();var a=m("a",p.leftIdentity(),context,"s1",5,5,"=");
        for(var c:List.of(new Context("target","Ki","binding","assay","protocol-v1","dimensionless","pIC50","review-1"),
                new Context("target","IC50","cellular","assay","protocol-v1","dimensionless","pIC50","review-1"),
                new Context("other","IC50","binding","assay","protocol-v1","dimensionless","pIC50","review-1"),
                new Context("target","IC50","binding","other","protocol-v1","dimensionless","pIC50","review-1"),
                new Context("target","IC50","binding","assay","protocol-v2","dimensionless","pIC50","review-1"),
                new Context("target","IC50","binding","assay","protocol-v1","nM","linear","review-1"))) {
            var r=service.analyze(List.of(p),List.of(a,m("b",p.rightIdentity(),c,"s1",6,6,"=")),0.3);
            assertTrue(r.effects().isEmpty());assertEquals("INCOMPARABLE_CONTEXT_OR_STUDY",r.rejections().getFirst().reason());
        }
        assertTrue(service.analyze(List.of(p),List.of(a,m("b",p.rightIdentity(),context,"s2",6,6,"=")),0.3).effects().isEmpty());
    }
    @Test void unresolvedReplicatesAndUnboundObservationsAbstain() throws Exception {
        var p=pair();var a=m("a",p.leftIdentity(),context,"s1",5,5,"=");var b=m("b",p.rightIdentity(),context,"s1",6,6,"=");
        var r=service.analyze(List.of(p),List.of(a,b,m("a2",p.leftIdentity(),context,"s1",5.1,5.1,"=")),0.3);
        assertTrue(r.effects().isEmpty());assertTrue(r.rejections().stream().allMatch(x->x.reason().equals("UNRESOLVED_REPLICATES")));
        assertEquals("OBSERVATION_NOT_BOUND_TO_SOURCE",service.analyze(List.of(p),List.of(a,m("unbound",p.rightIdentity(),context,"s1",6,6,"=")),0.3).rejections().getFirst().reason());
        assertThrows(IllegalArgumentException.class,()->service.analyze(List.of(p),List.of(a,a),0.3));
        assertThrows(IllegalArgumentException.class,()->m("a",p.leftIdentity(),context,"s1",5,5,">"));
        assertThrows(IllegalArgumentException.class,()->m("a",p.leftIdentity(),context,"s1",5,6,"unknown"));
        assertEquals("DELTA_OVERFLOW",service.analyze(List.of(p),List.of(
                m("a",p.leftIdentity(),context,"s1",-Double.MAX_VALUE,-Double.MAX_VALUE,"="),
                m("b",p.rightIdentity(),context,"s1",Double.MAX_VALUE,Double.MAX_VALUE,"=")),0.3).rejections().getFirst().reason());
    }
    @Test void chemicalContextConditioningChangesEstimateWithoutPoolingScientificContext() throws Exception {
        var backend=new OclMolecularBackend();var sources=new ArrayList<Source>();
        String[] smiles={"Oc1ccccc1","Nc1ccccc1","OC1CCCCC1","NC1CCCCC1"};
        for(int i=0;i<smiles.length;i++)sources.add(new Source("m"+i,"synthetic-context-fixture",backend.decodeStructure("SMILES",smiles[i]),List.of("o"+i)));
        var pairs=new OclMatchedPairExtractor().extract(sources,8).pairs();assertEquals(2,pairs.size());
        var observations=new ArrayList<Measurement>();
        for(var p:pairs) {
            boolean aromatic=p.left().graph().atoms().stream().anyMatch(totah.lab.athena.design.backend.MolecularGraph.Atom::aromatic);
            observations.add(m(p.left().observationReferences().getFirst(),p.leftIdentity(),context,"s1",5,5,"="));
            observations.add(m(p.right().observationReferences().getFirst(),p.rightIdentity(),context,"s1",aromatic?6:4,aromatic?6:4,"="));
        }
        var effects=service.analyze(pairs,observations,0.3).effects();assertEquals(2,effects.size());
        for(var e:effects) {
            var unconditional=service.summarize(effects,e.pair().transformation(),context,Optional.empty());
            var conditional=service.summarize(effects,e.pair().transformation(),context,Optional.of(e.pair().leftFragment().chemicalContext()));
            assertEquals(0,unconditional.mean().orElseThrow(),1e-10);assertEquals(e.lower(),conditional.mean().orElseThrow(),1e-10);
            assertEquals(1,conditional.pairCount());assertEquals(2,unconditional.pairCount());
        }
    }
    @Test void contradictoryAndWithinToleranceResultsRemainVisible() throws Exception {
        var p=pair();var measurements=List.of(m("a",p.leftIdentity(),context,"s1",5,5,"="),m("b",p.rightIdentity(),context,"s1",6,6,"="),
                m("a2",p.leftIdentity(),context,"s2",5,5,"="),m("b2",p.rightIdentity(),context,"s2",4,4,"="),
                m("a3",p.leftIdentity(),context,"s3",5,5,"="),m("b3",p.rightIdentity(),context,"s3",5.1,5.1,"="));
        var r=service.analyze(List.of(p),measurements,0.3);
        assertEquals(Set.of(Direction.INCREASE,Direction.DECREASE,Direction.WITHIN_TOLERANCE),new HashSet<>(r.effects().stream().map(Effect::direction).toList()));
        var s=service.summarize(r.effects(),p.transformation(),context,Optional.empty());
        assertEquals(3,s.studyCount());assertEquals(0.1/3,s.mean().orElseThrow(),1e-10);assertTrue(s.studyStandardDeviation().isPresent());
        assertTrue(service.summarize(r.effects(),p.transformation(),context,Optional.of("unseen context")).mean().isEmpty());
    }
    @Test void retainedStereoExtractionPreservesEvidenceEligibilityUnderPermutation() throws Exception {
        var backend = new OclMolecularBackend();
        var extractor = new OclMatchedPairExtractor();
        // Synthetic numbers test plumbing only; no assay or target interpretation.
        for (String prefix : List.of("CC[C@H](F)C", "C[C@H](F)C")) {
            var a = new Source("a", "synthetic-integration/v1", backend.decodeStructure("SMILES", prefix + "O"), List.of("oa"));
            var b = new Source("b", "synthetic-integration/v1", backend.decodeStructure("SMILES", prefix + "N"), List.of("ob"));
            var baseline = extractor.extract(List.of(a, b), 1);
            assertEquals(1, baseline.pairs().size(), baseline.issues().toString());
            var pair = baseline.pairs().getFirst();
            var observations = List.of(
                    m(pair.left().observationReferences().getFirst(), pair.leftIdentity(), context, "synthetic-study", 5, 5, "="),
                    m(pair.right().observationReferences().getFirst(), pair.rightIdentity(), context, "synthetic-study", 6, 6, "="));
            var expected = service.analyze(baseline.pairs(), observations, 0.3);
            assertEquals(1, expected.effects().size());
            assertTrue(expected.rejections().isEmpty());
            for (var original : List.of(a, b)) for (int i = 1; i < original.graph().atoms().size(); i++) {
                var mapper = new OclGraphMapper();
                var mapping = mapper.toOcl(original.graph());
                mapping.molecule().ensureHelperArrays(com.actelion.research.chem.Molecule.cHelperCIP);
                mapping.molecule().swapAtoms(0, i);
                mapping.molecule().ensureHelperArrays(com.actelion.research.chem.Molecule.cHelperCIP);
                var graph = mapper.fromOcl(mapping, mapping.molecule());
                assertEquals(backend.identify(original.graph()), backend.identify(graph));
                var reordered = new Source(original.id(), original.dataset(), graph, original.observationReferences());
                var extraction = extractor.extract(original == a ? List.of(b, reordered) : List.of(reordered, a), 1);
                assertEquals(baseline.issues(), extraction.issues());
                assertEquals(1, extraction.pairs().size());
                var actualPair = extraction.pairs().getFirst();
                assertEquals(pair.algorithm(), actualPair.algorithm());
                assertEquals(pair.coreCorrespondence(), actualPair.coreCorrespondence());
                var actual = service.analyze(List.of(actualPair, actualPair), observations.reversed(), 0.3);
                assertEquals(expected.rejections(), actual.rejections());
                assertEquals(1, actual.effects().size(), "duplicate extraction paths must not inflate support");
                var effect = actual.effects().getFirst();
                assertEquals(expected.effects().getFirst().left(), effect.left());
                assertEquals(expected.effects().getFirst().right(), effect.right());
                assertEquals(Direction.INCREASE, effect.direction());
                assertEquals(1, effect.lower());
                assertEquals(1, effect.upper());
                var summary = service.summarize(actual.effects(), pair.transformation(), context,
                        Optional.of(pair.leftFragment().chemicalContext()));
                assertEquals(1, summary.pairCount());
                assertEquals(1, summary.studyCount());
                assertEquals(1, summary.mean().orElseThrow());
                assertTrue(summary.studyStandardDeviation().isEmpty());
                assertEquals(actual, service.analyze(List.of(actualPair, actualPair), observations, 0.3));
            }
        }
    }

    @Test void extractedStereoPairsAbstainForMissingUnboundAndIncomparableEvidence() throws Exception {
        var backend = new OclMolecularBackend();
        var extraction = new OclMatchedPairExtractor().extract(List.of(
                new Source("a", "synthetic-integration/v1", backend.decodeStructure("SMILES", "C[C@H](F)CO"), List.of("oa")),
                new Source("b", "synthetic-integration/v1", backend.decodeStructure("SMILES", "C[C@H](F)CN"), List.of("ob"))), 1);
        assertEquals(1, extraction.pairs().size(), extraction.issues().toString());
        var pair = extraction.pairs().getFirst();
        var left = m(pair.left().observationReferences().getFirst(), pair.leftIdentity(), context, "synthetic-study", 5, 5, "=");
        var otherContext = new Context("other-synthetic-target", "IC50", "binding", "assay", "protocol-v1", "dimensionless", "pIC50", "review-1");
        var scenarios = List.of(List.of(left),
                List.of(left, m("unbound", pair.rightIdentity(), context, "synthetic-study", 6, 6, "=")),
                List.of(left, m(pair.right().observationReferences().getFirst(), pair.rightIdentity(), otherContext, "synthetic-study", 6, 6, "=")));
        var reasons = List.of("MISSING_MEASUREMENT", "OBSERVATION_NOT_BOUND_TO_SOURCE", "INCOMPARABLE_CONTEXT_OR_STUDY");
        for (int i = 0; i < scenarios.size(); i++) {
            var result = service.analyze(extraction.pairs(), scenarios.get(i), 0.3);
            assertTrue(result.effects().isEmpty());
            assertEquals(List.of(reasons.get(i)), result.rejections().stream().map(Rejection::reason).toList());
            assertEquals(result, service.analyze(extraction.pairs(), scenarios.get(i).reversed(), 0.3));
            var summary = service.summarize(result.effects(), pair.transformation(), context,
                    Optional.of(pair.leftFragment().chemicalContext()));
            assertEquals(0, summary.pairCount());
            assertEquals(0, summary.studyCount());
            assertTrue(summary.mean().isEmpty());
            assertTrue(summary.studyStandardDeviation().isEmpty());
        }
    }

}
