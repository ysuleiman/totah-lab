package totah.lab.prometheus.neural.ferminet.drivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;

final class FermiNetH2oGeometryBranchTest {

    @Test
    void iteration17ParametersSeedIndependentManifestBranches() throws Exception {
        Path path = checkpoint();
        FermiNetOptimizationCheckpoint parent = FermiNetOptimizationCheckpoint.read(path);
        var minus = state("symmetric-minus", parent);
        var plus = state("symmetric-plus", parent);
        var minusProvenance = FermiNetH2oSrDriver.verifyAndCreateBranchProvenance(
                path, 20260818L, FermiNetH2oGeometryManifest.require("symmetric-minus"),
                minus, parent);
        var plusProvenance = FermiNetH2oSrDriver.verifyAndCreateBranchProvenance(
                path, 20260818L, FermiNetH2oGeometryManifest.require("symmetric-plus"),
                plus, parent);

        assertEquals(parent.parameterChecksum(), minusProvenance.parentParameterSha256());
        assertEquals(parent.parameterChecksum(),
                FermiNetPretrainingQualification.parameterChecksum(minus));
        assertEquals(FermiNetH2oGeometryManifest.require("symmetric-minus")
                .geometryIdentity(), minusProvenance.childGeometrySha256());
        assertNotEquals(minusProvenance.samplingSeed(), plusProvenance.samplingSeed());
        assertNotEquals(minusProvenance.walkerInitializationSeed(),
                plusProvenance.walkerInitializationSeed());
        assertNotEquals(minusProvenance.sessionIdentity(), plusProvenance.sessionIdentity());

        var minusWalkers = FermiNetH2oSrDriver.freshBranchWalkers(
                minus, minusProvenance.walkerInitializationSeed());
        var plusWalkers = FermiNetH2oSrDriver.freshBranchWalkers(
                plus, plusProvenance.walkerInitializationSeed());
        assertEquals(64, minusWalkers.size());
        assertEquals(64, plusWalkers.size());
        assertNotEquals(parent.walkers(), minusWalkers,
                "BRANCH_FROM must not copy canonical checkpoint walkers");
        assertNotEquals(minusWalkers, plusWalkers,
                "different geometries must have independent walker/RNG initialization");
    }

    @Test
    void canonicalCheckpointCannotBeRebrandedAsDisplacedContinuation() throws Exception {
        Path path = checkpoint();
        FermiNetOptimizationCheckpoint parent = FermiNetOptimizationCheckpoint.read(path);
        FermiNetV1State canonical = state("canonical", parent);
        assertThrows(IllegalArgumentException.class,
                () -> FermiNetH2oSrDriver.verifyAndCreateBranchProvenance(
                        path, 20260818L,
                        FermiNetH2oGeometryManifest.require("canonical"),
                        canonical, parent));
    }

    @Test
    void canonicalResumeAndBranchModesRemainStructurallySeparate() throws Exception {
        String source = Files.readString(driverSource());
        assertTrue(source.contains("--resume and --branch-from are mutually exclusive"));
        assertTrue(source.contains("--geometry and --branch-from must be specified together"));
        assertTrue(source.contains("optimizer.resume("));
        assertTrue(source.contains("optimizer.optimizeCheckpointed("));
        assertTrue(source.contains("geometryIdentity, arguments.iterations()"));
        assertTrue(source.contains("execution_mode\", \"BRANCH_FROM"));
    }

    private static FermiNetV1State state(
            String key, FermiNetOptimizationCheckpoint parent) {
        var molecule = FermiNetH2oGeometryManifest.require(key).molecule();
        var configuration = FermiNetV1Configuration.locked();
        var layout = new FermiNetParameterLayout(configuration, molecule);
        return new FermiNetV1State(molecule, configuration,
                FermiNetParameters.fromArray(layout, parent.parameters()));
    }

    private static Path checkpoint() {
        String relative = "artifacts/prometheus/h2o/ferminet/sr/"
                + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                + "continuation-checkpoint.bin";
        return List.of(Path.of(relative), Path.of("../../..").resolve(relative))
                .stream().map(Path::normalize).filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "missing qualified iteration-17 checkpoint"));
    }

    private static Path driverSource() {
        String relative = "src/main/java/totah/lab/prometheus/neural/ferminet/"
                + "drivers/FermiNetH2oSrDriver.java";
        return List.of(Path.of(relative), Path.of("software/modules/prometheus")
                        .resolve(relative)).stream()
                .filter(Files::isRegularFile).findFirst().orElseThrow();
    }
}
