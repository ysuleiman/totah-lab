package totah.lab.mettl7.landscape;

import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.landscape.LigandConfiguration;

import java.util.Objects;

/** One explicitly matched ligand configuration realized in A and B. */
public record Mettl7MatchedConfiguration(
        String matchId,
        LigandConfiguration configuration,
        MolecularState mettl7aState,
        MolecularState mettl7bState,
        String correspondenceArtifactHash,
        String commonFrameProvenance) {
    public Mettl7MatchedConfiguration {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId must not be blank");
        }
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(mettl7aState, "mettl7aState");
        Objects.requireNonNull(mettl7bState, "mettl7bState");
        if (!mettl7aState.ligandId().equals(mettl7bState.ligandId())) {
            throw new IllegalArgumentException("A/B ligand identities differ");
        }
        if (correspondenceArtifactHash == null || correspondenceArtifactHash.isBlank()
                || commonFrameProvenance == null || commonFrameProvenance.isBlank()) {
            throw new IllegalArgumentException(
                    "correspondence hash and common-frame provenance are required");
        }
    }
}
