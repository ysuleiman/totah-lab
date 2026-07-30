package totah.lab.docking.flex;

import org.junit.jupiter.api.Test;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlexResidueSelectorTest {

    private final FlexResidueSelector selector = new FlexResidueSelector();

    @Test
    void resolvesInsertionCodedResidueOnlyWhenEntryIncludesInsertionCode() {
        Residue plain = residue("LYS", 33, ' ');
        Residue inserted = residue("LYS", 33, 'A');

        Map<String, Residue> selectedPlain = selector.resolve(List.of(plain, inserted), List.of("A:33"));
        Map<String, Residue> selectedInserted = selector.resolve(List.of(plain, inserted), List.of("A:33A"));

        assertSame(plain, selectedPlain.get("A:33"));
        assertSame(inserted, selectedInserted.get("A:33A"));
    }

    @Test
    void rejectsInvalidInsertionCodeSyntax() {
        Residue plain = residue("LYS", 33, ' ');

        assertThrows(IllegalArgumentException.class,
                () -> selector.resolve(List.of(plain), List.of("A:33AB")));
    }

    private Residue residue(String name, int number, char insertionCode) {
        return Residue.builder()
                .name(name)
                .chain("A")
                .number(number)
                .insertionCode(insertionCode)
                .atoms(List.of(atom("CA", "C")))
                .build();
    }

    private Atom atom(String name, String element) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(0.0, 0.0, 0.0))
                .charge(0.0)
                .autoDockType("C")
                .element(Element.fromSymbol(element))
                .build();
    }
}
