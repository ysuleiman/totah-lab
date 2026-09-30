package totah.lab.mettl7.landscape;

import totah.lab.athena.energy.EnergyComponent;

import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

/** METTL7-owned declaration of which A/B terms may be differenced and why. */
public record Mettl7EnergyComparability(
        Set<EnergyComponent> comparableComponents,
        String cancellationAssumptions,
        String samStateIdentity,
        String forceFieldIdentity,
        String ligandParameterHash,
        String solventModel,
        String nonbondedMethod,
        String cutoffAndSwitchingRules,
        String temperatureDefinition,
        String restraintProtocolHash,
        String minimizationProtocolHash,
        String commonFrameProvenance,
        String perturbationDescriptorHash,
        String preparationProvenance) {
    public Mettl7EnergyComparability {
        comparableComponents = Set.copyOf(java.util.Objects.requireNonNull(
                comparableComponents, "comparableComponents"));
        if (comparableComponents.isEmpty()
                || comparableComponents.contains(EnergyComponent.TOTAL_POTENTIAL)) {
            throw new IllegalArgumentException(
                    "declare comparable components; raw whole-system total is forbidden");
        }
        cancellationAssumptions = require(cancellationAssumptions,
                "cancellationAssumptions");
        samStateIdentity = require(samStateIdentity, "samStateIdentity");
        forceFieldIdentity = require(forceFieldIdentity, "forceFieldIdentity");
        ligandParameterHash = require(ligandParameterHash, "ligandParameterHash");
        solventModel = require(solventModel, "solventModel");
        nonbondedMethod = require(nonbondedMethod, "nonbondedMethod");
        cutoffAndSwitchingRules = require(cutoffAndSwitchingRules,
                "cutoffAndSwitchingRules");
        temperatureDefinition = require(temperatureDefinition, "temperatureDefinition");
        restraintProtocolHash = require(restraintProtocolHash, "restraintProtocolHash");
        minimizationProtocolHash = require(minimizationProtocolHash,
                "minimizationProtocolHash");
        commonFrameProvenance = require(commonFrameProvenance,
                "commonFrameProvenance");
        perturbationDescriptorHash = require(perturbationDescriptorHash,
                "perturbationDescriptorHash");
        preparationProvenance = require(preparationProvenance,
                "preparationProvenance");
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** Receipt fields that must match both evaluated A and B states exactly. */
    public Map<String, String> requiredEvaluationProvenance() {
        Map<String, String> required = new LinkedHashMap<>();
        required.put("ligand.parameters.sha256", ligandParameterHash);
        required.put("sam.state", samStateIdentity);
        required.put("solvent.model", solventModel);
        required.put("nonbonded.method", nonbondedMethod);
        required.put("cutoff.switching", cutoffAndSwitchingRules);
        required.put("temperature", temperatureDefinition);
        required.put("restraint.protocol.sha256", restraintProtocolHash);
        required.put("minimization.protocol.sha256", minimizationProtocolHash);
        required.put("common.frame", commonFrameProvenance);
        required.put("perturbation.sha256", perturbationDescriptorHash);
        return Map.copyOf(required);
    }
}
