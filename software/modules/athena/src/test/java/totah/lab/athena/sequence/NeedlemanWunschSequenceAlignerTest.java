package totah.lab.athena.sequence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeedlemanWunschSequenceAlignerTest {

    private final NeedlemanWunschSequenceAligner aligner =
            new NeedlemanWunschSequenceAligner();

    @Test
    void identicalSequencesAlignEveryResidue() {
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ARG", "ASN", "ASP"),
                sequence(10, "ALA", "ARG", "ASN", "ASP")
        );

        assertEquals(1.0, alignment.identity(), 1.0e-12);
        assertEquals(4, alignment.pairs().size());
        assertPair(alignment.pairs().get(0), 1, 10, "ALA", "ALA");
        assertPair(alignment.pairs().get(3), 4, 13, "ASP", "ASP");
    }

    @Test
    void candidateInsertionGapsTheQuery() {
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ARG", "ASN"),
                sequence(5, "ALA", "ARG", "GLY", "ASN")
        );

        assertEquals(3, alignment.pairs().size());
        assertPair(alignment.pairs().get(0), 1, 5, "ALA", "ALA");
        assertPair(alignment.pairs().get(1), 2, 6, "ARG", "ARG");
        assertPair(alignment.pairs().get(2), 3, 8, "ASN", "ASN");
        assertEquals(1.0, alignment.identity(), 1.0e-12);
    }

    @Test
    void candidateDeletionGapsTheCandidate() {
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ARG", "GLY", "ASN"),
                sequence(5, "ALA", "ARG", "ASN")
        );

        assertEquals(3, alignment.pairs().size());
        assertPair(alignment.pairs().get(0), 1, 5, "ALA", "ALA");
        assertPair(alignment.pairs().get(1), 2, 6, "ARG", "ARG");
        assertPair(alignment.pairs().get(2), 4, 7, "ASN", "ASN");
        assertEquals(1.0, alignment.identity(), 1.0e-12);
    }

    @Test
    void mismatchRunAlignsWithoutGaps() {
        // Three mismatches score -3; any gapped alternative costs at
        // least 2 * (2 * -2) = -8 plus one match, so the diagonal run
        // wins.
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ALA", "ALA"),
                sequence(1, "GLY", "GLY", "GLY")
        );

        assertEquals(3, alignment.pairs().size());
        assertEquals(0.0, alignment.identity(), 1.0e-12);
        assertPair(alignment.pairs().get(1), 2, 2, "ALA", "GLY");
    }

    @Test
    void gapBeatsMismatchWhenMismatchIsTooCostly() {
        // Match +2 with one gap (-2) totals 0, beating a mismatch
        // (-1) forced alignment of ARG onto GLY plus ... : the gap
        // path yields pairs (ALA, ALA) and (ASN, ASN) only.
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ARG", "ASN"),
                sequence(1, "ALA", "ASN")
        );

        assertEquals(2, alignment.pairs().size());
        assertPair(alignment.pairs().get(0), 1, 1, "ALA", "ALA");
        assertPair(alignment.pairs().get(1), 3, 2, "ASN", "ASN");
        assertEquals(1.0, alignment.identity(), 1.0e-12);
    }

    @Test
    void terminalGapsDoNotProducePairs() {
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ARG"),
                sequence(7, "GLY", "ALA", "ARG", "GLY")
        );

        assertEquals(2, alignment.pairs().size());
        assertPair(alignment.pairs().get(0), 1, 8, "ALA", "ALA");
        assertPair(alignment.pairs().get(1), 2, 9, "ARG", "ARG");
        assertEquals(1.0, alignment.identity(), 1.0e-12);
    }

    @Test
    void identityIsTheFractionOfIdenticalPairs() {
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ALA", "ARG", "ASN", "ASP"),
                sequence(1, "ALA", "GLY", "ASN", "CYS")
        );

        assertEquals(0.5, alignment.identity(), 1.0e-12);
    }

    @Test
    void residueNamesAreComparedCaseInsensitively() {
        SequenceAlignment alignment = aligner.align(
                sequence(1, "ala"),
                sequence(1, "ALA")
        );

        assertEquals(1.0, alignment.identity(), 1.0e-12);
    }

    @Test
    void emptySequenceProducesAnEmptyAlignment() {
        assertTrue(aligner.align(List.of(), sequence(1, "ALA"))
                .pairs().isEmpty());
        assertTrue(aligner.align(sequence(1, "ALA"), List.of())
                .pairs().isEmpty());
        assertEquals(
                0.0,
                aligner.align(List.of(), sequence(1, "ALA")).identity(),
                1.0e-12
        );
    }

    @Test
    void blankResidueNamesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SequenceResidue(1, " ")
        );
    }

    private static List<SequenceResidue> sequence(
            int firstResidueNumber,
            String... names
    ) {
        List<SequenceResidue> residues = new java.util.ArrayList<>();

        for (int index = 0; index < names.length; index++) {
            residues.add(new SequenceResidue(
                    firstResidueNumber + index,
                    names[index]
            ));
        }

        return residues;
    }

    private static void assertPair(
            AlignedResiduePair pair,
            int queryResidueNumber,
            int candidateResidueNumber,
            String queryResidueName,
            String candidateResidueName
    ) {
        assertEquals(queryResidueNumber, pair.queryResidueNumber());
        assertEquals(candidateResidueNumber, pair.candidateResidueNumber());
        assertEquals(queryResidueName, pair.queryResidueName());
        assertEquals(candidateResidueName, pair.candidateResidueName());
    }
}
