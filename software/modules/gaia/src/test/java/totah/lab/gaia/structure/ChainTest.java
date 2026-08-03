package totah.lab.gaia.structure;


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChainTest {

    @Test
    void shouldRejectNullId() {
        assertThrows(
                NullPointerException.class,
                () -> new Chain(null, List.of()));
    }

    @Test
    void shouldRejectBlankId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Chain("   ", List.of()));
    }

    @Test
    void shouldRejectNullResidues() {
        assertThrows(
                NullPointerException.class,
                () -> new Chain("A", null));
    }

    @Test
    void shouldDefensivelyCopyResidues() {
        Residue residue = new Residue(
                "ALA",
                1,
                List.of());

        List<Residue> residues = new ArrayList<>();
        residues.add(residue);

        Chain chain = new Chain("A", residues);

        residues.clear();

        assertEquals(1, chain.residueCount());
    }

    @Test
    void shouldReturnResidueCount() {
        Chain chain = new Chain(
                "A",
                List.of(
                        new Residue("ALA", 1, List.of()),
                        new Residue("GLY", 2, List.of())
                ));

        assertEquals(2, chain.residueCount());
    }

    @Test
    void shouldReportEmptyChain() {
        Chain chain = new Chain("A", List.of());

        assertTrue(chain.isEmpty());
        assertEquals(0, chain.residueCount());
    }

    @Test
    void shouldFindResidueByNumber() {
        Residue ala = new Residue("ALA", 1, List.of());
        Residue gly = new Residue("GLY", 2, List.of());

        Chain chain = new Chain(
                "A",
                List.of(ala, gly));

        assertTrue(chain.findResidue(1).isPresent());
        assertEquals(
                ala,
                chain.findResidue(1).orElseThrow());

        assertTrue(chain.findResidue(2).isPresent());
        assertEquals(
                gly,
                chain.findResidue(2).orElseThrow());
    }

    @Test
    void shouldReturnEmptyWhenResidueDoesNotExist() {
        Chain chain = new Chain(
                "A",
                List.of(
                        new Residue("ALA", 1, List.of())
                ));

        assertTrue(chain.findResidue(100).isEmpty());
    }

    @Test
    void shouldFindResidueByNumberAndInsertionCode() {
        Residue first =
                new Residue("SER", 10, null, List.of());

        Residue second =
                new Residue("SER", 10, 'A', List.of());

        Chain chain = new Chain(
                "A",
                List.of(first, second));

        assertEquals(
                first,
                chain.findResidue(10, null).orElseThrow());

        assertEquals(
                second,
                chain.findResidue(10, 'A').orElseThrow());
    }

    @Test
    void shouldReportResiduePresence() {
        Chain chain = new Chain(
                "A",
                List.of(
                        new Residue("ALA", 5, List.of())
                ));

        assertTrue(chain.containsResidue(5));
        assertFalse(chain.containsResidue(6));
    }

    @Test
    void shouldPreferPlainResidueWhenFindingByNumber() {
        Residue plain =
                new Residue("SER", 10, null, List.of());
        Residue inserted =
                new Residue("SER", 10, 'A', List.of());

        Chain chain = new Chain(
                "A",
                List.of(inserted, plain));

        assertEquals(
                plain,
                chain.findResidue(10).orElseThrow());
    }

    @Test
    void shouldReturnSingleInsertionCodedMatchByNumber() {
        Residue inserted =
                new Residue("SER", 10, 'A', List.of());

        Chain chain = new Chain(
                "A",
                List.of(inserted));

        assertEquals(
                inserted,
                chain.findResidue(10).orElseThrow());
    }

    @Test
    void shouldThrowWhenNumberHasOnlyInsertionCodeSiblings() {
        Chain chain = new Chain(
                "A",
                List.of(
                        new Residue("SER", 10, 'A', List.of()),
                        new Residue("SER", 10, 'B', List.of())));

        assertThrows(
                IllegalStateException.class,
                () -> chain.findResidue(10));
    }

    @Test
    void shouldUseCanonicalResidueIdentity() {
        Residue residue = new Residue(
                "SER", 10, 'A', List.of());
        Chain chain = new Chain("A", List.of(residue));

        ResidueId identity = new ResidueId(" A ", 10, 'A');

        assertSame(residue, chain.findResidue(identity).orElseThrow());
        assertTrue(chain.contains(identity));
        assertTrue(chain.findResidue(
                new ResidueId("B", 10, 'A')).isEmpty());
    }
}
