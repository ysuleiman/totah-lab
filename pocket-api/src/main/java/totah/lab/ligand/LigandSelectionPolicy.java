package totah.lab.ligand;

import totah.lab.pipeline.cleanup.ClassifiedResidue;
import totah.lab.pipeline.cleanup.ResidueRole;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Applies docking-ligand selection policy after identity classification.
 */
public final class LigandSelectionPolicy {

    private static final Set<String> DEFAULT_EXCLUDED_COMPONENTS =
            Set.of("GOL", "SO4");

    private final Set<String> excludedComponents;

    public LigandSelectionPolicy() {
        this(DEFAULT_EXCLUDED_COMPONENTS);
    }

    public LigandSelectionPolicy(Set<String> excludedComponents) {
        Objects.requireNonNull(excludedComponents, "excludedComponents is null");
        this.excludedComponents = excludedComponents.stream()
                .map(LigandSelectionPolicy::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public LigandSelectionDecision evaluate(ClassifiedResidue classified) {
        Objects.requireNonNull(classified, "classified is null");
        if (classified.role() != ResidueRole.LIGAND) {
            return LigandSelectionDecision.rejected(
                    LigandSelectionFailure.UNSUPPORTED_CLASSIFICATION,
                    "Component has role " + classified.role());
        }

        String componentId = normalize(classified.residue().getName());
        if (excludedComponents.contains(componentId)) {
            return LigandSelectionDecision.rejected(
                    LigandSelectionFailure.EXCLUDED_BY_POLICY,
                    componentId + " is excluded from ordinary docking-ligand selection");
        }

        return LigandSelectionDecision.eligible(
                componentId + " is an eligible extracted free-ligand candidate");
    }

    public Set<String> excludedComponents() {
        return excludedComponents;
    }

    private static String normalize(String componentId) {
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("componentId is blank");
        }
        return componentId.trim().toUpperCase(Locale.ROOT);
    }
}
