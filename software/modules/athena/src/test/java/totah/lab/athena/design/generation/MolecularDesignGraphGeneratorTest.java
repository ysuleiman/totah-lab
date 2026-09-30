package totah.lab.athena.design.generation;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.BackendEvidence;
import totah.lab.athena.design.backend.CanonicalIdentityService;
import totah.lab.athena.design.backend.GraphEdit;
import totah.lab.athena.design.backend.GraphEditTransactionEngine;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.MolecularSanitizer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MolecularDesignGraphGeneratorTest {
    @Test void heteroatomSubstitutionCanRemoveAnExplicitAuthorizedHydrogen() {
        var carbon = new MolecularGraph.Atom("C1", "C", null, 0, 0, true, "UNSPECIFIED", null, Map.of());
        var hydrogen = new MolecularGraph.Atom("H1", "H", null, 0, 0, false, "UNSPECIFIED", null, Map.of());
        var graph = new MolecularGraph(List.of(carbon, hydrogen), List.of(new MolecularGraph.Bond(
                "b1", "C1", "H1", MolecularGraph.BondOrder.SINGLE, false, "UNSPECIFIED", Map.of())), Map.of());
        var edit = new GraphEdit("c-to-n", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("C1", "H1"), Set.of(), null, null, "N", null, Map.of("substitutionAtomId", "C1"));
        var authorization = new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.ATOM_SUBSTITUTION),
                Set.of("C1", "H1"), Set.of(), Set.of());
        var result = new GraphEditTransactionEngine().apply(graph, edit, authorization);
        assertThat(result.product().atom("C1").orElseThrow().element()).isEqualTo("N");
        assertThat(result.product().atom("H1")).isEmpty();
        assertThat(result.receipt().deletedAtomIds()).containsExactly("H1");
    }
    @Test
    void enumerativeAndPrioritizedStrategiesAreExplicitAndDeduplicateBeforeExpansion() throws Exception {
        var sanitizer = (MolecularSanitizer) (graph, policy) ->
                new MolecularSanitizer.Result(graph, true, evidence("sanitize"));
        var identity = (CanonicalIdentityService) graph ->
                new CanonicalIdentityService.Result(graph.atoms().stream().map(MolecularGraph.Atom::element)
                        .sorted().collect(Collectors.joining("-")), evidence("identity"));
        var generator = new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(), sanitizer, identity);
        var config = new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.ENUMERATIVE,
                8, 1, 8, false, new MolecularSanitizer.SanitizationPolicy(Set.of(), true));
        var tree = generator.generate(parent(), config, (graph, depth) -> List.of(
                substitute("nitrogen-first", "N", 0), substitute("nitrogen-duplicate", "N", 1),
                substitute("oxygen", "O", 2)));
        assertThat(tree.nodes()).hasSize(3); // root, N, O; duplicate N is never expanded
        assertThat(tree.edges()).hasSize(3); // both edit paths remain visible as provenance edges
        assertThat(tree.edges().stream().filter(edge -> edge.childNodeId().equals("node-0001"))).hasSize(2);
    }

    @Test
    void disconnectedProductsFailUnlessExplicitlyAllowed() throws Exception {
        var sanitizer = (MolecularSanitizer) (graph, policy) -> new MolecularSanitizer.Result(graph, true, evidence("s"));
        var identity = (CanonicalIdentityService) graph -> new CanonicalIdentityService.Result(
                Integer.toString(graph.atoms().size()), evidence("i"));
        var generator = new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(), sanitizer, identity);
        var config = new MolecularDesignGraphGenerator.Configuration(GenerationStrategy.PRIORITIZED,
                4, 1, 4, false, new MolecularSanitizer.SanitizationPolicy(Set.of(), true));
        var tree = generator.generate(chain(), config, (graph, depth) -> List.of(prune()));
        assertThat(tree.nodes()).hasSize(1);
    }

    private static MolecularDesignGraphGenerator.AuthorizedEdit substitute(String id, String element, int priority) {
        var edit = new GraphEdit(id, "toy-vector", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("a2"), Set.of(), null, null, element, null, Map.of());
        return new MolecularDesignGraphGenerator.AuthorizedEdit(edit,
                new GraphEditTransactionEngine.Authorization("toy-vector", Set.of(GraphEdit.Type.ATOM_SUBSTITUTION),
                        Set.of("a2"), Set.of("a1"), Set.of()), priority);
    }
    private static MolecularDesignGraphGenerator.AuthorizedEdit prune() {
        var edit = new GraphEdit("prune", "toy-vector", GraphEdit.Type.SUBSTITUENT_PRUNING,
                Set.of("a2"), Set.of(), null, null, null, null, Map.of());
        return new MolecularDesignGraphGenerator.AuthorizedEdit(edit,
                new GraphEditTransactionEngine.Authorization("toy-vector", Set.of(GraphEdit.Type.SUBSTITUENT_PRUNING),
                        Set.of("a2"), Set.of("a1"), Set.of()), 0);
    }
    private static MolecularGraph parent() {
        return new MolecularGraph(List.of(atom("a1", "C"), atom("a2", "C")),
                List.of(new MolecularGraph.Bond("b1", "a1", "a2", MolecularGraph.BondOrder.SINGLE,
                        false, "UNSPECIFIED", Map.of())), Map.of());
    }
    private static MolecularGraph chain() {
        return new MolecularGraph(List.of(atom("a1", "C"), atom("a2", "C"), atom("a3", "C")),
                List.of(new MolecularGraph.Bond("b1", "a1", "a2", MolecularGraph.BondOrder.SINGLE,
                                false, "UNSPECIFIED", Map.of()),
                        new MolecularGraph.Bond("b2", "a2", "a3", MolecularGraph.BondOrder.SINGLE,
                                false, "UNSPECIFIED", Map.of())), Map.of());
    }
    private static MolecularGraph.Atom atom(String id, String element) {
        return new MolecularGraph.Atom(id, element, null, 0, 0, false, "UNSPECIFIED", null, Map.of());
    }
    private static BackendEvidence evidence(String operation) {
        return new BackendEvidence("toy", "1", operation, Map.of(), List.of(), List.of());
    }
}
