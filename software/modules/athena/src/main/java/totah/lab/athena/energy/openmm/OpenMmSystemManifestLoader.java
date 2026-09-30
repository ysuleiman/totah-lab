package totah.lab.athena.energy.openmm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import totah.lab.athena.energy.EnergyEvaluationException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads, resolves and cryptographically verifies an authoritative manifest. */
public final class OpenMmSystemManifestLoader {
    private final ObjectMapper mapper;

    public OpenMmSystemManifestLoader() {
        this(mapperWithPathSupport());
    }

    private static ObjectMapper mapperWithPathSupport() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Path.class, new com.fasterxml.jackson.databind.JsonDeserializer<>() {
            @Override
            public Path deserialize(com.fasterxml.jackson.core.JsonParser parser,
                    com.fasterxml.jackson.databind.DeserializationContext context) throws IOException {
                return Path.of(parser.getValueAsString());
            }
        });
        return new ObjectMapper().registerModule(module);
    }

    OpenMmSystemManifestLoader(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public VerifiedOpenMmSystem load(Path manifestPath)
            throws IOException, EnergyEvaluationException {
        Path absolute = manifestPath.toAbsolutePath().normalize();
        OpenMmSystemManifest parsed = mapper.readValue(absolute.toFile(), OpenMmSystemManifest.class);
        Path parent = absolute.getParent();
        OpenMmSystemManifest resolved = resolve(parsed, parent);
        Map<String, String> verified = new LinkedHashMap<>();
        verify("system", resolved.systemArtifact(), resolved.sha256(), verified);
        verify("coordinates", resolved.coordinateArtifact(), resolved.sha256(), verified);
        for (Map.Entry<String, Path> entry : resolved.scientificArtifacts().entrySet()) {
            verify(entry.getKey(), entry.getValue(), resolved.sha256(), verified);
        }
        return new VerifiedOpenMmSystem(resolved, absolute, sha256(absolute), verified);
    }

    private static OpenMmSystemManifest resolve(OpenMmSystemManifest m, Path parent) {
        Map<String, Path> artifacts = new LinkedHashMap<>();
        m.scientificArtifacts().forEach((key, path) -> artifacts.put(key, resolve(parent, path)));
        return new OpenMmSystemManifest(m.schemaVersion(), m.systemBuilderIdentity(),
                resolve(parent, m.systemArtifact()), m.systemFormat(),
                resolve(parent, m.coordinateArtifact()), m.proteinForceField(), m.solventModel(),
                m.ligandParameterization(), m.samParameterization(), m.protonationStates(),
                m.boxAndSolventConstruction(), m.ions(), m.nonbondedMethod(), m.cutoff(),
                m.switching(), m.pmeEwaldSettings(), m.constraints(), m.rigidWaterPolicy(),
                m.hydrogenMassRepartitioning(), m.forceGroupAssignments(),
                m.relaxationRestraintForceGroup(), m.systemAtomMappingSha256(), m.restraints(),
                artifacts, m.sha256());
    }

    private static Path resolve(Path parent, Path path) {
        return (path.isAbsolute() ? path : parent.resolve(path)).toAbsolutePath().normalize();
    }

    private static void verify(String key, Path path, Map<String, String> declared,
            Map<String, String> verified) throws IOException, EnergyEvaluationException {
        String expected = declared.get(key);
        if (expected == null) {
            throw new EnergyEvaluationException(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "missing declared hash for artifact " + key);
        }
        if (!Files.isRegularFile(path)) {
            throw new EnergyEvaluationException(EnergyEvaluationException.FailureCode.PARAMETERIZATION_UNAVAILABLE,
                    "missing OpenMM artifact " + path);
        }
        String actual = sha256(path);
        if (!actual.equals(expected)) {
            throw new EnergyEvaluationException(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "SHA-256 mismatch for " + key + ": expected " + expected + ", actual " + actual);
        }
        verified.put(key, actual);
    }

    static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
