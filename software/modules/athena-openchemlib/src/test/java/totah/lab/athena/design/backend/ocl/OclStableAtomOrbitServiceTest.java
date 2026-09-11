package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.MolecularGraph;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OclStableAtomOrbitServiceTest {
    @org.junit.jupiter.api.Test void explicitEquivalentHydrogensUseTheirParentOrbit() throws Exception {
        var atoms=java.util.List.of(atom("c","C"),atom("h1","H"),atom("h2","H"));
        var graph=new MolecularGraph(atoms,java.util.List.of(
                new MolecularGraph.Bond("b1","c","h1",MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",java.util.Map.of()),
                new MolecularGraph.Bond("b2","c","h2",MolecularGraph.BondOrder.SINGLE,false,"UNSPECIFIED",java.util.Map.of())),java.util.Map.of());
        var result=new OclStableAtomOrbitService().canonicalOrbits(graph).sourceAtomToCanonicalOrbit();
        org.assertj.core.api.Assertions.assertThat(result.get("h1")).isEqualTo(result.get("h2"));
    }
    @Test void namesAndInputOrderDoNotChangeChemistryCanonicalOrbitPartition() throws Exception {
        MolecularGraph first = chain("arbitrary-C1", "arbitrary-O", "arbitrary-C2");
        MolecularGraph renamedAndReordered = chain("right-carbon", "oxygen", "left-carbon",
                List.of(2, 1, 0));
        var service = new OclStableAtomOrbitService();
        var a = service.canonicalOrbits(first);
        var b = service.canonicalOrbits(renamedAndReordered);
        assertThat(a.sourceAtomToCanonicalOrbit().get("arbitrary-C1"))
                .isEqualTo(a.sourceAtomToCanonicalOrbit().get("arbitrary-C2"));
        assertThat(b.sourceAtomToCanonicalOrbit().get("right-carbon"))
                .isEqualTo(b.sourceAtomToCanonicalOrbit().get("left-carbon"));
        assertThat(a.sourceAtomToCanonicalOrbit().values().stream().distinct().count()).isEqualTo(2);
        assertThat(b.sourceAtomToCanonicalOrbit().values().stream().distinct().count()).isEqualTo(2);
    }

    private static MolecularGraph chain(String carbon1, String oxygen, String carbon2) {
        return chain(carbon1, oxygen, carbon2, List.of(0, 1, 2));
    }
    private static MolecularGraph chain(String carbon1, String oxygen, String carbon2, List<Integer> order) {
        List<MolecularGraph.Atom> canonical = List.of(atom(carbon1, "C"), atom(oxygen, "O"), atom(carbon2, "C"));
        List<MolecularGraph.Atom> atoms = order.stream().map(canonical::get).toList();
        return new MolecularGraph(atoms, List.of(
                new MolecularGraph.Bond("b1", carbon1, oxygen, MolecularGraph.BondOrder.SINGLE, false, "NONE", Map.of()),
                new MolecularGraph.Bond("b2", oxygen, carbon2, MolecularGraph.BondOrder.SINGLE, false, "NONE", Map.of())), Map.of());
    }
    private static MolecularGraph.Atom atom(String id, String element) {
        return new MolecularGraph.Atom(id, element, null, 0, 0, false, "NONE", null, Map.of());
    }
}
