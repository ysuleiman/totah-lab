package totah.lab.athena.landscape;

import totah.lab.athena.energy.EnergyEvaluation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Evidence-preserving evaluation of one configuration. */
public record LandscapeState(
        LigandConfiguration configuration,
        EnergyEvaluation energy,
        boolean physicallyValid,
        String stericState,
        boolean cofactorCompatible,
        Set<String> interactionFingerprint,
        Set<String> differentialPatches,
        Map<String, String> provenance) {
    public LandscapeState {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(energy, "energy");
        if (!configuration.configurationId().equals(energy.stateId())) {
            throw new IllegalArgumentException("configuration/energy id mismatch");
        }
        if (stericState == null || stericState.isBlank()) {
            throw new IllegalArgumentException("stericState must not be blank");
        }
        interactionFingerprint = Set.copyOf(Objects.requireNonNull(
                interactionFingerprint, "interactionFingerprint"));
        differentialPatches = Set.copyOf(Objects.requireNonNull(
                differentialPatches, "differentialPatches"));
        provenance = Map.copyOf(Objects.requireNonNull(provenance, "provenance"));
    }
}
