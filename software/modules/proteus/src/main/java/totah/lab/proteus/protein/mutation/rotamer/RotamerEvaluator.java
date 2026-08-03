package totah.lab.proteus.protein.mutation.rotamer;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

/** Deterministic heavy-atom steric score; lower scores are better. */
public final class RotamerEvaluator {
    public double score(Structure structure, ResidueId target, List<Atom> candidateAtoms) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candidateAtoms, "candidateAtoms");
        List<Atom> environment = structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream()
                        .filter(residue -> !(chain.id().equals(target.chainId())
                                && residue.getNumber() == target.residueNumber()
                                && Objects.equals(residue.getInsertionCode(), target.insertionCode())))
                        .flatMap(residue -> residue.getAtoms().stream()))
                .filter(atom -> !atom.isHydrogen())
                .toList();
        double score = 0.0;
        for (Atom candidate : candidateAtoms) {
            if (candidate.isHydrogen()) continue;
            for (Atom other : environment) {
                double overlap = 2.8 - distance(candidate.getPosition(), other.getPosition());
                if (overlap > 0.0) score += overlap * overlap;
            }
        }
        return score;
    }

    private double distance(Point3D first, Point3D second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
