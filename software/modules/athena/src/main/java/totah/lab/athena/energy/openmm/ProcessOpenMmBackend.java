package totah.lab.athena.energy.openmm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.energy.EnergyEvaluationException;
import totah.lab.athena.energy.MolecularState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Audited process boundary to the OpenMM Python API; OpenMM owns all physics. */
public final class ProcessOpenMmBackend implements OpenMmBackend {
    public static final String MANIFEST_PROVENANCE_KEY = "openmm.system.manifest";
    public static final String COORDINATE_HASH_PROVENANCE_KEY = "openmm.coordinates.sha256";

    private final OpenMmSystemManifestLoader manifestLoader;
    private final ObjectMapper mapper;
    private final OpenMmProcessExecutor processExecutor;

    public ProcessOpenMmBackend(Path pythonExecutable, Path runnerScript,
            Path receiptDirectory) {
        this(pythonExecutable, runnerScript, receiptDirectory,
                new OpenMmSystemManifestLoader());
    }

    ProcessOpenMmBackend(Path pythonExecutable, Path runnerScript,
            Path receiptDirectory, OpenMmSystemManifestLoader manifestLoader) {
        this.manifestLoader = Objects.requireNonNull(manifestLoader, "manifestLoader");
        this.processExecutor = new OpenMmProcessExecutor(pythonExecutable,
                runnerScript, receiptDirectory);
        this.mapper = processExecutor.mapper();
    }

    @Override
    public BackendResult evaluate(MolecularState state, OpenMmForceGroupMap forceGroups)
            throws EnergyEvaluationException {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(forceGroups, "forceGroups");
        String manifestValue = state.provenance().get(MANIFEST_PROVENANCE_KEY);
        if (manifestValue == null || manifestValue.isBlank()) {
            throw failure(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "state lacks " + MANIFEST_PROVENANCE_KEY, null);
        }
        try {
            VerifiedOpenMmSystem verified = manifestLoader.load(Path.of(manifestValue));
            if (verified.manifest().systemFormat() != OpenMmSystemManifest.SystemFormat.OPENMM_XML) {
                throw failure(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                        "a frozen OPENMM_XML Hamiltonian is required; AMBER_PRMTOP requires "
                                + "createSystem choices that are not contained in the prmtop", null);
            }
            String stateCoordinateHash = state.provenance().get(COORDINATE_HASH_PROVENANCE_KEY);
            String frozenCoordinateHash = verified.verifiedHashes().get("coordinates");
            if (!Objects.equals(stateCoordinateHash, frozenCoordinateHash)) {
                throw failure(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                        "state coordinate identity does not match frozen coordinate artifact", null);
            }
            validateGroupContract(verified.manifest(), forceGroups);
            OpenMmProcessExecutor.Execution execution = processExecutor.execute(
                    request(verified, forceGroups));
            JsonNode result = execution.response();
            OpenMmResponseValidator.validateEnergy(result, forceGroups.groups().keySet(),
                    "CPU", Map.of("Threads", "8", "DeterministicForces", "true"));
            Map<Integer, Double> energies = new LinkedHashMap<>();
            result.required("forceGroupKilojoulesPerMole").fields().forEachRemaining(entry ->
                    energies.put(Integer.parseInt(entry.getKey()), entry.getValue().asDouble()));
            Map<String, String> properties = new LinkedHashMap<>();
            result.required("platformProperties").fields().forEachRemaining(entry ->
                    properties.put(entry.getKey(), entry.getValue().asText()));
            Map<String, String> hashes = new LinkedHashMap<>(verified.verifiedHashes());
            hashes.put("system.sha256", verified.verifiedHashes().get("system"));
            hashes.put("coordinates.sha256", verified.verifiedHashes().get("coordinates"));
            hashes.put("manifest.sha256", verified.manifestSha256());
            hashes.putAll(execution.receiptProvenance());
            return new BackendResult(energies,
                    result.required("totalKilojoulesPerMole").asDouble(),
                    result.required("openMmVersion").asText(),
                    result.required("platform").asText(), properties,
                    verified.manifest().systemBuilderIdentity(), hashes,
                    result.path("minimized").asBoolean(false),
                    result.path("converged").asBoolean(true));
        } catch (EnergyEvaluationException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(EnergyEvaluationException.FailureCode.ENGINE_UNAVAILABLE,
                    "OpenMM I/O failure: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw failure(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "invalid OpenMM response: " + exception.getMessage(), exception);
        }
    }

    private ObjectNode request(VerifiedOpenMmSystem verified,
            OpenMmForceGroupMap forceGroups) {
        OpenMmSystemManifest manifest = verified.manifest();
        ObjectNode root = mapper.createObjectNode();
        root.put("systemArtifact", manifest.systemArtifact().toString());
        root.put("systemFormat", manifest.systemFormat().name());
        root.put("coordinateArtifact", manifest.coordinateArtifact().toString());
        root.put("platform", "CPU");
        ObjectNode properties = root.putObject("platformProperties");
        properties.put("Threads", "8");
        properties.put("DeterministicForces", "true");
        ObjectNode groups = root.putObject("forceGroups");
        forceGroups.groups().forEach((id, value) ->
                groups.put(Integer.toString(id), value.openMmForceClass()));
        return root;
    }

    private static void validateGroupContract(OpenMmSystemManifest manifest,
            OpenMmForceGroupMap requested) throws EnergyEvaluationException {
        Map<Integer, String> requestedClasses = new LinkedHashMap<>();
        requested.groups().forEach((group, definition) ->
                requestedClasses.put(group, definition.openMmForceClass()));
        if (!manifest.forceGroupAssignments().equals(requestedClasses)) {
            throw failure(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "requested force groups differ from frozen system manifest", null);
        }
    }

    private static EnergyEvaluationException failure(
            EnergyEvaluationException.FailureCode code, String message, Exception cause) {
        EnergyEvaluationException exception = new EnergyEvaluationException(code, message);
        if (cause != null) exception.initCause(cause);
        return exception;
    }
}
