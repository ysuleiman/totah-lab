package totah.lab.proteus.protein.mutation;

import totah.lab.gaia.structure.ResidueId;

public record AppliedMutation(ResidueId target, String originalResidueName,
                              String replacementResidueName, String rotamerId,
                              double stericScore) {
}
