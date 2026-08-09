package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PoseLabelTest {

    @Test
    void parsesSeedAndModeTokens() {
        PoseLabel label = PoseLabel.parse("DCMB-R vina s1 m3");

        assertEquals(1, label.seed());
        assertEquals(3, label.mode());
        assertNull(label.rank());
        assertNull(label.confidence());
    }

    @Test
    void parsesRankAndConfidenceTokens() {
        PoseLabel label = PoseLabel.parse(
                "DCMB-R diffdock 7B rank3 conf-1.429");

        assertNull(label.seed());
        assertNull(label.mode());
        assertEquals(3, label.rank());
        assertEquals(-1.429, label.confidence(), 1.0e-9);
    }

    @Test
    void unknownLabelsYieldNullFields() {
        PoseLabel label = PoseLabel.parse("METTL7-BRICS-0049");

        assertNull(label.seed());
        assertNull(label.mode());
        assertNull(label.rank());
        assertNull(label.confidence());
    }

    @Test
    void doesNotMistakeNameFragmentsForTokens() {
        PoseLabel label = PoseLabel.parse("MCULE-5056696566");

        assertNull(label.mode());
        assertNull(label.seed());
    }
}
