package totah.lab.prometheus.ingest.authoritative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.recovery.RecoveryClassification;

class PyscfGeometricArtifactReaderTest {

    @TempDir
    Path temporary;

    private final PyscfGeometricArtifactReader reader = new PyscfGeometricArtifactReader();

    @Test
    void reconstructsTheThreeArchivedVerifiedMinimaAndHessiansWhenArchiveIsPresent() throws IOException {
        Path root = findRepositoryRoot();
        Path unit = root.resolve("analysis/mettl7-phase2/execution-unit-05O");
        Assumptions.assumeTrue(Files.isDirectory(unit), "project evidence archive is not present");

        double[] expectedEnergies = {-1477.9438395697157, -1477.9410218953778, -1477.9243060703888};
        String[] ids = {"MIN01", "MIN02", "MIN04"};
        for (int i = 0; i < ids.length; i++) {
            PyscfGeometricOptimization optimization = reader.readOptimization(
                    unit.resolve("qm-native-minima").resolve(ids[i]));
            PyscfHessianResult hessian = reader.readHessian(unit.resolve("hessians").resolve(ids[i]));
            assertThat(optimization.calculationId().value()).contains(ids[i]);
            assertThat(optimization.finalGeometry().value().orElseThrow().atoms()).hasSize(56);
            assertThat(optimization.finalEnergyHartree().value().orElseThrow())
                    .isCloseTo(expectedEnergies[i], within(5.0e-10));
            assertThat(hessian.cartesianDimension()).isEqualTo(168);
            assertThat(hessian.cartesianHessian().value().orElseThrow()).hasSize(168 * 168);
            assertThat(hessian.frequencies().value().orElseThrow()).hasSize(162);
            assertThat(hessian.artifactChecksumsVerified()).isTrue();
        }
    }

    @Test
    void reconstructsOptimizationFromStructuredArtifactsAndGeometricLog() throws IOException {
        Files.writeString(temporary.resolve("input.json"), """
                {"minimum_id":"MIN01","method":"PBE-D3(BJ)/def2-SVP density-fitted gas phase",
                 "charge":0,"multiplicity":1,"constraints":"NONE",
                 "software":{"pyscf":"2.14.0","geometric":"1.1.1","dftd3":"1.5.0"}}
                """);
        Files.writeString(temporary.resolve("result.json"), """
                {"energy_hartree":-1477.9438395697157,"scf_converged":true,"cycles":11}
                """);
        Files.writeString(temporary.resolve("final.xyz"), """
                2
                MIN01 unconstrained final
                S 0.0000000000 0.0000000000 0.0000000000
                H 1.3600000000 0.0000000000 0.0000000000
                """);
        Files.writeString(temporary.resolve("final_gradient_hartree_per_bohr.txt"), """
                1.0e-5 2.0e-5 3.0e-5
                -1.0e-5 -2.0e-5 -3.0e-5
                """);
        Files.writeString(temporary.resolve("raw_combined.log"), """
                -=# geomeTRIC started. Version: 1.1.1 #=-
                Step   10 : Displace = 1.290e-03/3.072e-03 E (change) = -1477.9438395652 (-2.418e-07)
                Converged! =D
                """);

        PyscfGeometricOptimization result = reader.readOptimization(temporary);

        assertThat(result.calculationId().value()).contains("MIN01");
        assertThat(result.finalGeometry().value().orElseThrow().atoms()).hasSize(2);
        assertThat(result.finalEnergyHartree().value()).contains(-1477.9438395697157);
        assertThat(result.protocol().functional().value()).contains("PBE");
        assertThat(result.protocol().dispersion().value()).contains("D3(BJ)");
        assertThat(result.protocol().basisSet().value()).contains("def2-SVP");
        assertThat(result.finalGradientHartreePerBohr().value().orElseThrow()).hasSize(6);
        assertThat(result.geometryConverged().value()).contains(true);
        assertThat(result.softwareVersions().get("pyscf").classification())
                .isEqualTo(RecoveryClassification.RECOVERABLE_FROM_SOFTWARE_ENVIRONMENT_ARTIFACT);
        assertThat(result.comparisons()).singleElement().satisfies(comparison -> {
            assertThat(comparison.absoluteDifference()).isLessThan(5.0e-9);
            assertThat(comparison.historicalSource().locator()).contains("line 2");
        });
        assertThat(result.finalEnergyHartree().provenance()).singleElement().satisfies(source -> {
            assertThat(source.locator()).isEqualTo("/energy_hartree");
            assertThat(source.sha256()).isEqualTo(ArtifactChecksums.sha256(temporary.resolve("result.json")));
        });
    }

    @Test
    void reconstructsAndChecksumValidatesHessian() throws IOException {
        Files.writeString(temporary.resolve("input.json"), """
                {"minimum_id":"MIN01","method":"PBE-D3(BJ)/def2-SVP density-fitted gas phase analytic Hessian",
                 "charge":0,"multiplicity":1,
                 "frequency_projection":"PySCF harmonic_analysis exclude_trans=True exclude_rot=True",
                 "software":{"pyscf":"2.14.0","numpy":"2.5.2"}}
                """);
        Path matrix = temporary.resolve("cartesian_hessian_flat_hartree_per_bohr2.txt");
        Path frequencies = temporary.resolve("frequencies_cm-1.txt");
        Path log = temporary.resolve("raw_combined.log");
        Files.writeString(matrix, "1.0 0.1\n0.1 2.0\n");
        Files.writeString(frequencies, "0.0\n47.06\n");
        Files.writeString(log, "");
        Files.writeString(temporary.resolve("result.json"), """
                {"status":"HESSIAN_COMPLETE","energy_hartree":-1477.9438395697284,
                 "scf_converged":true,"frequency_count":2,
                 "artifact_sha256":{
                   "cartesian_hessian_flat_hartree_per_bohr2.txt":"%s",
                   "frequencies_cm-1.txt":"%s",
                   "raw_combined.log":"%s"}}
                """.formatted(ArtifactChecksums.sha256(matrix), ArtifactChecksums.sha256(frequencies),
                ArtifactChecksums.sha256(log)));

        PyscfHessianResult result = reader.readHessian(temporary);

        assertThat(result.cartesianDimension()).isEqualTo(2);
        assertThat(result.cartesianHessian().value().orElseThrow()).containsExactly(1.0, 0.1, 0.1, 2.0);
        assertThat(result.frequencies().value().orElseThrow()).containsExactly(0.0, 47.06);
        assertThat(result.hessianUnit()).contains("hartree/bohr^2").contains("unmass-weighted");
        assertThat(result.artifactChecksumsVerified()).isTrue();
    }

    @Test
    void rejectsRaggedHessianInsteadOfInventingShape() throws IOException {
        writeMinimalHessianInputAndResult(2);
        Files.writeString(temporary.resolve("cartesian_hessian_flat_hartree_per_bohr2.txt"), "1 0\n0 1 2\n");
        Files.writeString(temporary.resolve("frequencies_cm-1.txt"), "0\n1\n");

        assertThatThrownBy(() -> reader.readHessian(temporary))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ragged numeric matrix");
    }

    private void writeMinimalHessianInputAndResult(int count) throws IOException {
        Files.writeString(temporary.resolve("input.json"), """
                {"minimum_id":"MIN01","method":"PBE/def2-SVP","charge":0,"multiplicity":1,
                 "frequency_projection":"PySCF harmonic_analysis","software":{"pyscf":"2.14.0"}}
                """);
        Files.writeString(temporary.resolve("result.json"), """
                {"status":"HESSIAN_COMPLETE","energy_hartree":-1.0,"scf_converged":true,
                 "frequency_count":%d,"artifact_sha256":{}}
                """.formatted(count));
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("analysis/mettl7-phase2"))) {
            current = current.getParent();
        }
        return current == null ? Path.of(System.getProperty("user.dir")) : current;
    }
}
