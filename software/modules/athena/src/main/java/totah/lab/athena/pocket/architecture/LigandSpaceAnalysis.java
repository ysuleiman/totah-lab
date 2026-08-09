package totah.lab.athena.pocket.architecture;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * Per-atom space description of one ligand pose inside its receptor
 * and pocket. All values are geometric estimates from heavy-atom and
 * alpha-sphere geometry, not physical measurements.
 *
 * <p>{@code shellFreeFraction} is a local free-volume proxy: the
 * fraction of 14 deterministic sample points (6 axis directions plus
 * 8 cube corners) on a probe-radius shell around the atom that have
 * NO receptor heavy atom within the probe radius — a fixed-pattern
 * approximation, no randomness involved. {@code meanWallDistance} is
 * the mean over atoms of the nearest receptor heavy-atom distance.
 */
public record LigandSpaceAnalysis(
        Ligand pose,
        List<LigandAtomSpace> atoms,
        double meanWallDistance
) {

    /**
     * Space metrics of one ligand heavy atom.
     *
     * @param atomName ligand atom name (structure order is preserved
     *                 in the enclosing list)
     * @param nearestReceptorAtomDistance distance to the nearest
     *        receptor heavy atom (wall distance)
     * @param nearestResidue residue of that nearest receptor atom
     * @param nearestBackboneAtomDistance distance to the nearest
     *        receptor backbone (N/CA/C/O/OXT) heavy atom
     * @param nearestSphereSurfaceDistance signed surface distance
     *        max(0, d - radius) to the nearest alpha sphere
     * @param shellFreeFraction free-volume proxy in [0, 1]
     */
    public record LigandAtomSpace(
            String atomName,
            double nearestReceptorAtomDistance,
            ResidueId nearestResidue,
            double nearestBackboneAtomDistance,
            double nearestSphereSurfaceDistance,
            double shellFreeFraction
    ) {
    }

    public LigandSpaceAnalysis {
        Objects.requireNonNull(pose, "pose");
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
    }
}
