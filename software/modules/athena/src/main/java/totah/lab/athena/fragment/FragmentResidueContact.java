package totah.lab.athena.fragment;

import java.util.Objects;
import java.util.Set;
import totah.lab.athena.pocket.evidence.EvidenceResidueId;

public record FragmentResidueContact(
        int fragmentAtomIndex,
        EvidenceResidueId residue,
        String residueAtomName,
        double distanceAngstrom,
        Set<FragmentPocketChemistry> evidenceChannels
) {
    public FragmentResidueContact {
        if (fragmentAtomIndex < 0 || distanceAngstrom < 0.0) throw new IllegalArgumentException("Invalid contact geometry");
        Objects.requireNonNull(residue, "residue");
        if (residueAtomName == null || residueAtomName.isBlank()) throw new IllegalArgumentException("residueAtomName is required");
        residueAtomName = residueAtomName.trim();
        evidenceChannels = Set.copyOf(Objects.requireNonNull(evidenceChannels, "evidenceChannels"));
    }
}
