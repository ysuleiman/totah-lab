package totah.lab.athena.sasa;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ShrakeRupleySasaTest {

    private static final double PROBE = ShrakeRupleySasa.DEFAULT_PROBE_RADIUS;
    private static final double CARBON_RADIUS =
            Element.C.getVanDerWaalsRadius();

    @Test
    void singleIsolatedAtomShouldHaveFullSphereArea() {
        Structure structure = structureOf(
                carbon("C1", 0.0, 0.0, 0.0));

        double expected =
                4.0 * Math.PI * Math.pow(CARBON_RADIUS + PROBE, 2.0);

        assertThat(ShrakeRupleySasa.total(structure))
                .isCloseTo(expected, within(expected * 0.01));
    }

    @Test
    void distantAtomsShouldEachBeFullyAccessible() {
        Structure structure = structureOf(
                carbon("C1", 0.0, 0.0, 0.0),
                carbon("C2", 25.0, 0.0, 0.0));

        double expected =
                4.0 * Math.PI * Math.pow(CARBON_RADIUS + PROBE, 2.0);

        ShrakeRupleySasa.SasaResult result =
                ShrakeRupleySasa.calculate(structure);

        assertThat(result.totalArea())
                .isCloseTo(2.0 * expected, within(2.0 * expected * 0.01));
        assertThat(result.areaOf(reference("C1")))
                .isCloseTo(expected, within(expected * 0.01));
        assertThat(result.areaOf(reference("C2")))
                .isCloseTo(expected, within(expected * 0.01));
    }

    @Test
    void buriedAtomShouldHaveSignificantlyReducedArea() {
        List<Atom> atoms = new ArrayList<>();
        atoms.add(carbon("C0", 0.0, 0.0, 0.0));
        double offset = 3.0;
        String[] names = {"CX1", "CX2", "CY1", "CY2", "CZ1", "CZ2"};
        double[][] directions = {
                {offset, 0, 0}, {-offset, 0, 0},
                {0, offset, 0}, {0, -offset, 0},
                {0, 0, offset}, {0, 0, -offset}
        };
        for (int i = 0; i < names.length; i++) {
            atoms.add(carbon(
                    names[i],
                    directions[i][0],
                    directions[i][1],
                    directions[i][2]));
        }
        Structure structure = structureOf(atoms);

        double isolated =
                4.0 * Math.PI * Math.pow(CARBON_RADIUS + PROBE, 2.0);

        ShrakeRupleySasa.SasaResult result =
                ShrakeRupleySasa.calculate(structure);

        assertThat(result.areaOf(reference("C0")))
                .isLessThan(isolated * 0.2);
        assertThat(result.areaOf(reference("CX1")))
                .isLessThan(isolated);
    }

    @Test
    void higherSampleDensityShouldStayCloseToExactArea() {
        Structure structure = structureOf(
                carbon("C1", 0.0, 0.0, 0.0));

        double expected =
                4.0 * Math.PI * Math.pow(CARBON_RADIUS + PROBE, 2.0);

        ShrakeRupleySasa.SasaResult result =
                ShrakeRupleySasa.calculate(structure, PROBE, 960);

        assertThat(result.totalArea())
                .isCloseTo(expected, within(expected * 0.005));
        assertThat(result.spherePointCount()).isEqualTo(960);
        assertThat(result.probeRadius()).isEqualTo(PROBE);
    }

    @Test
    void areaOfUnknownAtomShouldBeRejected() {
        Structure structure = structureOf(
                carbon("C1", 0.0, 0.0, 0.0));

        ShrakeRupleySasa.SasaResult result =
                ShrakeRupleySasa.calculate(structure);

        assertThatThrownBy(() -> result.areaOf(reference("ZZ")))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void invalidParametersShouldBeRejected() {
        Structure structure = structureOf(
                carbon("C1", 0.0, 0.0, 0.0));

        assertThatThrownBy(
                () -> ShrakeRupleySasa.calculate(structure, -1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> ShrakeRupleySasa.calculate(structure, PROBE, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> ShrakeRupleySasa.calculate(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void resultsShouldBeDeterministic() {
        Structure structure = structureOf(
                carbon("C1", 0.0, 0.0, 0.0),
                carbon("C2", 3.5, 0.0, 0.0));

        assertThat(ShrakeRupleySasa.calculate(structure).areaByAtom())
                .isEqualTo(
                        ShrakeRupleySasa.calculate(structure).areaByAtom());
    }

    private static Atom carbon(String name, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .element(Element.C)
                .build();
    }

    private static AtomReference reference(String atomName) {
        return new AtomReference("A", 1, ' ', atomName);
    }

    private static Structure structureOf(Atom... atoms) {
        return structureOf(List.of(atoms));
    }

    private static Structure structureOf(List<Atom> atoms) {
        return new Structure(List.of(
                new Chain("A", List.of(
                        new Residue("GLY", 1, atoms)))));
    }
}
