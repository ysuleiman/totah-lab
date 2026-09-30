package totah.lab.athena.energy.openmm;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete, immutable provenance for one pre-built OpenMM system.
 *
 * <p>This is deliberately a loader contract, not another preparation
 * pipeline.  The authoritative preparation workflow produces the artifacts
 * and this manifest; Athena verifies and consumes them without rebuilding the
 * Hamiltonian.</p>
 */
public record OpenMmSystemManifest(
        String schemaVersion,
        String systemBuilderIdentity,
        Path systemArtifact,
        SystemFormat systemFormat,
        Path coordinateArtifact,
        String proteinForceField,
        String solventModel,
        String ligandParameterization,
        String samParameterization,
        Map<String, String> protonationStates,
        String boxAndSolventConstruction,
        String ions,
        String nonbondedMethod,
        String cutoff,
        String switching,
        String pmeEwaldSettings,
        String constraints,
        String rigidWaterPolicy,
        String hydrogenMassRepartitioning,
        Map<Integer, String> forceGroupAssignments,
        int relaxationRestraintForceGroup,
        String systemAtomMappingSha256,
        List<String> restraints,
        Map<String, Path> scientificArtifacts,
        Map<String, String> sha256) {

    public OpenMmSystemManifest {
        schemaVersion = text(schemaVersion, "schemaVersion");
        systemBuilderIdentity = text(systemBuilderIdentity, "systemBuilderIdentity");
        Objects.requireNonNull(systemArtifact, "systemArtifact");
        Objects.requireNonNull(systemFormat, "systemFormat");
        Objects.requireNonNull(coordinateArtifact, "coordinateArtifact");
        proteinForceField = text(proteinForceField, "proteinForceField");
        solventModel = text(solventModel, "solventModel");
        ligandParameterization = text(ligandParameterization, "ligandParameterization");
        samParameterization = text(samParameterization, "samParameterization");
        protonationStates = nonempty(protonationStates, "protonationStates");
        boxAndSolventConstruction = text(boxAndSolventConstruction, "boxAndSolventConstruction");
        ions = text(ions, "ions");
        nonbondedMethod = text(nonbondedMethod, "nonbondedMethod");
        cutoff = text(cutoff, "cutoff");
        switching = text(switching, "switching");
        pmeEwaldSettings = text(pmeEwaldSettings, "pmeEwaldSettings");
        constraints = text(constraints, "constraints");
        rigidWaterPolicy = text(rigidWaterPolicy, "rigidWaterPolicy");
        hydrogenMassRepartitioning = text(hydrogenMassRepartitioning, "hydrogenMassRepartitioning");
        forceGroupAssignments = Map.copyOf(Objects.requireNonNull(forceGroupAssignments,
                "forceGroupAssignments"));
        if (forceGroupAssignments.isEmpty()) {
            throw new IllegalArgumentException("forceGroupAssignments must not be empty");
        }
        forceGroupAssignments.forEach((group, forceClass) -> {
            if (group == null || group < 0 || group > 31) {
                throw new IllegalArgumentException("force group must be in 0..31");
            }
            text(forceClass, "force class");
        });
        if (relaxationRestraintForceGroup < 0 || relaxationRestraintForceGroup > 31) {
            throw new IllegalArgumentException("relaxationRestraintForceGroup must be in 0..31");
        }
        systemAtomMappingSha256 = text(systemAtomMappingSha256,
                "systemAtomMappingSha256");
        if (!systemAtomMappingSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("systemAtomMappingSha256 must be lowercase SHA-256");
        }
        restraints = List.copyOf(Objects.requireNonNull(restraints, "restraints"));
        if (restraints.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("restraints must contain explicit descriptions");
        }
        scientificArtifacts = nonempty(scientificArtifacts, "scientificArtifacts");
        sha256 = nonempty(sha256, "sha256");
        sha256.forEach((name, hash) -> {
            if (!hash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid SHA-256 for " + name);
            }
        });
    }

    public enum SystemFormat { AMBER_PRMTOP, OPENMM_XML }

    private static String text(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    private static <K, V> Map<K, V> nonempty(Map<K, V> value, String field) {
        Map<K, V> copied = Map.copyOf(Objects.requireNonNull(value, field));
        if (copied.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        return copied;
    }
}
