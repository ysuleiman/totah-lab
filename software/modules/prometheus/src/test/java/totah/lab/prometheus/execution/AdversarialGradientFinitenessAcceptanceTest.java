package totah.lab.prometheus.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A3 residual oracle on the preflight gradient seam added in the second fix
 * round: a NaN/Infinity token anywhere in final_gradient_hartree_per_bohr.txt
 * must be rejected, never silently registered. Spec:
 * docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md test A3.
 */
class AdversarialGradientFinitenessAcceptanceTest {

    @TempDir Path tempDir;

    private Path gradient(String... lines) throws IOException {
        Path path = tempDir.resolve("final_gradient_hartree_per_bohr.txt");
        Files.write(path, List.of(lines));
        return path;
    }

    /** A3: non-finite token at first, middle and last position. */
    @Test
    void a3NonFiniteGradientTokenRejectedAtAnyPosition() throws IOException {
        for (String token : new String[] {"NaN", "Infinity", "-Infinity"}) {
            assertThrows(IOException.class, () -> ForceCampaignPreflightRunner
                    .readFiniteGradient(gradient(token, "0.13", "-0.27", "0.41", "-0.11", "0.29"), 6));
            assertThrows(IOException.class, () -> ForceCampaignPreflightRunner
                    .readFiniteGradient(gradient("0.13", "-0.27", token, "0.41", "-0.11", "0.29"), 6));
            assertThrows(IOException.class, () -> ForceCampaignPreflightRunner
                    .readFiniteGradient(gradient("0.13", "-0.27", "0.41", "-0.11", "0.29", token), 6));
        }
    }

    /** A3: wrong component count rejected; finite control parses exactly. */
    @Test
    void a3WrongCountRejectedAndFiniteControlParses() throws IOException {
        assertThrows(IOException.class, () -> ForceCampaignPreflightRunner
                .readFiniteGradient(gradient("0.13 -0.27 0.41"), 6));
        assertEquals(List.of(0.13, -0.27, 0.41, -0.11, 0.29, -0.37),
                ForceCampaignPreflightRunner.readFiniteGradient(
                        gradient("0.13 -0.27 0.41", "", "-0.11 0.29 -0.37"), 6));
    }
}
