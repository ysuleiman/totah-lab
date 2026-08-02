package totah.lab.athena.pocket.selection;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves pocket residue identities and selects their structural lining. */
public final class PocketResidueSelection {

    public List<Residue> resolvedResidues(
            Structure structure,
            Pocket pocket) {
        requireInputs(structure, pocket);
        return pocket.residues().stream()
                .map(structure::findResidue)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public List<ResidueId> unresolvedResidues(
            Structure structure,
            Pocket pocket) {
        requireInputs(structure, pocket);
        return pocket.residues().stream()
                .filter(id -> structure.findResidue(id).isEmpty())
                .toList();
    }

    /**
     * Returns resolved pocket residues followed by structure residues whose
     * heavy atoms are within the cutoff of a resolved pocket residue heavy
     * atom. This preserves the legacy lining-residue distance definition.
     */
    public List<Residue> liningResidues(
            Structure structure,
            Pocket pocket,
            double cutoffAngstroms) {
        requireInputs(structure, pocket);
        if (!Double.isFinite(cutoffAngstroms)
                || cutoffAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "cutoffAngstroms must be finite and positive");
        }

        List<Residue> resolved = resolvedResidues(structure, pocket);
        Map<ResidueId, Residue> lining = new LinkedHashMap<>();
        for (ResidueId id : pocket.residues()) {
            structure.findResidue(id).ifPresent(residue ->
                    lining.putIfAbsent(id, residue));
        }

        Set<ResidueId> pocketIds = new HashSet<>(pocket.residues());
        for (LocatedResidue candidate : allResidues(structure)) {
            if (!pocketIds.contains(candidate.id())
                    && isNeighbor(
                            candidate.residue(),
                            resolved,
                            cutoffAngstroms)) {
                lining.putIfAbsent(candidate.id(), candidate.residue());
            }
        }
        return List.copyOf(lining.values());
    }

    private static boolean isNeighbor(
            Residue candidate,
            List<Residue> pocketResidues,
            double cutoff) {
        double cutoffSquared = cutoff * cutoff;
        for (Atom candidateAtom : candidate.getAtoms()) {
            if (!candidateAtom.isHeavyAtom()) {
                continue;
            }
            for (Residue pocketResidue : pocketResidues) {
                for (Atom pocketAtom : pocketResidue.getAtoms()) {
                    if (pocketAtom.isHeavyAtom()
                            && candidateAtom.getPosition().distanceSquared(
                                    pocketAtom.getPosition())
                            <= cutoffSquared) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<LocatedResidue> allResidues(Structure structure) {
        List<LocatedResidue> residues = new ArrayList<>();
        structure.getChains().forEach(chain ->
                chain.residues().forEach(residue -> residues.add(
                        new LocatedResidue(
                                new ResidueId(
                                        chain.id(),
                                        residue.getNumber(),
                                        residue.getInsertionCode()),
                                residue))));
        return residues;
    }

    private static void requireInputs(
            Structure structure,
            Pocket pocket) {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");
    }

    private record LocatedResidue(
            ResidueId id,
            Residue residue) {
    }
}
