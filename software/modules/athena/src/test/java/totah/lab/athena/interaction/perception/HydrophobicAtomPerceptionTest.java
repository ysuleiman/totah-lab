package totah.lab.athena.interaction.perception;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HydrophobicAtomPerceptionTest {

    private final HydrophobicAtomPerception perception =
            new HydrophobicAtomPerception();

    @Test
    void perceivesCarbonsWithOnlyCarbonOrHydrogenNeighbors() {
        Structure structure = structure(List.of(
                        atom("C1", Element.C, "C"),
                        atom("C2", Element.C, "C"),
                        atom("H1", Element.H, "H"),
                        atom("O1", Element.O, "OA")),
                List.of(
                        bond("C1", "C2"),
                        bond("C1", "H1"),
                        bond("C2", "O1")));

        HydrophobicAtoms result = perception.perceive(structure);

        assertThat(result.provenance())
                .isEqualTo(PerceptionProvenance.BOND_GRAPH);
        assertThat(result.degraded()).isFalse();
        assertThat(result.atoms())
                .extracting(Atom::getName)
                .containsExactly("C1");
    }

    @Test
    void rejectsCarbonBondedToHeteroatomAndNonCarbonAtoms() {
        Structure structure = structure(List.of(
                        atom("C1", Element.C, "C"),
                        atom("O1", Element.O, "OA"),
                        atom("O2", Element.O, "OA")),
                List.of(
                        bond("C1", "O1"),
                        bond("O1", "O2")));

        // C1 has an oxygen neighbor; O1/O2 are not carbons at all.
        assertThat(perception.perceive(structure).atoms()).isEmpty();
    }

    @Test
    void degradesToAd4TypesWhenConnectivityAbsent() {
        Structure structure = new Structure(List.of(new Chain("A",
                List.of(new Residue("LIG", 1, List.of(
                        atom("C1", Element.C, "C"),
                        atom("C2", Element.C, "A"),
                        atom("O1", Element.O, "OA"),
                        atom("N1", Element.N, null)))))));

        HydrophobicAtoms result = perception.perceive(structure);

        assertThat(result.provenance())
                .isEqualTo(PerceptionProvenance.AD4_FALLBACK);
        assertThat(result.degraded()).isTrue();
        assertThat(result.note()).contains("ABSENT");
        assertThat(result.atoms())
                .extracting(Atom::getName)
                .containsExactlyInAnyOrder("C1", "C2");
    }

    @Test
    void degradesToAd4TypesWhenConnectivityPartial() {
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(new Residue("LIG", 1,
                        List.of(atom("C1", Element.C, "C")))))),
                List.of(),
                ConnectivityProvenance.PARTIAL);

        HydrophobicAtoms result = perception.perceive(structure);

        assertThat(result.provenance())
                .isEqualTo(PerceptionProvenance.AD4_FALLBACK);
        assertThat(result.atoms())
                .extracting(Atom::getName)
                .containsExactly("C1");
    }

    @Test
    void emptyStructureYieldsNoHydrophobicAtoms() {
        Structure structure = new Structure(List.of());

        HydrophobicAtoms result = perception.perceive(structure);

        assertThat(result.atoms()).isEmpty();
        assertThat(result.provenance())
                .isEqualTo(PerceptionProvenance.AD4_FALLBACK);
    }

    private static Structure structure(
            List<Atom> atoms,
            List<Bond> bonds) {

        return new Structure(
                List.of(new Chain("A",
                        List.of(new Residue("LIG", 1, atoms)))),
                bonds,
                ConnectivityProvenance.EXPLICIT);
    }

    private static Bond bond(String firstName, String secondName) {
        return new Bond(
                new AtomReference("A", 1, ' ', firstName),
                new AtomReference("A", 1, ' ', secondName),
                BondOrder.SINGLE);
    }

    private static Atom atom(
            String name,
            Element element,
            String autoDockType) {

        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .element(element)
                .autoDockType(autoDockType)
                .position(new Point3D(0.0, 0.0, 0.0))
                .occupancy(1.0)
                .build();
    }
}
