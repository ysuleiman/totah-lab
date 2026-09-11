package totah.lab.athena.recognition;

import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.surface.differential.DifferentialResidueScore;
import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;
import java.util.Optional;

/** One unweighted recognition relation composed from canonical Athena evidence. */
public record RecognitionEdge(
        String id,
        RecognitionNode ligandFeature,
        RecognitionNode environment,
        Interaction interaction,
        Optional<DifferentialResidueScore> differentialSurface,
        EvidenceQuality quality,
        InteractionRole role,
        String poseId,
        Optional<String> familyId,
        Optional<String> representativeProvenance) {

    public RecognitionEdge {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id required");
        Objects.requireNonNull(ligandFeature, "ligandFeature");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(interaction, "interaction");
        differentialSurface = Objects.requireNonNull(differentialSurface, "differentialSurface");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(role, "role");
        if (poseId == null || poseId.isBlank()) throw new IllegalArgumentException("poseId required");
        familyId = Objects.requireNonNull(familyId, "familyId");
        representativeProvenance = Objects.requireNonNull(representativeProvenance,
                "representativeProvenance");
        if (ligandFeature.kind() != RecognitionNode.Kind.LIGAND_FEATURE) {
            throw new IllegalArgumentException("edge must start at a ligand feature");
        }
        ResidueId environmentResidue = environment.residue().orElseThrow();
        if (!interaction.residue().equals(environmentResidue)) {
            throw new IllegalArgumentException("interaction/environment residue mismatch");
        }
        differentialSurface.ifPresent(score -> {
            if (!score.queryResidue().equals(environmentResidue)) {
                throw new IllegalArgumentException("surface/environment residue mismatch");
            }
        });
    }

    public ResidueId environmentResidue() {
        return environment.residue().orElseThrow();
    }

    public InteractionType interactionType() {
        return interaction.type();
    }

    public Key key() {
        return new Key(ligandFeature.id(), environmentResidue(), interactionType());
    }

    public boolean engagesDifferentialEnvironment() {
        return differentialSurface.map(score -> score.rup() > 0.0 || score.rus() > 0.0)
                .orElse(false);
    }

    public record Key(String ligandFeatureId, ResidueId residue, InteractionType type) {
        public Key {
            if (ligandFeatureId == null || ligandFeatureId.isBlank()) {
                throw new IllegalArgumentException("ligandFeatureId required");
            }
            Objects.requireNonNull(residue, "residue");
            Objects.requireNonNull(type, "type");
        }
    }
}
