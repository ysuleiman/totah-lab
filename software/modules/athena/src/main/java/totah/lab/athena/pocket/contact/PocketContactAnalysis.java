package totah.lab.athena.pocket.contact;


import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static totah.lab.athena.pocket.geometry.PocketGeometry.areNeighbors;

/**
 * Heavy-atom contact and distance operations involving residues and ligands.
 *
 * <p>All cutoffs and returned distances are measured in angstroms. Hydrogen
 * atoms are excluded from contact and minimum-distance calculations.</p>
 *
 * <p>This class is independent of pocket-source formats. It operates only on
 * Gaia molecular types.</p>
 */
public final class PocketContactAnalysis {

    private PocketContactAnalysis() {
    }
    public static List<Residue> ligandNeighbors(
            Structure structure,
            Ligand ligand,
            double cutoffAngstroms) {

        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(ligand, "ligand");
        validatePositiveFinite(cutoffAngstroms, "cutoffAngstroms");

        return residues(structure)
                .filter(residue ->
                        areNeighbors(residue, ligand, cutoffAngstroms))
                .toList();
    }

    private static Stream<Residue> residues(Structure structure) {
        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream());
    }

    private static void validatePositiveFinite(
            double value,
            String name) {

        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    name + " must be finite and greater than zero");
        }
    }
}