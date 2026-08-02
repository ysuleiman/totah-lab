package totah.lab.hephaestus.protein.flexibility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardResidueChiBondsTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("standardResidues")
    void preservesExactOrderedPolicy(String residue, List<StandardResidueChiBonds.ChiBond> expected) {
        assertTrue(StandardResidueChiBonds.supports(residue));
        assertEquals(expected, StandardResidueChiBonds.bondsFor(residue));
    }

    @Test
    void rigidSideChainsHaveEmptyLists() {
        assertTrue(StandardResidueChiBonds.bondsFor("ALA").isEmpty());
        assertTrue(StandardResidueChiBonds.bondsFor("GLY").isEmpty());
        assertTrue(StandardResidueChiBonds.bondsFor("PRO").isEmpty());
    }

    @Test
    void normalizesCaseAndPadding() {
        assertTrue(StandardResidueChiBonds.supports("  lys  "));
        assertEquals(StandardResidueChiBonds.bondsFor("LYS"), StandardResidueChiBonds.bondsFor("  lys  "));
    }

    @Test
    void unknownAndNullNamesAreExplicitlyUnsupported() {
        assertFalse(StandardResidueChiBonds.supports("MSE"));
        assertFalse(StandardResidueChiBonds.supports(null));
        assertEquals(List.of(), StandardResidueChiBonds.bondsFor("MSE"));
        assertEquals(List.of(), StandardResidueChiBonds.bondsFor(null));
    }

    @Test
    void returnedListsAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> StandardResidueChiBonds.bondsFor("LYS").add(bond("CE", "NZ")));
    }

    private static Stream<Arguments> standardResidues() {
        return Stream.of(
                entry("ARG","CA-CB","CB-CG","CG-CD","CD-NE"), entry("ASN","CA-CB","CB-CG"),
                entry("ASP","CA-CB","CB-CG"), entry("CYS","CA-CB","CB-SG"),
                entry("GLN","CA-CB","CB-CG","CG-CD"), entry("GLU","CA-CB","CB-CG","CG-CD"),
                entry("HIS","CA-CB"), entry("ILE","CA-CB","CB-CG1"), entry("LEU","CA-CB","CB-CG"),
                entry("LYS","CA-CB","CB-CG","CG-CD","CD-CE"), entry("MET","CA-CB","CB-CG","CG-SD"),
                entry("PHE","CA-CB"), entry("PRO"), entry("SER","CA-CB","CB-OG"),
                entry("THR","CA-CB","CB-OG1"), entry("TRP","CA-CB"), entry("TYR","CA-CB"),
                entry("VAL","CA-CB"), entry("ALA"), entry("GLY"));
    }

    private static Arguments entry(String residue, String... pairs) {
        return Arguments.of(residue, Arrays.stream(pairs).map(pair -> {
            int separator = pair.indexOf('-');
            return bond(pair.substring(0, separator), pair.substring(separator + 1));
        }).toList());
    }

    private static StandardResidueChiBonds.ChiBond bond(String parent, String child) {
        return new StandardResidueChiBonds.ChiBond(parent, child);
    }
}
