package totah.lab.athena.surface.differential;

import totah.lab.athena.interaction.Interaction;

import java.util.Objects;

/** One ligand interaction annotated without collapsing its surface evidence. */
public record DifferentialInteraction(
        Interaction interaction,
        DifferentialResidueScore surfaceScore,
        DifferentialDirection direction) {
    public DifferentialInteraction {
        Objects.requireNonNull(interaction, "interaction");
        Objects.requireNonNull(surfaceScore, "surfaceScore");
        Objects.requireNonNull(direction, "direction");
    }
}
