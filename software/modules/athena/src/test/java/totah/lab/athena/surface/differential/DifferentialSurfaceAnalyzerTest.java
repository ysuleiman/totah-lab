package totah.lab.athena.surface.differential;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentialSurfaceAnalyzerTest {
    @Test
    void unmatchedQueryResidueHasUnitRup() {
        SurfaceResidue query = surface("A", 1, "ALA", 0.0);
        DifferentialSurfaceMap map = new DifferentialSurfaceAnalyzer().analyze(
                List.of(query), List.of(),
                new ExplicitResidueCorrespondence(Map.of()),
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE);
        assertThat(map.residues().getFirst().rup()).isEqualTo(1.0);
        assertThat(map.residues().getFirst().rus()).isEqualTo(1.0);
        assertThat(map.residues().getFirst().rss()).isZero();
    }

    @Test
    void subjectOnlyNeighborContributesAsMaximumDifference() {
        SurfaceResidue query = surface("A", 1, "ALA", 0.0);
        SurfaceResidue matched = surface("B", 11, "ALA", 0.0);
        SurfaceResidue extra = surface("B", 12, "GLU", 2.0);
        DifferentialSurfaceMap map = new DifferentialSurfaceAnalyzer().analyze(
                List.of(query), List.of(matched, extra),
                new ExplicitResidueCorrespondence(Map.of(query.id(), matched.id())),
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE);
        assertThat(map.residues().getFirst().rup()).isZero();
        assertThat(map.residues().getFirst().rus()).isGreaterThan(0.0);
    }

    @Test
    void appliesStrictSurfaceThresholdBeforeNeighborhoodConstruction() {
        SurfaceResidue boundary = new SurfaceResidue(
                surface("A", 1, "ALA", 0.0).id(),
                surface("A", 1, "ALA", 0.0).residue(), 50.0, 0.05);
        DifferentialSurfaceMap map = new DifferentialSurfaceAnalyzer().analyze(
                List.of(boundary), List.of(),
                new ExplicitResidueCorrespondence(Map.of()),
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE);
        assertThat(map.residues()).isEmpty();
    }

    @Test
    void neighborhoodMembershipUsesAlphaCarbonRatherThanMinimumAtomDistance() {
        SurfaceResidue first = surface("A", 1, "ALA", 0.0);
        Atom closeSideChain = Atom.builder().pdbSerial(22).name("CB")
                .position(new Point3D(0.5, 0.0, 0.0)).element(Element.C).build();
        Atom distantAlpha = Atom.builder().pdbSerial(21).name("CA")
                .position(new Point3D(8.0, 0.0, 0.0)).element(Element.C).build();
        Residue residue = new Residue("ALA", 2, List.of(distantAlpha, closeSideChain));
        SurfaceResidue second = new SurfaceResidue(
                new ResidueId("A", 2, null), residue, 50.0, 0.5);
        Map<ResidueId, Map<ResidueId, Double>> neighborhoods =
                LocalResidueNeighborhood.build(List.of(first, second), 7.0);
        assertThat(neighborhoods.get(first.id())).doesNotContainKey(second.id());
    }

    @Test
    void neighborhoodsRetainDeterministicResidueOrder() {
        SurfaceResidue third = surface("A", 3, "ALA", 1.0);
        SurfaceResidue first = surface("A", 1, "ALA", 0.0);
        SurfaceResidue second = surface("A", 2, "ALA", 0.5);
        Map<ResidueId, Map<ResidueId, Double>> neighborhoods =
                LocalResidueNeighborhood.build(List.of(third, first, second), 7.0);
        assertThat(neighborhoods.keySet()).containsExactly(
                third.id(), first.id(), second.id());
        assertThat(neighborhoods.get(first.id()).keySet()).containsExactly(
                first.id(), second.id(), third.id());
    }

    private static SurfaceResidue surface(
            String chain, int number, String name, double x) {
        Atom atom = Atom.builder().pdbSerial(number).name("CA")
                .position(new Point3D(x, 0.0, 0.0)).element(Element.C).build();
        Residue residue = new Residue(name, number, List.of(atom));
        return new SurfaceResidue(new ResidueId(chain, number, null),
                residue, 50.0, 0.5);
    }
}
