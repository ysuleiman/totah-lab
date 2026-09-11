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
