package totah.lab.athena.landscape;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CoordinateRealizerTest {
    @Test
    void zeroAndTranslationPreserveOrderAndAreDeterministic() {
        Structure source = ligand();
        var realizer = new CoordinateRealizer();
        var zero = perturbation(new Point3D(0, 0, 0), 0, Map.of());
        var first = realizer.realize(source, zero, identity(source), Map.of(), "frame-v1");
        var second = realizer.realize(source, zero, identity(source), Map.of(), "frame-v1");
        assertThat(positions(first.coordinates())).isEqualTo(positions(source));
        assertThat(positions(first.coordinates())).isEqualTo(positions(second.coordinates()));
        assertThat(names(first.coordinates())).containsExactlyElementsOf(names(source));

        var translated = realizer.realize(source,
                perturbation(new Point3D(2, -1, 3), 0, Map.of()),
                identity(source), Map.of(), "frame-v1");
        assertThat(positions(translated.coordinates()).getFirst())
                .isEqualTo(new Point3D(2, -1, 3));
    }

    @Test
    void rigidRotationAndInverseRoundTripAreCorrect() {
        Structure source = ligand();
        var realizer = new CoordinateRealizer();
        var rotated = realizer.realize(source,
                perturbation(new Point3D(0, 0, 0), 180, Map.of()),
                identity(source), Map.of(), "frame-v1").coordinates();
        assertThat(positions(rotated).getFirst().x()).isCloseTo(2.5, within(1e-12));
        assertThat(positions(rotated).get(2).x()).isCloseTo(0.5, within(1e-12));
        var roundTrip = realizer.realize(rotated,
                perturbation(new Point3D(0, 0, 0), -180, Map.of()),
                identity(rotated), Map.of(), "frame-v1").coordinates();
        assertPointsClose(positions(roundTrip), positions(source));
    }

    @Test
    void torsionRotatesOnlyDeclaredDownstreamAtoms() {
        Structure source = ligand();
        AtomReference a = ref("A"), b = ref("B"), c = ref("C"), d = ref("D");
        var torsion = new CoordinateRealizer.TorsionDefinition(a, b, Set.of(c, d));
        Structure result = new CoordinateRealizer().realize(source,
                perturbation(new Point3D(0, 0, 0), 0, Map.of("phi", 90.0)),
                identity(source), Map.of("phi", torsion), "frame-v1").coordinates();
        assertThat(positions(result).get(2).z()).isCloseTo(1.0, within(1e-12));
        assertThat(positions(result).get(2).y()).isCloseTo(0.0, within(1e-12));
        assertThat(positions(result).getFirst()).isEqualTo(positions(source).getFirst());
    }

    @Test
    void sameDescriptorGivesIdenticalGeometryForMatchedFrames() {
        var q = perturbation(new Point3D(.5, -.5, 1), 30, Map.of());
        Structure a = ligand(), b = ligand();
        var realizer = new CoordinateRealizer();
        assertThat(positions(realizer.realize(a, q, identity(a), Map.of(), "shared").coordinates()))
                .isEqualTo(positions(realizer.realize(b, q, identity(b), Map.of(), "shared").coordinates()));
    }

    @Test
    void multipleTorsionsUseCanonicalIdentifierOrderRegardlessOfMapOrder() {
        Structure source = ligand();
        var first = new LinkedHashMap<String, Double>();
        first.put("zeta", 35.0);
        first.put("alpha", -20.0);
        var reversed = new LinkedHashMap<String, Double>();
        reversed.put("alpha", -20.0);
        reversed.put("zeta", 35.0);
        Map<String, CoordinateRealizer.TorsionDefinition> allowed = Map.of(
                "alpha", new CoordinateRealizer.TorsionDefinition(
                        ref("A"), ref("B"), Set.of(ref("C"), ref("D"))),
                "zeta", new CoordinateRealizer.TorsionDefinition(
                        ref("B"), ref("C"), Set.of(ref("D"))));
        var realizer = new CoordinateRealizer();
        var one = realizer.realize(source,
                perturbation(new Point3D(0, 0, 0), 0, first),
                identity(source), allowed, "frame");
        var two = realizer.realize(source,
                perturbation(new Point3D(0, 0, 0), 0, reversed),
                identity(source), allowed, "frame");
        assertThat(positions(one.coordinates())).isEqualTo(positions(two.coordinates()));
    }

    @Test
    void rejectsInvalidCorrespondenceAxisAndRotatingSet() {
        Structure source = ligand();
        Map<AtomReference, AtomReference> duplicateTarget = new LinkedHashMap<>(identity(source));
        duplicateTarget.put(ref("A"), ref("B"));
        assertThat(new CoordinateRealizer().realize(source,
                perturbation(new Point3D(0, 0, 0), 0, Map.of()),
                duplicateTarget, Map.of(), "frame").rejectionReason())
                .isEqualTo("ATOM_CORRESPONDENCE_INCOMPLETE_OR_NONBIJECTIVE");

        var nonbonded = new CoordinateRealizer.TorsionDefinition(
                ref("A"), ref("C"), Set.of(ref("D")));
        assertThat(new CoordinateRealizer().realize(source,
                perturbation(new Point3D(0, 0, 0), 0, Map.of("phi", 10.0)),
                identity(source), Map.of("phi", nonbonded), "frame").rejectionReason())
                .isEqualTo("TORSION_AXIS_NOT_BONDED:phi");

        var incomplete = new CoordinateRealizer.TorsionDefinition(
                ref("A"), ref("B"), Set.of(ref("D")));
        assertThat(new CoordinateRealizer().realize(source,
                perturbation(new Point3D(0, 0, 0), 0, Map.of("phi", 10.0)),
                identity(source), Map.of("phi", incomplete), "frame").rejectionReason())
                .isEqualTo("TORSION_ROTATING_SET_INVALID:phi");

        Structure degenerate = degenerateLigand();
        var validSet = new CoordinateRealizer.TorsionDefinition(
                ref("A"), ref("B"), Set.of(ref("C")));
        assertThat(new CoordinateRealizer().realize(degenerate,
                perturbation(new Point3D(0, 0, 0), 0, Map.of("phi", 10.0)),
                identity(degenerate), Map.of("phi", validSet), "frame").rejectionReason())
                .isEqualTo("TORSION_AXIS_DEGENERATE:phi");
    }

    @Test
    void recordsExactRotationCenterAndRejectsBlankTorsionIdentifier() {
        var result = new CoordinateRealizer().realize(ligand(),
                perturbation(new Point3D(0, 0, 0), 0, Map.of()),
                identity(ligand()), Map.of(), "frame");
        assertThat(result.rotationCenterAngstroms()).isEqualTo(new Point3D(1.25, .75, 0));
        assertThatThrownBy(() -> perturbation(new Point3D(0, 0, 0), 0,
                Map.of(" ", 1.0))).isInstanceOf(IllegalArgumentException.class);
    }

    private static Perturbation perturbation(Point3D translation, double rz,
            Map<String, Double> torsions) {
        return new Perturbation("q", translation,
                new LigandConfiguration.EulerRotation(0, 0, rz), torsions);
    }

    private static Structure ligand() {
        return new Structure(List.of(new Chain("L", List.of(new Residue("LIG", 1,
                List.of(atom(1, "A", 0, 0, 0), atom(2, "B", 1, 0, 0),
                        atom(3, "C", 2, 1, 0), atom(4, "D", 2, 2, 0)))))),
                List.of(new Bond(ref("A"), ref("B"), BondOrder.SINGLE),
                        new Bond(ref("B"), ref("C"), BondOrder.SINGLE),
                        new Bond(ref("C"), ref("D"), BondOrder.SINGLE)));
    }

    private static Structure degenerateLigand() {
        return new Structure(List.of(new Chain("L", List.of(new Residue("LIG", 1,
                List.of(atom(1, "A", 0, 0, 0), atom(2, "B", 0, 0, 0),
                        atom(3, "C", 1, 0, 0)))))),
                List.of(new Bond(ref("A"), ref("B"), BondOrder.SINGLE),
                        new Bond(ref("B"), ref("C"), BondOrder.SINGLE)));
    }

    private static Atom atom(int serial, String name, double x, double y, double z) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(x, y, z))
                .element(Element.C).build();
    }

    private static AtomReference ref(String name) {
        return new AtomReference("L", 1, ' ', name);
    }

    private static Map<AtomReference, AtomReference> identity(Structure structure) {
        Map<AtomReference, AtomReference> map = new LinkedHashMap<>();
        for (String name : names(structure)) map.put(ref(name), ref(name));
        return map;
    }

    private static List<String> names(Structure structure) {
        return structure.getChains().getFirst().residues().getFirst().getAtoms().stream()
                .map(Atom::getName).toList();
    }

    private static List<Point3D> positions(Structure structure) {
        return structure.getChains().getFirst().residues().getFirst().getAtoms().stream()
                .map(Atom::getPosition).toList();
    }

    private static void assertPointsClose(List<Point3D> actual, List<Point3D> expected) {
        for (int i = 0; i < actual.size(); i++) {
            assertThat(actual.get(i).distance(expected.get(i))).isLessThan(1e-12);
        }
    }
}
