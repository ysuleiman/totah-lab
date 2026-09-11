package totah.lab.athena.design.backend;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphEditTransactionEngineTest {
    private final GraphEditTransactionEngine engine = new GraphEditTransactionEngine();

    @Test
    void atomSubstitutionIsDeterministicAndKeepsLineage() {
        var edit = new GraphEdit("e1", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("a2"), Set.of(), null, null, "N", null, Map.of());
        var authorization = authorization(Set.of(GraphEdit.Type.ATOM_SUBSTITUTION), Set.of("a2"));
        var first = engine.apply(parent(), edit, authorization);
        var second = engine.apply(parent(), edit, authorization);
        assertThat(first).isEqualTo(second);
        assertThat(first.product().atom("a2").orElseThrow().element()).isEqualTo("N");
        assertThat(first.receipt().parentToProductAtomIds()).containsEntry("a1", "a1").containsEntry("a2", "a2");
    }

    @Test
    void protectedAtomsFailClosed() {
        var edit = new GraphEdit("e2", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("a1"), Set.of(), null, null, "N", null, Map.of());
        assertThatThrownBy(() -> engine.apply(parent(), edit,
                authorization(Set.of(GraphEdit.Type.ATOM_SUBSTITUTION), Set.of("a1"))))
                .hasMessageContaining("protected atom");
    }

    @Test
    void attachmentAndPruningHaveExactLineage() {
        var fragment = new MolecularGraph(List.of(atom("new1", "F")), List.of(), Map.of());
        var attach = new GraphEdit("grow", "v1", GraphEdit.Type.SUBSTITUENT_GROWTH,
                Set.of(), Set.of(), "a2", fragment, null, MolecularGraph.BondOrder.SINGLE,
                Map.of("fragmentAnchorAtomId", "new1", "attachmentBondId", "newBond"));
        var grown = engine.apply(parent(), attach,
                authorization(Set.of(GraphEdit.Type.SUBSTITUENT_GROWTH), Set.of("a2")));
        assertThat(grown.receipt().addedAtomIds()).containsExactly("new1");
        assertThat(grown.receipt().addedBondIds()).containsExactly("newBond");
        assertThat(grown.receipt().validations())
                .contains("ATTACHMENT_ANCHOR_AUTHORIZATION_PASS:a2");

        var prune = new GraphEdit("prune", "v1", GraphEdit.Type.SUBSTITUENT_PRUNING,
                Set.of("a2"), Set.of(), null, null, null, null, Map.of());
        var pruned = engine.apply(parent(), prune,
                authorization(Set.of(GraphEdit.Type.SUBSTITUENT_PRUNING), Set.of("a2")));
        assertThat(pruned.receipt().deletedAtomIds()).containsExactly("a2");
        assertThat(pruned.receipt().deletedBondIds()).containsExactly("b1");
    }

    @Test
    void authorizedBondModificationAndSubstituentReplacementAreBounded() {
        var bondEdit = new GraphEdit("bond", "v1", GraphEdit.Type.AUTHORIZED_BOND_MODIFICATION,
                Set.of(), Set.of("b1"), null, null, null, MolecularGraph.BondOrder.DOUBLE, Map.of());
        var bondResult = engine.apply(parent(), bondEdit,
                new GraphEditTransactionEngine.Authorization("v1",
                        Set.of(GraphEdit.Type.AUTHORIZED_BOND_MODIFICATION), Set.of(), Set.of("a1"), Set.of()));
        assertThat(bondResult.product().bond("b1").orElseThrow().order()).isEqualTo(MolecularGraph.BondOrder.DOUBLE);

        var fragment = new MolecularGraph(List.of(atom("replacement", "N")), List.of(), Map.of());
        var replace = new GraphEdit("replace", "v1", GraphEdit.Type.SUBSTITUENT_REPLACEMENT,
                Set.of("a2"), Set.of(), "a1", fragment, null, MolecularGraph.BondOrder.SINGLE,
                Map.of("fragmentAnchorAtomId", "replacement", "attachmentBondId", "replacementBond"));
        var replacement = engine.apply(parent(), replace,
                new GraphEditTransactionEngine.Authorization("v1",
                        Set.of(GraphEdit.Type.SUBSTITUENT_REPLACEMENT), Set.of("a1", "a2"), Set.of(), Set.of()));
        assertThat(replacement.product().atom("a2")).isEmpty();
        assertThat(replacement.product().atom("replacement")).isPresent();
        assertThat(replacement.receipt().deletedAtomIds()).containsExactly("a2");
        assertThat(replacement.receipt().addedAtomIds()).containsExactly("replacement");
    }

    @Test
    void attachmentAnchorMustExistBePermittedAndNotProtected() {
        var valid = attachment("a2", GraphEdit.Type.FRAGMENT_ATTACHMENT, Set.of());
        assertThat(engine.apply(parent(), valid,
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.FRAGMENT_ATTACHMENT),
                        Set.of("a2"), Set.of("a1"), Set.of())).receipt().validations())
                .contains("ATTACHMENT_ANCHOR_AUTHORIZATION_PASS:a2");

        assertThatThrownBy(() -> engine.apply(parent(), valid,
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.FRAGMENT_ATTACHMENT),
                        Set.of(), Set.of("a1"), Set.of())))
                .hasMessageContaining("outside permitted region");
        assertThatThrownBy(() -> engine.apply(parent(), attachment("a1", GraphEdit.Type.FRAGMENT_ATTACHMENT, Set.of()),
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.FRAGMENT_ATTACHMENT),
                        Set.of("a1"), Set.of("a1"), Set.of())))
                .hasMessageContaining("protected");
        assertThatThrownBy(() -> engine.apply(parent(), attachment("missing", GraphEdit.Type.FRAGMENT_ATTACHMENT, Set.of()),
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.FRAGMENT_ATTACHMENT),
                        Set.of("missing"), Set.of(), Set.of())))
                .hasMessageContaining("anchor missing");
    }

    @Test
    void pruneAndAttachmentCannotBypassAnchorAuthorization() {
        var replacement = attachment("a1", GraphEdit.Type.SUBSTITUENT_REPLACEMENT, Set.of("a2"));
        assertThatThrownBy(() -> engine.apply(parent(), replacement,
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.SUBSTITUENT_REPLACEMENT),
                        Set.of("a2"), Set.of("a1"), Set.of())))
                .hasMessageContaining("outside permitted region");
    }

    @Test
    void substitutionOnlyRemovesTerminalHydrogensOnTheSubstitutedAtomDeterministically() {
        var graph = graphWithHydrogens();
        var valid = new GraphEdit("sub", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("center", "h2", "h1"), Set.of(), null, null, "N", null,
                Map.of("substitutionAtomId", "center"));
        var authorization = new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.ATOM_SUBSTITUTION),
                Set.of("center", "h1", "h2"), Set.of(), Set.of());
        var first = engine.apply(graph, valid, authorization);
        var second = engine.apply(graph, valid, authorization);
        assertThat(first).isEqualTo(second);
        assertThat(first.receipt().deletedAtomIds()).containsExactly("h1", "h2");

        var elsewhere = new GraphEdit("elsewhere", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("center", "otherH"), Set.of(), null, null, "N", null,
                Map.of("substitutionAtomId", "center"));
        assertThatThrownBy(() -> engine.apply(graph, elsewhere,
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.ATOM_SUBSTITUTION),
                        Set.of("center", "otherH"), Set.of(), Set.of())))
                .hasMessageContaining("not bonded to substitution atom");

        var nonHydrogen = new GraphEdit("nonH", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("center", "other"), Set.of(), null, null, "N", null,
                Map.of("substitutionAtomId", "center"));
        assertThatThrownBy(() -> engine.apply(graph, nonHydrogen,
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.ATOM_SUBSTITUTION),
                        Set.of("center", "other"), Set.of(), Set.of())))
                .hasMessageContaining("explicit hydrogen");
    }

    @Test
    void substitutionRejectsHydrogenWithMultipleBonds() {
        var base = graphWithHydrogens();
        var bonds = new java.util.ArrayList<>(base.bonds());
        bonds.add(new MolecularGraph.Bond("badH", "h1", "other", MolecularGraph.BondOrder.SINGLE,
                false, "UNSPECIFIED", Map.of()));
        var graph = new MolecularGraph(base.atoms(), bonds, Map.of());
        var edit = new GraphEdit("multi", "v1", GraphEdit.Type.ATOM_SUBSTITUTION,
                Set.of("center", "h1"), Set.of(), null, null, "N", null,
                Map.of("substitutionAtomId", "center"));
        assertThatThrownBy(() -> engine.apply(graph, edit,
                new GraphEditTransactionEngine.Authorization("v1", Set.of(GraphEdit.Type.ATOM_SUBSTITUTION),
                        Set.of("center", "h1"), Set.of(), Set.of())))
                .hasMessageContaining("must be terminal");
    }

    private static GraphEdit attachment(String anchor, GraphEdit.Type type, Set<String> affected) {
        var fragment = new MolecularGraph(List.of(atom("fragment", "F")), List.of(), Map.of());
        return new GraphEdit("attachment", "v1", type, affected, Set.of(), anchor, fragment,
                null, MolecularGraph.BondOrder.SINGLE,
                Map.of("fragmentAnchorAtomId", "fragment", "attachmentBondId", "attachment-bond"));
    }

    private static MolecularGraph graphWithHydrogens() {
        return new MolecularGraph(
                List.of(atom("center", "C"), atom("other", "C"), atom("h1", "H"),
                        atom("h2", "H"), atom("otherH", "H")),
                List.of(
                        new MolecularGraph.Bond("center-other", "center", "other", MolecularGraph.BondOrder.SINGLE, false, "UNSPECIFIED", Map.of()),
                        new MolecularGraph.Bond("center-h1", "center", "h1", MolecularGraph.BondOrder.SINGLE, false, "UNSPECIFIED", Map.of()),
                        new MolecularGraph.Bond("center-h2", "center", "h2", MolecularGraph.BondOrder.SINGLE, false, "UNSPECIFIED", Map.of()),
                        new MolecularGraph.Bond("other-h", "other", "otherH", MolecularGraph.BondOrder.SINGLE, false, "UNSPECIFIED", Map.of())),
                Map.of());
    }

    private static GraphEditTransactionEngine.Authorization authorization(Set<GraphEdit.Type> types,
                                                                           Set<String> editable) {
        return new GraphEditTransactionEngine.Authorization("v1", types, editable, Set.of("a1"), Set.of());
    }

    private static MolecularGraph parent() {
        return new MolecularGraph(List.of(atom("a1", "C"), atom("a2", "C")),
                List.of(new MolecularGraph.Bond("b1", "a1", "a2", MolecularGraph.BondOrder.SINGLE,
                        false, "UNSPECIFIED", Map.of())), Map.of("fixture", "unrelated-to-mettl7"));
    }

    private static MolecularGraph.Atom atom(String id, String element) {
        return new MolecularGraph.Atom(id, element, null, 0, 0, false,
                "UNSPECIFIED", null, Map.of());
    }
}
