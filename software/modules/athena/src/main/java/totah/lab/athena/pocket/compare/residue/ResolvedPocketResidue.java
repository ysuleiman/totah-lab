package totah.lab.athena.pocket.compare.residue;

import totah.lab.gaia.structure.Residue;

public record ResolvedPocketResidue(
        String chainId,
        Residue residue
) {
}
