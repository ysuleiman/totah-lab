package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.ConformerGenerator3d;
import totah.lab.athena.design.backend.ConformerMinimizer;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.MolecularSanitizer;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OclMolecularBackendAcceptanceTest {
    private final OclMolecularBackend backend = new OclMolecularBackend();

    @Test
    void canonicalIdentityAndSanitizationAreStableForAromaticAndChargedGraphs() throws Exception {
        var benzene = benzene();
        String first = backend.identify(benzene).canonicalKey();
        String second = backend.identify(benzene).canonicalKey();
        assertThat(first).isEqualTo(second).startsWith("OCL_IDCODE:");
        var sanitized = backend.sanitize(benzene,
                new MolecularSanitizer.SanitizationPolicy(Set.of(), true));
        assertThat(sanitized.valid()).isTrue();
        assertThat(sanitized.evidence().graphChanges()).isEmpty();

        var ammonium = new MolecularGraph(List.of(atom("n", "N", 1)), List.of(), Map.of());
        assertThat(backend.identify(ammonium).canonicalKey()).isNotBlank();
    }

    @Test
    void smartsReturnsStableProjectAtomIds() throws Exception {
        var result = backend.match("[O]", ethanol());
        assertThat(result.queryToTargetAtomIds()).containsExactly(Map.of("query:0", "o1"));
    }

    @Test
    void fixedSeedConformersAreReproducible() throws Exception {
        var configuration = new ConformerGenerator3d.Configuration(246813579L, 2, 5_000L, true);
        var first = backend.generate(ethanol(), configuration);
        var second = backend.generate(ethanol(), configuration);
        assertThat(first.conformers()).isEqualTo(second.conformers());
        assertThat(first.conformers()).isNotEmpty();
        assertThat(first.evidence().messages()).contains("seed=246813579");
    }

    @Test
    void unsupportedStereoDescriptorFailsExplicitly() {
        var atom = new MolecularGraph.Atom("c", "C", null, 0, 0, false, "R", null, Map.of());
        assertThatThrownBy(() -> backend.identify(new MolecularGraph(List.of(atom), List.of(), Map.of())))
                .hasMessageContaining("unsupported project-neutral stereo descriptor");
    }

    @Test
    void explicitParitySurvivesSanitizationRoundTrip() throws Exception {
        var atoms = List.of(
                new MolecularGraph.Atom("center", "C", null, 0, 0, false, "PARITY_1", null, Map.of()),
                atom("f", "F", 0), atom("cl", "Cl", 0), atom("br", "Br", 0), atom("i", "I", 0));
        var graph = new MolecularGraph(atoms, List.of(
                bond("bf", "center", "f", MolecularGraph.BondOrder.SINGLE, false),
                bond("bcl", "center", "cl", MolecularGraph.BondOrder.SINGLE, false),
                bond("bbr", "center", "br", MolecularGraph.BondOrder.SINGLE, false),
                bond("bi", "center", "i", MolecularGraph.BondOrder.SINGLE, false)), Map.of());
        var result = backend.sanitize(graph, new MolecularSanitizer.SanitizationPolicy(Set.of(), true));
        assertThat(result.graph().atom("center").orElseThrow().stereochemistry()).isEqualTo("PARITY_1");
        assertThat(backend.validate(result.graph()).definedStereoElements()).isEqualTo(1);
    }

    @Test
    void mmffMinimizationIsBoundedAndReportsBackend() throws Exception {
        var generated = backend.generate(ethanol(),
                new ConformerGenerator3d.Configuration(1234L, 1, 5_000L, true)).conformers().getFirst().graph();
        var result = backend.minimize(generated,
                new ConformerMinimizer.Configuration("MMFF94SPLUS", 200, 1.0e-4, 1.0e-6));
        assertThat(result.energy()).isFinite();
        assertThat(result.evidence().backend()).isEqualTo("OPEN_CHEM_LIB");
    }

    @Test
    void correspondenceRepairsStableIdsAcrossDifferentAtomOrders() throws Exception {
        var original = ethanol();
        var reordered = new MolecularGraph(List.of(atom("oxygen", "O", 0), atom("middle", "C", 0), atom("end", "C", 0)),
                List.of(bond("new2", "middle", "oxygen", MolecularGraph.BondOrder.SINGLE, false),
                        bond("new1", "end", "middle", MolecularGraph.BondOrder.SINGLE, false)), Map.of());
        assertThat(backend.identify(original).canonicalKey()).isEqualTo(backend.identify(reordered).canonicalKey());
        var mapping = backend.correspondence(reordered, original);
        assertThat(mapping.exhaustive()).isTrue(); assertThat(mapping.ambiguous()).isFalse();
        assertThat(mapping.selected().atoms()).isEqualTo(Map.of("oxygen", "o1", "middle", "c2", "end", "c1"));
        assertThat(mapping.selected().bonds()).isEqualTo(Map.of("new2", "b2", "new1", "b1"));
    }

    @Test
    void symmetryAndTetrahedralParityAreProvedByTheBackend() throws Exception {
        assertThat(backend.correspondence(benzene(), benzene()).alternatives()).hasSize(12);
        var center = new MolecularGraph.Atom("center", "C", null, 0, 0, false, "PARITY_1", null, Map.of());
        var original = new MolecularGraph(List.of(center, atom("f", "F", 0), atom("cl", "Cl", 0), atom("br", "Br", 0), atom("i", "I", 0)),
                List.of(bond("bf", "center", "f", MolecularGraph.BondOrder.SINGLE, false), bond("bcl", "center", "cl", MolecularGraph.BondOrder.SINGLE, false),
                        bond("bbr", "center", "br", MolecularGraph.BondOrder.SINGLE, false), bond("bi", "center", "i", MolecularGraph.BondOrder.SINGLE, false)), Map.of());
        var reversedParity = new MolecularGraph.Atom("center", "C", null, 0, 0, false, "PARITY_2", null, Map.of());
        var reordered = new MolecularGraph(List.of(reversedParity, original.atoms().get(2), original.atoms().get(1), original.atoms().get(3), original.atoms().get(4)),original.bonds(),Map.of());
        assertThat(backend.identify(original).canonicalKey()).isEqualTo(backend.identify(reordered).canonicalKey());
        assertThat(backend.correspondence(reordered, original).selected().atoms().get("center")).isEqualTo("center");
        var opposite = new MolecularGraph(List.of(reversedParity, original.atoms().get(1), original.atoms().get(2), original.atoms().get(3), original.atoms().get(4)),original.bonds(),Map.of());
        assertThat(backend.identify(original).canonicalKey()).isNotEqualTo(backend.identify(opposite).canonicalKey());
        assertThat(backend.correspondence(opposite,original).alternatives()).isEmpty();
    }

    @Test
    void unsupportedHydrogenCountsAndBondStereoFailClosed() {
        var hydrogenCount = new MolecularGraph(List.of(new MolecularGraph.Atom("n", "N", null, 1, 5, false, "UNSPECIFIED", null, Map.of())),List.of(),Map.of());
        assertThatThrownBy(() -> backend.identify(hydrogenCount)).hasMessageContaining("hydrogen-count annotation");
        var stereo = new MolecularGraph(ethanol().atoms(),List.of(new MolecularGraph.Bond("b1","c1","c2",MolecularGraph.BondOrder.SINGLE,false,"E",Map.of()),ethanol().bonds().get(1)),Map.of());
        assertThatThrownBy(() -> backend.sanitize(stereo,new MolecularSanitizer.SanitizationPolicy(Set.of(),true))).hasMessageContaining("unsupported bond stereo");
    }

    @Test
    void chargedProductsRetainMultipleReceiptedDerivationsThroughOcl() throws Exception {
        var root = new MolecularGraph(List.of(atom("n", "N", 1), atom("c", "C", 0), atom("negative", "O", -1)), List.of(bond("nc", "n", "c", MolecularGraph.BondOrder.SINGLE, false), bond("co", "c", "negative", MolecularGraph.BondOrder.SINGLE, false)), Map.of());
        var provenance = new totah.lab.athena.design.generation.MolecularDesignTree.Provenance(
                new totah.lab.athena.design.generation.MolecularDesignTree.Reference("hypothesis", "1"),List.of(),List.of(),
                new totah.lab.athena.design.generation.MolecularDesignTree.Reference("rule", "1"),List.of(),"retain charged anchor",List.of(),false);
        var edits = java.util.stream.Stream.of("first","second").map(id -> {
            var edit = new totah.lab.athena.design.backend.GraphEdit(id,"v",totah.lab.athena.design.backend.GraphEdit.Type.ATOM_SUBSTITUTION,Set.of("c"),Set.of(),null,null,"N",null,Map.of());
            return new totah.lab.athena.design.generation.MolecularDesignGraphGenerator.AuthorizedEdit(edit,
                    new totah.lab.athena.design.backend.GraphEditTransactionEngine.Authorization("v",Set.of(edit.type()),Set.of("c"),Set.of("n"),Set.of()),0,provenance);
        }).toList();
        var generator = new totah.lab.athena.design.generation.MolecularDesignGraphGenerator(new totah.lab.athena.design.backend.GraphEditTransactionEngine(),backend,backend);
        var tree = generator.generateTraced(root,provenance,new totah.lab.athena.design.generation.MolecularDesignGraphGenerator.Configuration(
                totah.lab.athena.design.generation.GenerationStrategy.ENUMERATIVE,8,1,8,false,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)),
                state -> state.depth()==0 ? edits : List.of(), a -> {}, (p,e,g) -> new totah.lab.athena.design.generation.MolecularDesignGraphGenerator.GeometryResult(true,List.of(),Set.of()));
        assertThat(tree.attempts()).hasSize(2);
        assertThat(tree.attempts().get(1).outcome()).isEqualTo(totah.lab.athena.design.generation.MolecularDesignTree.Outcome.DEDUPLICATED);
        assertThat(tree.states()).hasSize(3);
        assertThat(tree.states().get(2).graph().atom("n").orElseThrow().formalCharge()).isEqualTo(1);
    }

    @Test
    void positiveHydrogenCountsMustMatchInferredHydrogensAndArePreserved() throws Exception {
        var graph = new MolecularGraph(List.of(new MolecularGraph.Atom("c","C",null,0,4,false,"UNSPECIFIED",null,Map.of())),List.of(),Map.of());
        assertThat(backend.sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)).graph()).isEqualTo(graph);
        assertThat(backend.identify(graph).canonicalKey()).isNotBlank();
    }

    @Test
    void existingNetChargeValidationLimitationIsExplicit() {
        var graph = new MolecularGraph(List.of(atom("n","N",1)),List.of(),Map.of());
        assertThatThrownBy(() -> backend.sanitize(graph,new MolecularSanitizer.SanitizationPolicy(Set.of(),true)))
                .isInstanceOf(totah.lab.athena.design.backend.MolecularBackendException.class)
                .hasMessageContaining("OCL sanitization failed");
    }

    private static MolecularGraph ethanol() {
        return new MolecularGraph(List.of(atom("c1", "C", 0), atom("c2", "C", 0), atom("o1", "O", 0)),
                List.of(bond("b1", "c1", "c2", MolecularGraph.BondOrder.SINGLE, false),
                        bond("b2", "c2", "o1", MolecularGraph.BondOrder.SINGLE, false)), Map.of());
    }

    private static MolecularGraph benzene() {
        var atoms = java.util.stream.IntStream.range(0, 6).mapToObj(i -> atom("c" + i, "C", 0)).toList();
        var bonds = java.util.stream.IntStream.range(0, 6).mapToObj(i ->
                bond("b" + i, "c" + i, "c" + ((i + 1) % 6), MolecularGraph.BondOrder.AROMATIC, true)).toList();
        return new MolecularGraph(atoms, bonds, Map.of());
    }

    private static MolecularGraph.Atom atom(String id, String element, int charge) {
        return new MolecularGraph.Atom(id, element, null, charge, 0, false,
                "UNSPECIFIED", null, Map.of());
    }
    private static MolecularGraph.Bond bond(String id, String a, String b, MolecularGraph.BondOrder order,
                                             boolean aromatic) {
        return new MolecularGraph.Bond(id, a, b, order, aromatic, "UNSPECIFIED", Map.of());
    }
}
