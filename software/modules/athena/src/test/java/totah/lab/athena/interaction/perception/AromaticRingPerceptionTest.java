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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AromaticRingPerceptionTest {

    private final AromaticRingPerception perception =
            new AromaticRingPerception();

    @Test
    void perceivesPheRingFromProteinTemplate() {
        Structure structure = proteinStructure("PHE", 43,
                List.of("CG", "CD1", "CD2", "CE1", "CE2", "CZ"));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(ring -> {
                    assertThat(ring.ringId()).isEqualTo("PHE A:43 ring0");
                    assertThat(ring.source())
                            .isEqualTo(PerceptionProvenance.PROTEIN_TEMPLATE);
                    assertThat(ring.degraded()).isFalse();
                    assertThat(ring.owner().residueNumber()).isEqualTo(43);
                    assertThat(ring.atoms())
                            .extracting(Atom::getName)
                            .containsExactly(
                                    "CG", "CD1", "CD2", "CE1", "CE2", "CZ");
                    // fixture atoms sit at (0, 0..5, 0)
                    assertThat(ring.centroid())
                            .isEqualTo(new Point3D(0.0, 2.5, 0.0));
                });
    }

    @Test
    void perceivesBothTrpRings() {
        Structure structure = proteinStructure("TRP", 10, List.of(
                "CG", "CD1", "NE1", "CE2", "CD2",
                "CE3", "CZ2", "CZ3", "CH2"));

        assertThat(perception.perceive(structure))
                .satisfiesExactly(
                        ring -> {
                            assertThat(ring.ringId())
                                    .isEqualTo("TRP A:10 ring0");
                            assertThat(ring.atoms())
                                    .extracting(Atom::getName)
                                    .containsExactly(
                                            "CG", "CD1", "NE1", "CE2", "CD2");
                        },
                        ring -> {
                            assertThat(ring.ringId())
                                    .isEqualTo("TRP A:10 ring1");
                            assertThat(ring.atoms())
                                    .extracting(Atom::getName)
                                    .containsExactly(
                                            "CD2", "CE2", "CE3",
                                            "CZ2", "CZ3", "CH2");
                        });
    }

    @Test
    void skipsIncompleteProteinTemplate() {
        Structure structure = proteinStructure("PHE", 43,
                List.of("CG", "CD1", "CD2", "CE1", "CE2")); // CZ missing

        assertThat(perception.perceive(structure)).isEmpty();
    }

    @Test
    void nonAromaticResidueYieldsNoRings() {
        Structure structure = proteinStructure("ALA", 5,
                List.of("CA", "CB", "N", "C", "O"));

        assertThat(perception.perceive(structure)).isEmpty();
    }

    @Test
    void perceivesLigandRingFromAromaticBonds() {
        Structure structure = ligandStructure(
                benzeneAtoms(), ringBonds("C1", "C2", "C3", "C4", "C5", "C6",
                        BondOrder.AROMATIC));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(ring -> {
                    assertThat(ring.ringId()).isEqualTo("LIG A:501 ring0");
                    assertThat(ring.source())
                            .isEqualTo(PerceptionProvenance.BOND_GRAPH);
                    assertThat(ring.atoms()).hasSize(6);
                });
    }

    @Test
    void fusedNaphthaleneYieldsTwoRingsAndNoPerimeterRing() {
        // Two 6-rings fused on the C5-C10 bond; the 10-membered perimeter
        // cycle carries the fusion bond as a chord and must be discarded.
        List<Atom> atoms = new ArrayList<>();
        for (int index = 1; index <= 10; index++) {
            atoms.add(atom("C" + index, "A"));
        }
        List<Bond> bonds = List.of(
                aromaticBond("C1", "C2"),
                aromaticBond("C2", "C3"),
                aromaticBond("C3", "C4"),
                aromaticBond("C4", "C5"),
                aromaticBond("C5", "C10"),
                aromaticBond("C10", "C1"),
                aromaticBond("C5", "C6"),
                aromaticBond("C6", "C7"),
                aromaticBond("C7", "C8"),
                aromaticBond("C8", "C9"),
                aromaticBond("C9", "C10"));

        List<AromaticRing> rings = perception.perceive(
                ligandStructure(atoms, bonds));

        assertThat(rings).hasSize(2);
        assertThat(rings).allSatisfy(ring -> {
            assertThat(ring.source())
                    .isEqualTo(PerceptionProvenance.BOND_GRAPH);
            assertThat(ring.atoms()).hasSize(6);
        });
        assertThat(rings.stream()
                .map(ring -> ring.atoms().stream()
                        .map(Atom::getName)
                        .collect(Collectors.toSet())))
                .containsExactlyInAnyOrder(
                        Set.of("C1", "C2", "C3", "C4", "C5", "C10"),
                        Set.of("C5", "C6", "C7", "C8", "C9", "C10"));
    }

    @Test
    void kekuleBondsUseAd4TypedCandidatesOverBondGraph() {
        Structure structure = ligandStructure(
                benzeneAtoms(), ringBonds("C1", "C2", "C3", "C4", "C5", "C6",
                        BondOrder.SINGLE));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(ring -> {
                    assertThat(ring.source())
                            .isEqualTo(PerceptionProvenance.BOND_GRAPH);
                    assertThat(ring.note()).contains("AD4");
                    assertThat(ring.atoms()).hasSize(6);
                });
    }

    @Test
    void degradesToAd4PseudoRingWhenConnectivityAbsent() {
        Structure structure = new Structure(List.of(new Chain("A",
                List.of(new Residue("LIG", 501, List.of(
                        atom("C1", "A"),
                        atom("C2", "A"),
                        atom("C3", "A"),
                        atom("C4", "A"),
                        atom("C5", "A"),
                        atom("C6", "A"),
                        atom("O1", "OA")))))));

        assertThat(perception.perceive(structure))
                .singleElement()
                .satisfies(ring -> {
                    assertThat(ring.source())
                            .isEqualTo(PerceptionProvenance.AD4_FALLBACK);
                    assertThat(ring.degraded()).isTrue();
                    assertThat(ring.note()).contains("topology unknown");
                    assertThat(ring.atoms()).hasSize(6);
                });
    }

    @Test
    void fewerThanThreeAd4AromaticAtomsYieldNoFallbackRing() {
        Structure structure = new Structure(List.of(new Chain("A",
                List.of(new Residue("LIG", 501, List.of(
                        atom("C1", "A"),
                        atom("C2", "A"),
                        atom("O1", "OA")))))));

        assertThat(perception.perceive(structure)).isEmpty();
    }

    @Test
    void emptyStructureYieldsNoRings() {
        assertThat(perception.perceive(new Structure(List.of()))).isEmpty();
    }

    private static Structure proteinStructure(
            String residueName,
            int residueNumber,
            List<String> atomNames) {

        List<Atom> atoms = new ArrayList<>();
        int index = 0;
        for (String atomName : atomNames) {
            atoms.add(Atom.builder()
                    .pdbSerial(1)
                    .name(atomName)
                    .element(atomName.startsWith("N") ? Element.N
                            : Element.C)
                    .autoDockType(atomName.startsWith("N") ? "N" : "C")
                    .position(new Point3D(0.0, index++, 0.0))
                    .occupancy(1.0)
                    .build());
        }
        return new Structure(List.of(new Chain("A",
                List.of(new Residue(residueName, residueNumber, atoms)))));
    }

    private static List<Atom> benzeneAtoms() {
        List<Atom> atoms = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            atoms.add(atom("C" + index, "A"));
        }
        return atoms;
    }

    private static List<Bond> ringBonds(
            String first,
            String second,
            String third,
            String fourth,
            String fifth,
            String sixth,
            BondOrder order) {

        List<String> names =
                List.of(first, second, third, fourth, fifth, sixth);
        List<Bond> bonds = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            bonds.add(new Bond(
                    new AtomReference("A", 501, ' ', names.get(index)),
                    new AtomReference("A", 501, ' ',
                            names.get((index + 1) % names.size())),
                    order));
        }
        return bonds;
    }

    private static Structure ligandStructure(
            List<Atom> atoms,
            List<Bond> bonds) {

        return new Structure(
                List.of(new Chain("A",
                        List.of(new Residue("LIG", 501, atoms)))),
                bonds,
                ConnectivityProvenance.EXPLICIT);
    }

    private static Bond aromaticBond(String firstName, String secondName) {
        return new Bond(
                new AtomReference("A", 501, ' ', firstName),
                new AtomReference("A", 501, ' ', secondName),
                BondOrder.AROMATIC);
    }

    private static Atom atom(String name, String autoDockType) {
        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .element(name.startsWith("O") ? Element.O : Element.C)
                .autoDockType(autoDockType)
                .position(new Point3D(0.0, 0.0, 0.0))
                .occupancy(1.0)
                .build();
    }
}
