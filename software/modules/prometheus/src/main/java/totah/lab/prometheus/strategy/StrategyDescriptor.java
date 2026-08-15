package totah.lab.prometheus.strategy;

import java.util.Objects;
import java.util.Set;

/** Molecule-independent identity and declared scope of a parameterization method. */
public record StrategyDescriptor(
        String strategyId,
        String displayName,
        String methodFamily,
        Set<ParameterizationCapability> capabilities,
        String integrationVersion) {

    public StrategyDescriptor {
        strategyId = requireNonBlank(strategyId, "strategyId");
        displayName = requireNonBlank(displayName, "displayName");
        methodFamily = requireNonBlank(methodFamily, "methodFamily");
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
        integrationVersion = requireNonBlank(integrationVersion, "integrationVersion");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be non-blank");
        }
        return value;
    }
}
