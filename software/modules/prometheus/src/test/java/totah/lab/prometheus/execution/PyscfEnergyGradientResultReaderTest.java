package totah.lab.prometheus.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.recovery.ArtifactChecksums;

final class PyscfEnergyGradientResultReaderTest {

    @TempDir Path temporary;

    @Test
    void validatesForceSignAndInputGeometryChecksum() throws Exception {
        Path geometry = temporary.resolve("input_geometry.xyz");
        Files.writeString(geometry, "1\nfixture\nH 0 0 0\n");
        Path result = temporary.resolve("result.json");
        Path spec = temporary.resolve("calculation_specification.json");
        Files.writeString(spec, "{}\n");
        Files.writeString(result, json(ArtifactChecksums.sha256(geometry),
                ArtifactChecksums.sha256(spec), "-0.1"));

        PyscfEnergyGradientResult parsed = new PyscfEnergyGradientResultReader().read(result);

        assertThat(parsed.energyHartree()).isEqualTo(-1.25);
        assertThat(parsed.gradientNormHartreePerBohr()).isEqualTo(0.1);
        assertThat(parsed.forceHartreePerBohr().getFirst().getFirst()).isEqualTo(-0.1);
    }

    @Test
    void rejectsAForceThatIsNotNegativeGradient() throws Exception {
        Path geometry = temporary.resolve("input_geometry.xyz");
        Files.writeString(geometry, "1\nfixture\nH 0 0 0\n");
        Path result = temporary.resolve("result.json");
        Path spec = temporary.resolve("calculation_specification.json");
        Files.writeString(spec, "{}\n");
        Files.writeString(result, json(ArtifactChecksums.sha256(geometry),
                ArtifactChecksums.sha256(spec), "0.1"));

        assertThatThrownBy(() -> new PyscfEnergyGradientResultReader().read(result))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("force != -gradient");
    }

    private static String json(String geometrySha, String specificationSha, String forceX) {
        return """
                {"specification_checksum":"abc","geometry_identity":"geom",
                 "input_geometry_sha256":"%s","calculation_specification_sha256":"%s","energy_hartree":-1.25,
                 "gradient_hartree_per_bohr":[[0.1,0.0,0.0]],
                 "force_hartree_per_bohr":[[%s,0.0,0.0]],
                 "gradient_norm_hartree_per_bohr":0.1,"scf_converged":true,
                 "finite_difference_audit":{"central_difference_hartree_per_bohr":0.1,
                   "analytic_gradient_projection_hartree_per_bohr":0.1,
                   "absolute_difference_hartree_per_bohr":0.0,
                   "plus_energy_hartree":-1.0,"minus_energy_hartree":-1.1},
                 "software":{"pyscf":"2.14.0","dftd3":"1.5.0"}}
                """.formatted(geometrySha, specificationSha, forceX);
    }
}
