package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mettl7AdenosylCofactorRecognitionTest {
    @Test
    void recognizesSamAndSahWithoutTreatingProteinResiduesAsCofactor() {
        assertTrue(Mettl7IncrementalPosePostProcessor.isAdenosylCofactor("SAM"));
        assertTrue(Mettl7IncrementalPosePostProcessor.isAdenosylCofactor("SAH"));
        assertTrue(Mettl7IncrementalPosePostProcessor.isAdenosylCofactor("sah"));
        assertFalse(Mettl7IncrementalPosePostProcessor.isAdenosylCofactor("SER"));
    }
}
