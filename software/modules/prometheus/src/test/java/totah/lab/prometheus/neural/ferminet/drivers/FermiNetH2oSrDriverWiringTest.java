package totah.lab.prometheus.neural.ferminet.drivers;

import totah.lab.prometheus.neural.ferminet.runtime.*;
import totah.lab.prometheus.neural.ferminet.pretraining.*;
import totah.lab.prometheus.neural.ferminet.drivers.*;
import totah.lab.prometheus.neural.ferminet.reference.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void oneIterationRemainsTheDefaultAndPreservesTheOneStepPath() throws IOException {
        String source = Files.readString(driverSource());

        assertTrue(source.contains("int iterations = 1;"));
        assertTrue(source.contains(
                "if (arguments.iterations() > 1 || resumeCheckpoint != null || branch != null)"));
        assertTrue(source.contains("optimizer.optimizeCheckpointed("));
        assertTrue(source.contains("sampleCanonicalVmc("),
                "one-step mode must retain independent post-SR validation");
    }

    @Test
    void multiIterationModeUsesOnePersistentOptimizerCall() throws IOException {
        String source = Files.readString(driverSource());
        int methodStart = source.indexOf("private static void runPersistentTrajectory(");
        int nextMethod = source.indexOf("private static void persistIteration(", methodStart);
        String method = source.substring(methodStart, nextMethod);

        assertEquals(1, occurrences(method, "optimizer.optimizeCheckpointed("));
        assertEquals(1, occurrences(method, "optimizer.resume("));
        assertEquals(0, occurrences(method, "oneIteration("));
        assertTrue(method.contains("arguments.iterations()"));
    }

    @Test
    void provenanceMismatchFailsClosed() {
        IllegalStateException mismatch = assertThrows(IllegalStateException.class,
                () -> FermiNetH2oSrDriver.verifyIdentity(
                        "wrong", "expected", "decoded parameter checksum"));
        assertTrue(mismatch.getMessage().contains("decoded parameter checksum mismatch"));
    }

    @Test
    void driverUsesDecodedParameterIdentityAndPersistsContinuity() throws IOException {
        String source = Files.readString(driverSource());
        int verification = source.indexOf("verifyProvenance(initialState");
        int optimizer = source.indexOf("new FermiNetVariationalOptimizer(");

        assertTrue(verification >= 0 && verification < optimizer,
                "provenance must be checked before optimizer construction");
        assertTrue(source.contains(
                "FermiNetPretrainingQualification.parameterChecksum(state)"));
        assertFalse(source.contains("sha256(arguments.parameterFile())"),
                "raw parameter-file SHA must not be used as canonical state identity");
        assertTrue(source.contains("expectedInputChecksum = outputChecksum"));
        assertTrue(source.contains("input_parameter_checksum"));
        assertTrue(source.contains("output_parameter_checksum"));
        assertTrue(source.contains("next_walker_checksum"));
        assertTrue(source.contains("continuation-checkpoint.bin"));
        assertTrue(source.contains("case \"--resume\""));
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
