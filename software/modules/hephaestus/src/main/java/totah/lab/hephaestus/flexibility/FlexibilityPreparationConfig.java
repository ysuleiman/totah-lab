package totah.lab.hephaestus.flexibility;

import totah.lab.gaia.structure.ResidueId;

import java.util.Set;

public record FlexibilityPreparationConfig(
        Set<ResidueId> flexibleResidues,
        boolean includeBackbone,
        boolean allowTerminalResidues,
        boolean allowModifiedResidues) {
    public FlexibilityPreparationConfig {
        flexibleResidues = flexibleResidues == null ? Set.of() : Set.copyOf(flexibleResidues);
    }
    public static FlexibilityPreparationConfig none() {
        return new FlexibilityPreparationConfig(Set.of(), false, false, false);
    }
}
