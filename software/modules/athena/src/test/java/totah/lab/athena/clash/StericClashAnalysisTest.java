package totah.lab.athena.clash;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StericClashAnalysisTest {

    private static final double CARBON_RADIUS =
            Element.C.getVanDerWaalsRadius(); // 1.70
    private static final double CARBON_THRESHOLD =
            (CARBON_RADIUS + CARBON_RADIUS)
                    * StericClashAnalysis.DEFAULT_RADIUS_SCALE; // 2.38

    @Test
    void atomsFarApartYieldNoClashes() {
        Structure structure = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("C1", Element.C, 10.0, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(structure)).isEmpty();
    }

    @Test
    void distanceExactlyAtThresholdIsNotAClash() {
        // Boundary convention: strict < (documented in StericClashAnalysis),
        // so a pair at exactly radiusSum * radiusScale does not clash.
        // sqrt(t*t) == t exactly for IEEE round-to-nearest doubles.
        Structure structure = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("C1", Element.C, CARBON_THRESHOLD, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(structure)).isEmpty();
    }

    @Test
    void distanceJustBelowThresholdClashesAndJustAboveDoesNot() {
        Structure justBelow = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("C1", Element.C, CARBON_THRESHOLD - 1.0e-9, 0.0, 0.0));
        Structure justAbove = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("C1", Element.C, CARBON_THRESHOLD + 1.0e-9, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(justBelow)).hasSize(1);
        assertThat(StericClashAnalysis.findClashes(justAbove)).isEmpty();
    }

    @Test
    void clearOverlapReportsClashWithCorrectPairAndAmount() {
        Structure structure = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("C2", Element.C, 1.0, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(structure))
                .singleElement()
                .satisfies(clash -> {
                    assertThat(clash.first())
                            .isEqualTo(new AtomReference("A", 1, ' ', "C1"));
                    assertThat(clash.second())
                            .isEqualTo(new AtomReference("A", 2, ' ', "C2"));
                    assertThat(clash.distance()).isEqualTo(1.0);
                    assertThat(clash.overlapAmount())
                            .isEqualTo(2 * CARBON_RADIUS - 1.0);
                });
    }

    @Test
    void clashRecordCanonicalizesAtomOrder() {
        AtomReference first = new AtomReference("A", 1, ' ', "C1");
        AtomReference second = new AtomReference("A", 2, ' ', "C1");

        StericClashAnalysis.Clash clash =
                new StericClashAnalysis.Clash(second, first, 1.0, 2.4);

        assertThat(clash.first()).isEqualTo(first);
        assertThat(clash.second()).isEqualTo(second);
    }

    @Test
    void hydrogenAtomsAreExcluded() {
        Structure structure = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("H1", Element.H, 0.3, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(structure)).isEmpty();
    }

    @Test
    void atomsWithoutElementAreExcluded() {
        Structure structure = twoResidueStructure(
                atom("C1", Element.C, 0.0, 0.0, 0.0),
                atom("X1", null, 0.1, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(structure)).isEmpty();
    }

    @Test
    void unknownElementUsesDefaultUnknownRadius() {
        // UNKNOWN radius falls back to DEFAULT_UNKNOWN_RADIUS (1.70), so the
        // threshold matches two carbons: 2.2 < 2.38 clashes.
        Structure unknownPair = twoResidueStructure(
                atom("X1", Element.UNKNOWN, 0.0, 0.0, 0.0),
                atom("X2", Element.UNKNOWN, 2.2, 0.0, 0.0));
        // Nitrogen radius is 1.55: threshold is 2.17, so 2.2 does not clash.
        Structure nitrogenPair = twoResidueStructure(
                atom("N1", Element.N, 0.0, 0.0, 0.0),
                atom("N2", Element.N, 2.2, 0.0, 0.0));

        assertThat(StericClashAnalysis.findClashes(unknownPair)).hasSize(1);
        assertThat(StericClashAnalysis.findClashes(nitrogenPair)).isEmpty();
    }

    @Test
    void sameResiduePairsAreExcluded() {
        Structure structure = new Structure(List.of(new Chain("A", List.of(
                new Residue("GLY", 1, List.of(
                        atom("C1", Element.C, 0.0, 0.0, 0.0),
                        atom("C2", Element.C, 0.5, 0.0, 0.0)))))));

        assertThat(StericClashAnalysis.findClashes(structure)).isEmpty();
    }

    @Test
    void bondedPairsAreExcluded() {
        Atom first = atom("C1", Element.C, 0.0, 0.0, 0.0);
        Atom second = atom("C1", Element.C, 0.5, 0.0, 0.0);
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(
                        new Residue("GLY", 1, List.of(first)),
                        new Residue("GLY", 2, List.of(second))))),
                List.of(new Bond(
                        new AtomReference("A", 1, ' ', "C1"),
                        new AtomReference("A", 2, ' ', "C1"),
                        BondOrder.SINGLE)));

        assertThat(StericClashAnalysis.findClashes(structure)).isEmpty();
    }

    @Test
    void resultsAreDeterministicAcrossRuns() {
        Structure structure = new Structure(List.of(new Chain("A", List.of(
                new Residue("GLY", 1, List.of(
                        atom("C1", Element.C, 0.0, 0.0, 0.0),
                        atom("N1", Element.N, 3.0, 0.0, 0.0))),
                new Residue("GLY", 2, List.of(
                        atom("C1", Element.C, 0.8, 0.0, 0.0),
                        atom("O1", Element.O, 3.5, 0.5, 0.0))),
                new Residue("GLY", 3, List.of(
                        atom("C1", Element.C, 1.6, 0.2, 0.0)))))));

        List<StericClashAnalysis.Clash> first =
                StericClashAnalysis.findClashes(structure);
        List<StericClashAnalysis.Clash> second =
                StericClashAnalysis.findClashes(structure);

        assertThat(first).isNotEmpty();
        assertThat(first).isEqualTo(second);
        // Pairs are emitted in structure traversal order.
        assertThat(first).isSortedAccordingTo(
                java.util.Comparator.comparing(
                        (StericClashAnalysis.Clash clash) -> clash.first())
                        .thenComparing(StericClashAnalysis.Clash::second));
    }

    private static Structure twoResidueStructure(Atom first, Atom second) {
        return new Structure(List.of(new Chain("A", List.of(
                new Residue("GLY", 1, List.of(first)),
                new Residue("GLY", 2, List.of(second))))));
    }

    private static Atom atom(
            String name,
            Element element,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .pdbSerial(1)
                .name(name)
                .element(element)
                .position(new Point3D(x, y, z))
                .occupancy(1.0)
                .build();
    }
}
