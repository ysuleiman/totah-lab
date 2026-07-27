package totah.lab.protein;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ElementResolverTest {

    @Test
    void trustsExplicitElement() {
        Atom atom = Atom.builder()
                .name("CA")
                .element(Element.builder().symbol("mg").build())
                .build();

        assertThat(ElementResolver.resolveSymbol(atom, false)).isEqualTo("Mg");
    }

    @Test
    void treatsProteinCaAsCarbon() {
        Atom atom = Atom.builder().name("CA").build();

        assertThat(ElementResolver.resolveSymbol(atom, false)).isEqualTo("C");
    }

    @Test
    void treatsMonoatomicCaAsCalcium() {
        Atom atom = Atom.builder().name("CA").build();

        assertThat(ElementResolver.resolveSymbol(atom, true)).isEqualTo("Ca");
    }

    @Test
    void stripsLeadingHydrogenDigits() {
        Atom atom = Atom.builder().name("1HB").build();

        assertThat(ElementResolver.resolveSymbol(atom, false)).isEqualTo("H");
    }

    @Test
    void resolvesCommonTwoLetterElementsCanonically() {
        assertThat(ElementResolver.resolveSymbol(Atom.builder().name("CL").build(), false)).isEqualTo("Cl");
        assertThat(ElementResolver.resolveSymbol(Atom.builder().name("zn").build(), false)).isEqualTo("Zn");
        assertThat(ElementResolver.resolveSymbol(Atom.builder().name("NA").build(), true)).isEqualTo("Na");
    }
}
