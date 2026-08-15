package totah.lab.prometheus.strategy;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Complete scientific declaration of a parameterization methodology. */
public record ScientificStrategyDescriptor(
        String strategyId,
        String displayName,
        String methodology,
        String productionFunctionalForm,
        List<ScientificRequirementDescriptor> requirements,
        List<ExternalDependencyDescriptor> externalDependencies,
        Set<ParameterizationCapability> outputs,
        EngineCompatibility openMmCompatibility,
        String openMmConstraints,
        EngineCompatibility amberCompatibility,
        String amberConstraints,
        List<String> knownLimitations) {

    public ScientificStrategyDescriptor {
        strategyId = requireNonBlank(strategyId, "strategyId");
        displayName = requireNonBlank(displayName, "displayName");
        methodology = requireNonBlank(methodology, "methodology");
        productionFunctionalForm = requireNonBlank(productionFunctionalForm, "productionFunctionalForm");
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        externalDependencies = List.copyOf(Objects.requireNonNull(externalDependencies, "externalDependencies"));
        outputs = Set.copyOf(Objects.requireNonNull(outputs, "outputs"));
        Objects.requireNonNull(openMmCompatibility, "openMmCompatibility");
        openMmConstraints = requireNonBlank(openMmConstraints, "openMmConstraints");
        Objects.requireNonNull(amberCompatibility, "amberCompatibility");
        amberConstraints = requireNonBlank(amberConstraints, "amberConstraints");
        knownLimitations = List.copyOf(Objects.requireNonNull(knownLimitations, "knownLimitations"));
        if (requirements.isEmpty() || outputs.isEmpty() || knownLimitations.isEmpty()) {
            throw new IllegalArgumentException("requirements, outputs, and knownLimitations must not be empty");
        }
    }

    public boolean produces(ParameterizationCapability capability) {
        return outputs.contains(Objects.requireNonNull(capability, "capability"));
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
