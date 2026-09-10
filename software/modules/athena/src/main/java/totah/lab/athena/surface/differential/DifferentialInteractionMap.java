package totah.lab.athena.surface.differential;

import totah.lab.athena.interaction.InteractionProfile;

import java.util.List;
import java.util.Objects;

/** Composition layer over an existing interaction profile and surface map. */
public record DifferentialInteractionMap(
        DifferentialSurfaceMap surfaceMap,
        List<DifferentialInteraction> interactions) {
    public DifferentialInteractionMap {
        Objects.requireNonNull(surfaceMap, "surfaceMap");
        interactions = List.copyOf(Objects.requireNonNull(interactions, "interactions"));
    }

    public static DifferentialInteractionMap overlay(
            DifferentialSurfaceMap surfaceMap,
            InteractionProfile interactionProfile,
            DifferentialDirection direction) {
        Objects.requireNonNull(surfaceMap, "surfaceMap");
        Objects.requireNonNull(interactionProfile, "interactionProfile");
        Objects.requireNonNull(direction, "direction");
        List<DifferentialInteraction> rows = interactionProfile.interactions().stream()
                .map(interaction -> surfaceMap.score(interaction.residue())
                        .map(score -> new DifferentialInteraction(
                                interaction, score, direction)))
                .flatMap(java.util.Optional::stream)
                .toList();
        return new DifferentialInteractionMap(surfaceMap, rows);
    }
}
