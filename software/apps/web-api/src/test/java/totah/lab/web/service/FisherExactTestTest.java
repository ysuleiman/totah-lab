package totah.lab.web.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FisherExactTestTest {

    @Test
    void matchesClassicHypergeometricTail() {
        // Drawing all 4 flagged items out of 4 draws from an 8-item
        // background with 4 flagged: C(4,4)*C(4,0)/C(8,4) = 1/70.
        assertEquals(
                1.0 / 70.0,
                FisherExactTest.enrichmentPValue(4, 4, 4, 8),
                1e-12
        );
    }

    @Test
    void noFlaggedHitsGivesPValueOne() {
        assertEquals(
                1.0,
                FisherExactTest.enrichmentPValue(0, 4, 4, 8),
                1e-12
        );
    }

    @Test
    void partialOverlap() {
        // N=4, K=1, n=3: P(X>=1) = 1 - C(1,0)C(3,3)/C(4,3) = 0.75.
        assertEquals(
                0.75,
                FisherExactTest.enrichmentPValue(1, 3, 1, 4),
                1e-12
        );
    }

    @Test
    void rejectsInvalidCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FisherExactTest.enrichmentPValue(-1, 4, 4, 8)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FisherExactTest.enrichmentPValue(5, 4, 4, 8)
        );
    }
}
