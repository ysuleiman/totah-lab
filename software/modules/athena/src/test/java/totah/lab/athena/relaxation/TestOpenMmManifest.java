package totah.lab.athena.relaxation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

final class TestOpenMmManifest {
    private TestOpenMmManifest() {}

    static Path write(Path directory, String mappingSha256) throws Exception {
        return write(directory, mappingSha256, true);
    }

    static Path write(Path directory, String mappingSha256, boolean frozenRestraint) throws Exception {
        String hashes = "\"system\":\"" + hash(directory.resolve("system.xml"))
                + "\",\"coordinates\":\"" + hash(directory.resolve("coordinates.json"))
                + "\",\"receptor.sha256\":\"" + hash(directory.resolve("receptor.txt"))
                + "\",\"ligand.sha256\":\"" + hash(directory.resolve("ligand.txt"))
                + "\",\"parameters.sha256\":\"" + hash(directory.resolve("parameters.txt")) + "\"";
        String json = """
                {"schemaVersion":"athena-openmm-system-v1","systemBuilderIdentity":"fixture-v1",
                "systemArtifact":"system.xml","systemFormat":"OPENMM_XML",
                "coordinateArtifact":"coordinates.json","proteinForceField":"controlled",
                "solventModel":"vacuum","ligandParameterization":"controlled",
                "samParameterization":"controlled","protonationStates":{"all":"explicit"},
                "boxAndSolventConstruction":"nonperiodic","ions":"none","nonbondedMethod":"NoCutoff",
                "cutoff":"not-applicable","switching":"disabled","pmeEwaldSettings":"not-applicable",
                "constraints":"none","rigidWaterPolicy":"not-applicable","hydrogenMassRepartitioning":"none",
                "forceGroupAssignments":%s,
                "relaxationRestraintForceGroup":5,
                "systemAtomMappingSha256":"%s",
                "restraints":["explicit protocol-generated positional restraints"],
                "scientificArtifacts":{"receptor.sha256":"receptor.txt","ligand.sha256":"ligand.txt",
                "parameters.sha256":"parameters.txt"},"sha256":{%s}}
                """.formatted(frozenRestraint
                        ? "{\"1\":\"NonbondedForce\",\"2\":\"HarmonicBondForce\",\"3\":\"HarmonicAngleForce\",\"4\":\"PeriodicTorsionForce\",\"5\":\"CustomExternalForce\"}"
                        : "{\"1\":\"NonbondedForce\",\"2\":\"HarmonicBondForce\",\"3\":\"HarmonicAngleForce\",\"4\":\"PeriodicTorsionForce\"}",
                        mappingSha256, hashes);
        return Files.writeString(directory.resolve("manifest.json"), json);
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
