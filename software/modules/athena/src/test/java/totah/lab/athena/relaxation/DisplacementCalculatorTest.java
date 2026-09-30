package totah.lab.athena.relaxation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DisplacementCalculatorTest {
    @Test
    void reportsFixedFrameRmsdAndBothMaximumDisplacements() {
        Structure before = structure(0, 0);
        Structure after = structure(3, 4);
        Map<AtomReference, AtomReference> mapping = Map.of(ref("A"), ref("A"), ref("B"), ref("B"));
        var result = DisplacementCalculator.calculate(new DisplacementCalculator.Inputs(
                before, after, before, after, before, after,
                mapping, mapping, mapping, mapping));
        assertThat(result.ligandHeavyAtomRmsdAngstroms())
                .isCloseTo(Math.sqrt(12.5), within(1e-12));
        assertThat(result.ligandMaximumDisplacementAngstroms()).isEqualTo(4.0);
        assertThat(result.samMaximumDisplacementAngstroms()).isEqualTo(4.0);
        assertThat(result.maximumSystemDisplacementAngstroms()).isEqualTo(4.0);
        assertThat(result.frameConvention()).isEqualTo("FIXED_RECEPTOR_FRAME_NO_SUPERPOSITION");
    }

    @Test
    void rejectsNonbijectiveAndUnresolvedMappings() {
        Structure structure = structure(0, 0);
        assertThatThrownBy(() -> DisplacementCalculator.calculate(
                new DisplacementCalculator.Inputs(structure, structure, structure, structure,
                        structure, structure, Map.of(ref("A"), ref("B"), ref("B"), ref("B")),
                        Map.of(), Map.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bijective");
        assertThatThrownBy(() -> DisplacementCalculator.calculate(
                new DisplacementCalculator.Inputs(structure, structure, structure, structure,
                        structure, structure, Map.of(ref("Z"), ref("A")),
                        Map.of(), Map.of(), Map.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unresolved");
    }

    private static Structure structure(double ax, double bx) {
        return new Structure(List.of(new Chain("L", List.of(new Residue("LIG", 1,
                List.of(atom(1, "A", ax), atom(2, "B", bx)))))));
    }

    private static Atom atom(int serial, String name, double x) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(x, 0, 0))
                .element(Element.C).build();
    }

    private static AtomReference ref(String name) {
        return new AtomReference("L", 1, ' ', name);
    }
}
