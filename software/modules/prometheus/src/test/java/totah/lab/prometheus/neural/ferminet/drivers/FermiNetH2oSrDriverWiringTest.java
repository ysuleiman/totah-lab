package totah.lab.prometheus.neural.ferminet.drivers;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Guards canonical training and validation VMC against serial rewiring. */
final class FermiNetH2oSrDriverWiringTest {

    @Test
    void mainRoutesTrainingThroughOptimizerAndValidationThroughParallelVmc()
            throws IOException {
        String source = Files.readString(driverSource());
        int mainStart = source.indexOf("public static void main(String[] args)");
        int seamStart = source.indexOf(
                "static FermiNetRuntimeSampling.Result sampleCanonicalVmc(");

        assertTrue(mainStart >= 0, "canonical main method not found");
        assertTrue(seamStart > mainStart, "canonical VMC seam not found after main");

        String mainBody = source.substring(mainStart, seamStart);
        assertEquals(1, occurrences(mainBody, "sampleCanonicalVmc("),
                "post-SR validation must use the canonical VMC seam");
        assertTrue(mainBody.contains("new FermiNetVariationalOptimizer(VMC_PARALLELISM)"),
                "training VMC and update must use the canonical optimizer path");
        assertFalse(mainBody.contains("new FermiNetVmc()"),
                "canonical main must not instantiate the serial sampler");

        String seamBody = source.substring(seamStart,
                source.indexOf("static int canonicalVmcParallelism()", seamStart));
        assertTrue(seamBody.contains("FermiNetRuntimeSampling.sampleParallel("),
                "canonical VMC seam must delegate to deterministic parallel VMC");
    }

    private static Path driverSource() {
        String relative = "src/main/java/totah/lab/prometheus/neural/ferminet/"
                + "drivers/FermiNetH2oSrDriver.java";
        List<Path> candidates = List.of(
                Path.of(relative),
                Path.of("software/modules/prometheus").resolve(relative));
        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "cannot locate canonical FermiNet H2O SR driver source"));
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }
}
