package totah.lab.athena.energy.openmm;

import totah.lab.athena.energy.EnergyComponent;
import totah.lab.athena.energy.EnergyEvaluation;
import totah.lab.athena.energy.EnergyEvaluationException;
import totah.lab.athena.energy.MolecularEnergyEvaluator;
import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.energy.ScientificMethod;
import totah.lab.athena.thermo.EnergyUnit;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Validating adapter over a separately validated OpenMM backend. */
public final class OpenMmEnergyEvaluator implements MolecularEnergyEvaluator {
    private final OpenMmBackend backend;
    private final OpenMmForceGroupMap forceGroups;

    public OpenMmEnergyEvaluator(OpenMmBackend backend,
            OpenMmForceGroupMap forceGroups) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.forceGroups = Objects.requireNonNull(forceGroups, "forceGroups");
    }

    @Override public String evaluatorId() { return "openmm-component-evaluator-v1"; }

    @Override
    public EnergyEvaluation evaluate(MolecularState state)
            throws EnergyEvaluationException {
        OpenMmBackend.BackendResult result = backend.evaluate(state, forceGroups);
        validate(state, result, forceGroups);
        EnumMap<EnergyComponent, Double> components = new EnumMap<>(EnergyComponent.class);
        components.put(EnergyComponent.TOTAL_POTENTIAL, result.totalKilojoulesPerMole());
        result.forceGroupKilojoulesPerMole().forEach((group, value) -> {
            OpenMmForceGroupMap.ForceGroup mapping = forceGroups.groups().get(group);
            if (mapping == null) throw new IllegalArgumentException("unmapped force group " + group);
            if (components.putIfAbsent(mapping.component(), value) != null) {
                components.merge(mapping.component(), value, Double::sum);
            }
        });
        Map<String, String> provenance = new LinkedHashMap<>(result.artifactHashes());
        provenance.put("openmm.version", result.openMmVersion());
        provenance.put("openmm.platform", result.platform());
        provenance.put("system.builder", result.systemBuilderIdentity());
        provenance.put("restraint.protocol", state.restraintDefinition());
        provenance.put("minimized", Boolean.toString(result.minimized()));
        provenance.put("converged", Boolean.toString(result.converged()));
        provenance.put("source.configuration", state.stateId());
        result.platformProperties().forEach((key, value) ->
                provenance.put("platform." + key, value));
        return new EnergyEvaluation(state.stateId(), components,
                EnergyUnit.KILOJOULES_PER_MOLE, evaluatorId(),
                state.forceFieldIdentity(), state.parameterProvenance(),
                ScientificMethod.STATE_ENERGY, provenance);
    }

    private static void validate(MolecularState state,
            OpenMmBackend.BackendResult result, OpenMmForceGroupMap forceGroups)
            throws EnergyEvaluationException {
        if (!Double.isFinite(result.totalKilojoulesPerMole())
                || result.forceGroupKilojoulesPerMole().values().stream()
                .anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new EnergyEvaluationException(
                    EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "OpenMM returned non-finite energy");
        }
        if (!result.forceGroupKilojoulesPerMole().keySet()
                .equals(forceGroups.groups().keySet())) {
            throw new EnergyEvaluationException(
                    EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "OpenMM force-group result does not exactly match declared groups");
        }
        if (!"CPU".equals(result.platform())
                || !"8".equals(result.platformProperties().get("Threads"))
                || !"true".equalsIgnoreCase(result.platformProperties()
                .get("DeterministicForces"))) {
            throw new EnergyEvaluationException(
                    EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "OpenMM deterministic CPU settings are not CPU/8/true");
        }
        if (result.openMmVersion() == null || result.openMmVersion().isBlank()
                || result.platform() == null || result.platform().isBlank()
                || result.systemBuilderIdentity() == null
                || result.systemBuilderIdentity().isBlank()) {
            throw new EnergyEvaluationException(
                    EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "OpenMM provenance is incomplete");
        }
        for (String hash : java.util.List.of("receptor.sha256", "ligand.sha256",
                "parameters.sha256")) {
            if (!result.artifactHashes().containsKey(hash)) {
                throw new EnergyEvaluationException(
                        EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                        "missing OpenMM artifact hash " + hash);
            }
        }
        if (state.cofactor().isPresent()
                && !result.artifactHashes().containsKey("sam.sha256")) {
            throw new EnergyEvaluationException(
                    EnergyEvaluationException.FailureCode.STATE_INCOMPLETE,
                    "missing OpenMM artifact hash sam.sha256");
        }
    }
}
