package totah.lab.mettl7.campaign.v2;

import java.util.Objects;

/** A mandatory mechanistic branch; chemical species are frozen separately. */
public record CompoundBranch(
        String id,
        ChemistryBranch chemistryBranch,
        boolean productiveStateSearchRequired) {

    public CompoundBranch {
        if (Objects.requireNonNull(id, "id").isBlank()) {
            throw new IllegalArgumentException("blank compound branch id");
        }
        Objects.requireNonNull(chemistryBranch, "chemistryBranch");
    }

    public enum ChemistryBranch { S_METHYLATION, N_METHYLATION, INHIBITOR_SELECTIVITY }
}
