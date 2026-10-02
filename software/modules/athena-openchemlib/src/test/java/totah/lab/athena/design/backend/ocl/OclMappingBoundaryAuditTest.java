package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.knowledge.MatchedPairExtractor.Source;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic structural fixtures only: no activity claims, proposed ligands, or design authorization. */
class OclMappingBoundaryAuditTest {
    private final OclMolecularBackend backend = new OclMolecularBackend();
    private final OclMatchedPairExtractor extractor = new OclMatchedPairExtractor();

    private Source source(String id, String smiles) throws Exception {
        return new Source(id, "synthetic-boundary-audit/v1", backend.decodeStructure("SMILES", smiles),
                List.of("observation:" + id, "provenance:" + id));
    }

    private MolecularGraph permute(MolecularGraph graph, int first, int second) throws Exception {
        var mapper = new OclGraphMapper();
        var mapping = mapper.toOcl(graph);
        var molecule = mapping.molecule();
        molecule.ensureHelperArrays(Molecule.cHelperCIP);
        molecule.swapAtoms(first, second);
        molecule.ensureHelperArrays(Molecule.cHelperCIP);
        var result = mapper.fromOcl(mapping, molecule);
        return new MolecularGraph(result.atoms(), result.bonds().reversed(), result.properties());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CC[C@H](F)CO", "[13CH3]c1ccccc1", "[NH3+]CCc1ccccc1"})
    void identityAndStableAtomCorrespondenceSurviveToolkitPermutations(String smiles) throws Exception {
        var graph = source("source", smiles).graph();
        var identity = backend.identify(graph);
        var ids = graph.atoms().stream().map(MolecularGraph.Atom::id).collect(Collectors.toSet());
        for (int i = 1; i < graph.atoms().size(); i++) {
            var reordered = permute(graph, 0, i);
            assertEquals(identity, backend.identify(reordered));
            assertEquals(ids, reordered.atoms().stream().map(MolecularGraph.Atom::id).collect(Collectors.toSet()));
            for (var atom : graph.atoms()) {
                var actual = reordered.atom(atom.id()).orElseThrow();
                assertEquals(atom.element(), actual.element());
                assertEquals(atom.isotope(), actual.isotope());
                assertEquals(atom.formalCharge(), actual.formalCharge());
            }
            var proof = backend.correspondence(graph, reordered);
            assertTrue(proof.exhaustive());
            assertTrue(proof.alternatives().stream().anyMatch(mapping ->
                    mapping.atoms().keySet().equals(ids)
                            && mapping.atoms().entrySet().stream().allMatch(e -> e.getKey().equals(e.getValue()))));
        }
    }

    @Test
    void retainedStereoContextIsIndependentOfAtomOrdering() throws Exception {
        var a = source("a", "CC[C@H](F)CO");
        var b = source("b", "CC[C@H](F)CN");
        var baseline = extractor.extract(List.of(a, b), 1);
        assertEquals(1, baseline.pairs().size(), baseline.issues().toString());
        var graph = permute(a.graph(), 0, 3);
        assertEquals(backend.identify(a.graph()), backend.identify(graph), "same molecular identity is a prerequisite");
        var reordered = new Source(a.id(), a.dataset(), graph, a.observationReferences());
        var actual = extractor.extract(List.of(b, reordered), 1);
        assertEquals(baseline.issues(), actual.issues());
        assertEquals(1, actual.pairs().size(), actual.issues().toString());
        var expectedPair = baseline.pairs().getFirst();
        var actualPair = actual.pairs().getFirst();
        assertEquals(expectedPair.transformation(), actualPair.transformation());
        assertEquals(expectedPair.coreCorrespondence().alternatives(), actualPair.coreCorrespondence().alternatives());
        assertAll(
                () -> assertEquals(expectedPair.leftFragment(), actualPair.leftFragment()),
                () -> assertEquals(expectedPair.rightFragment(), actualPair.rightFragment()));
    }

    @Test
    void attachmentDistinguishedStereoCoreRemainsProvable() throws Exception {
        var a = source("a", "C[C@H](F)CO");
        var b = source("b", "C[C@H](F)CN");
        // Only terminal O/N is variable. The capped retained core distinguishes CH2-cap
        // from methyl; deleting the cap makes those branches identical and loses stereo.
        for (var source : List.of(a, b)) {
            var single = extractor.extract(List.of(source), 1);
            assertTrue(single.issues().stream().noneMatch(i -> i.reason().startsWith("SOURCE_REJECTED:")
                    || i.reason().equals("NO_ELIGIBLE_SINGLE_CUT")), single.issues().toString());
        }
        var result = extractor.extract(List.of(a, b), 1);
        assertEquals(1, result.pairs().size(), result.issues().toString());
        var pair = result.pairs().getFirst();
        assertTrue(pair.coreCorrespondence().exhaustive());
        assertEquals(pair.leftFragment().constant(), pair.rightFragment().constant());
        for (var mapping : pair.coreCorrespondence().alternatives()) {
            assertEquals(pair.leftFragment().constantAtoms(), mapping.atoms().keySet());
            assertEquals(pair.rightFragment().constantAtoms(), Set.copyOf(mapping.atoms().values()));
            assertEquals(pair.rightFragment().constantAnchor(), mapping.atoms().get(pair.leftFragment().constantAnchor()));
        }
    }

    @Test
    void cappedCoreProofSurvivesEverySourcePermutationAndPreservesStereoDistinction() throws Exception {
        var a = source("a", "C[C@H](F)CO");
        var b = source("b", "C[C@H](F)CN");
        var baseline = extractor.extract(List.of(a, b), 1);
        assertEquals(1, baseline.pairs().size(), baseline.issues().toString());
        for (var original : List.of(a, b)) {
            for (int i = 1; i < original.graph().atoms().size(); i++) {
                var graph = permute(original.graph(), 0, i);
                assertEquals(backend.identify(original.graph()), backend.identify(graph));
                var reordered = new Source(original.id(), original.dataset(), graph, original.observationReferences());
                var result = extractor.extract(original == a ? List.of(b, reordered) : List.of(reordered, a), 1);
                assertEquals(baseline.issues(), result.issues());
                assertEquals(1, result.pairs().size(), result.issues().toString());
                var expected = baseline.pairs().getFirst();
                var actual = result.pairs().getFirst();
                assertEquals(expected.leftFragment(), actual.leftFragment());
                assertEquals(expected.rightFragment(), actual.rightFragment());
                assertEquals(expected.coreCorrespondence(), actual.coreCorrespondence());
                assertEquals(expected.left().observationReferences(), actual.left().observationReferences());
                assertEquals(expected.right().observationReferences(), actual.right().observationReferences());
            }
        }
        var opposite = source("opposite", "C[C@@H](F)CN");
        assertNotEquals(backend.identify(b.graph()), backend.identify(opposite.graph()));
        assertTrue(extractor.extract(List.of(a, opposite), 1).pairs().isEmpty(),
                "opposite retained-core stereochemistry must not be treated as a terminal-only change");
    }

    @Test
    void rejectedSourceDoesNotAcquirePairsOrLoseItsReceiptDuringReplay() throws Exception {
        var a = source("a", "Oc1ccccc1");
        var b = source("b", "Nc1ccccc1");
        var invalid = new Source("invalid", a.dataset(),
                new MolecularGraph(a.graph().atoms(), List.of(), Map.of()), List.of("observation:invalid"));
        var baseline = extractor.extract(List.of(a, b), 1);
        assertFalse(baseline.pairs().isEmpty());
        var actual = extractor.extract(List.of(a, invalid, b), 1);
        assertEquals(baseline.pairs(), actual.pairs());
        assertTrue(actual.issues().stream().anyMatch(i -> i.source().equals(invalid.id())
                && i.reason().startsWith("SOURCE_REJECTED:")));
        assertEquals(actual, extractor.extract(List.of(b, invalid, a), 1));
        for (var pair : actual.pairs()) {
            for (var retained : List.of(pair.left(), pair.right())) {
                var original = retained.id().equals(a.id()) ? a : b;
                assertEquals(original, retained, "source graph, dataset and observation/provenance references survive");
            }
            assertEquals(OclMatchedPairExtractor.ALGORITHM, pair.algorithm());
        }
    }
}
