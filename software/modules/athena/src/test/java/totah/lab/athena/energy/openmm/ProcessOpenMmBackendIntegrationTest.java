package totah.lab.athena.energy.openmm;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.MolecularState;
import totah.lab.gaia.structure.Structure;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ProcessOpenMmBackendIntegrationTest {
    @TempDir Path directory;

    @Test
    void realOpenMmProcessReturnsAllPhysicalForceGroupsAndReceipt() throws Exception {
        String executable = System.getProperty("athena.openmm.python");
        Assumptions.assumeTrue(executable != null && Files.isExecutable(Path.of(executable)),
                "set -Dathena.openmm.python to run real OpenMM integration");
        generate(executable);
        for (String name : List.of("receptor", "ligand", "parameters")) {
            Files.writeString(directory.resolve(name + ".txt"), name);
        }
        Path manifest = writeManifest();
        var backend = new ProcessOpenMmBackend(Path.of(executable),
                Path.of("src/main/resources/openmm/athena_openmm_runner.py"),
                directory.resolve("receipts"));
        var result = backend.evaluate(state(manifest,
                OpenMmSystemManifestLoader.sha256(directory.resolve("coordinates.json"))), groups());
        var repeated = backend.evaluate(state(manifest,
                OpenMmSystemManifestLoader.sha256(directory.resolve("coordinates.json"))), groups());
        assertThat(result.openMmVersion()).isEqualTo("8.5.2");
        assertThat(result.platform()).isEqualTo("CPU");
        assertThat(result.platformProperties()).containsEntry("Threads", "8")
                .containsEntry("DeterministicForces", "true");
        assertThat(result.forceGroupKilojoulesPerMole()).containsOnlyKeys(1, 2, 3, 4, 5);
        assertThat(result.artifactHashes()).containsKeys("manifest.sha256",
                "execution.request.sha256", "execution.receipt.sha256", "execution.receipt.path");
        assertThat(result.forceGroupKilojoulesPerMole().values()).allMatch(Double::isFinite);
        assertThat(repeated.totalKilojoulesPerMole()).isCloseTo(result.totalKilojoulesPerMole(),
                within(OpenMmValidationTolerances.DETERMINISTIC_ENERGY_KJ_PER_MOL));
        double groupSum = result.forceGroupKilojoulesPerMole().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        assertThat(groupSum).isCloseTo(result.totalKilojoulesPerMole(),
                within(OpenMmValidationTolerances.FORCE_GROUP_SUM_KJ_PER_MOL));
        var reference = new ObjectMapper().readTree(directory.resolve("independent-reference.json").toFile());
        assertThat(result.totalKilojoulesPerMole())
                .isCloseTo(reference.required("totalKilojoulesPerMole").asDouble(),
                        within(OpenMmValidationTolerances.DIRECT_REFERENCE_ENERGY_KJ_PER_MOL));
    }

    @Test
    void rejectsXmlWhoseFrozenForceGroupsDoNotMatchManifest() throws Exception {
        String executable = System.getProperty("athena.openmm.python");
        Assumptions.assumeTrue(executable != null && Files.isExecutable(Path.of(executable)));
        generate(executable);
        String xml = Files.readString(directory.resolve("system.xml"));
        Files.writeString(directory.resolve("system.xml"),
                xml.replaceFirst("forceGroup=\"1\"", "forceGroup=\"0\""));
        for (String name : List.of("receptor", "ligand", "parameters"))
            Files.writeString(directory.resolve(name + ".txt"), name);
        Path manifest = writeManifest();
        var backend = new ProcessOpenMmBackend(Path.of(executable),
                Path.of("src/main/resources/openmm/athena_openmm_runner.py"),
                directory.resolve("receipts"));
        assertThatThrownBy(() -> backend.evaluate(state(manifest,
                OpenMmSystemManifestLoader.sha256(directory.resolve("coordinates.json"))), groups()))
                .isInstanceOf(totah.lab.athena.energy.EnergyEvaluationException.class)
                .hasMessageContaining("exact declared group matches");
    }

    private void generate(String executable) throws Exception {
        Path builder = resource("/openmm/build_controlled_fixture.py");
        Process generation = new ProcessBuilder(executable, builder.toString(), directory.toString())
                .redirectErrorStream(true).start();
        String generationOutput = new String(generation.getInputStream().readAllBytes());
        assertThat(generation.waitFor()).as(generationOutput).isZero();
    }

    private Path writeManifest() throws Exception {
        StringBuilder hashes = new StringBuilder();
        for (String name : List.of("system", "coordinates", "receptor", "ligand", "parameters")) {
            Path file = directory.resolve(name + (name.equals("system") ? ".xml"
                    : name.equals("coordinates") ? ".json" : ".txt"));
            if (!hashes.isEmpty()) hashes.append(",");
            String key = switch (name) {
                case "receptor", "ligand", "parameters" -> name + ".sha256";
                default -> name;
            };
            hashes.append("\"").append(key).append("\":\"")
                    .append(OpenMmSystemManifestLoader.sha256(file)).append("\"");
        }
        String json = """
                {"schemaVersion":"athena-openmm-system-v1",
                "systemBuilderIdentity":"controlled-openmm-8.5.2-v1",
                "systemArtifact":"system.xml","systemFormat":"OPENMM_XML",
                "coordinateArtifact":"coordinates.json","proteinForceField":"controlled",
                "solventModel":"vacuum","ligandParameterization":"controlled",
                "samParameterization":"not-present","protonationStates":{"all":"explicit"},
                "boxAndSolventConstruction":"nonperiodic","ions":"none",
                "nonbondedMethod":"NoCutoff","cutoff":"not-applicable","switching":"disabled",
                "pmeEwaldSettings":"not-applicable","constraints":"none",
                "rigidWaterPolicy":"not-applicable","hydrogenMassRepartitioning":"none",
                "forceGroupAssignments":{"1":"NonbondedForce","2":"HarmonicBondForce",
                "3":"HarmonicAngleForce","4":"PeriodicTorsionForce","5":"CustomExternalForce"},
                "relaxationRestraintForceGroup":5,
                "systemAtomMappingSha256":"%s",
                "restraints":["particle-3 harmonic positional fixture"],
                "scientificArtifacts":{"receptor.sha256":"receptor.txt",
                "ligand.sha256":"ligand.txt","parameters.sha256":"parameters.txt"},
                "sha256":{%s}}
                """.formatted("0".repeat(64), hashes);
        return Files.writeString(directory.resolve("manifest.json"), json);
    }

    private static MolecularState state(Path manifest, String coordinateHash) {
        Structure empty = new Structure(List.of());
        return new MolecularState("fixture", "R", "L", empty, empty, Optional.empty(),
                "explicit", "controlled", "controlled", "vacuum", "fixture-restraint",
                Map.of(ProcessOpenMmBackend.MANIFEST_PROVENANCE_KEY, manifest.toString(),
                        ProcessOpenMmBackend.COORDINATE_HASH_PROVENANCE_KEY, coordinateHash));
    }

    private static OpenMmForceGroupMap groups() {
        return new OpenMmForceGroupMap(Map.of(
                1, new OpenMmForceGroupMap.ForceGroup("NonbondedForce", EnergyComponent.NONBONDED, "unsplit"),
                2, new OpenMmForceGroupMap.ForceGroup("HarmonicBondForce", EnergyComponent.BONDED, "bond"),
                3, new OpenMmForceGroupMap.ForceGroup("HarmonicAngleForce", EnergyComponent.BONDED, "angle"),
                4, new OpenMmForceGroupMap.ForceGroup("PeriodicTorsionForce", EnergyComponent.BONDED, "torsion"),
                5, new OpenMmForceGroupMap.ForceGroup("CustomExternalForce", EnergyComponent.RESTRAINT, "restraint")));
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(ProcessOpenMmBackendIntegrationTest.class.getResource(name).toURI());
    }
}
