package totah.lab.athena.pocket.compare.residue;

import totah.lab.gaia.geometry.Point3D;

public record PocketResiduePoint(
        ResidueReference reference,
        Point3D position,
        ResidueChemistry chemistry
) {
}