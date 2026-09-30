package totah.lab.athena.energy.openmm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.energy.EnergyEvaluationException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMmSystemManifestLoaderTest {
    @TempDir Path directory;

    @Test
    void resolvesRelativeArtifactsAndVerifiesEveryHash() throws Exception {
        Path system = Files.writeString(directory.resolve("system.xml"), "system");
        Path coordinates = Files.writeString(directory.resolve("coordinates.json"), "coordinates");
        Path receptor = Files.writeString(directory.resolve("receptor.pdb"), "receptor");
        Path manifest = writeManifest(system, coordinates, receptor,
                OpenMmSystemManifestLoader.sha256(system));
        VerifiedOpenMmSystem verified = new OpenMmSystemManifestLoader().load(manifest);
        assertThat(verified.manifest().systemArtifact()).isEqualTo(system.toAbsolutePath());
        assertThat(verified.verifiedHashes()).containsKeys("system", "coordinates", "receptor.sha256");
        assertThat(verified.manifestSha256()).hasSize(64);
    }

    @Test
    void rejectsTamperedArtifactAndIncompleteProvenance() throws Exception {
        Path system = Files.writeString(directory.resolve("system.xml"), "system");
        Path coordinates = Files.writeString(directory.resolve("coordinates.json"), "coordinates");
        Path receptor = Files.writeString(directory.resolve("receptor.pdb"), "receptor");
        Path manifest = writeManifest(system, coordinates, receptor, "0".repeat(64));
        assertThatThrownBy(() -> new OpenMmSystemManifestLoader().load(manifest))
                .isInstanceOf(EnergyEvaluationException.class)
                .hasMessageContaining("SHA-256 mismatch");
    }

    private Path writeManifest(Path system, Path coordinates, Path receptor, String systemHash)
            throws Exception {
        String json = """
                {
                  "schemaVersion":"athena-openmm-system-v1",
                  "systemBuilderIdentity":"controlled-fixture-builder-v1",
                  "systemArtifact":"system.xml",
                  "systemFormat":"OPENMM_XML",
                  "coordinateArtifact":"coordinates.json",
                  "proteinForceField":"none-controlled-fixture",
                  "solventModel":"vacuum",
                  "ligandParameterization":"explicit-controlled-particles",
                  "samParameterization":"not-present",
                  "protonationStates":{"fixture":"explicit"},
                  "boxAndSolventConstruction":"nonperiodic-no-box",
                  "ions":"none",
                  "nonbondedMethod":"NoCutoff",
                  "cutoff":"not-applicable",
                  "switching":"disabled",
                  "pmeEwaldSettings":"not-applicable",
                  "constraints":"none",
                  "rigidWaterPolicy":"not-applicable",
                  "hydrogenMassRepartitioning":"none",
                  "forceGroupAssignments":{"1":"NonbondedForce"},
                  "relaxationRestraintForceGroup":5,
                  "systemAtomMappingSha256":"%s",
                  "restraints":[],
                  "scientificArtifacts":{"receptor.sha256":"receptor.pdb"},
                  "sha256":{
                    "system":"%s",
                    "coordinates":"%s",
                    "receptor.sha256":"%s"
                  }
                }
                """.formatted("0".repeat(64), systemHash, OpenMmSystemManifestLoader.sha256(coordinates),
                OpenMmSystemManifestLoader.sha256(receptor));
        return Files.writeString(directory.resolve("manifest.json"), json);
    }
}
