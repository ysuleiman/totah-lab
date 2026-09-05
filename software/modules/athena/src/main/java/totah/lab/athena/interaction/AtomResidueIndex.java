package totah.lab.athena.interaction;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Identity-based lookup from {@link Atom} instances to their owning
 * residue and atom reference within one structure. Atoms are matched by
 * object identity, so the indexed atoms must be the exact instances held
 * by the structure.
 */
final class AtomResidueIndex {

    private final Map<Atom, ResidueId> residueByAtom = new IdentityHashMap<>();
    private final Map<Atom, AtomReference> referenceByAtom =
            new IdentityHashMap<>();

    private AtomResidueIndex() {
    }

    static AtomResidueIndex of(Structure structure) {
        Objects.requireNonNull(structure, "structure");
        AtomResidueIndex index = new AtomResidueIndex();
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                ResidueId residueId = new ResidueId(
                        chain.id(), residue.getNumber(),
                        residue.getInsertionCode());
                char insertionCode = residue.getInsertionCode() == null
                        ? ' ' : residue.getInsertionCode();
                for (Atom atom : residue.getAtoms()) {
                    index.residueByAtom.put(atom, residueId);
                    index.referenceByAtom.put(atom, new AtomReference(
                            chain.id(), residue.getNumber(),
                            insertionCode, atom.getName()));
                }
            }
        }
        return index;
    }

    /** Returns the residue owning {@code atom}, empty for foreign instances. */
    Optional<ResidueId> residueOf(Atom atom) {
        return Optional.ofNullable(residueByAtom.get(atom));
    }

    /** Returns the reference of {@code atom}, empty for foreign instances. */
    Optional<AtomReference> referenceOf(Atom atom) {
        return Optional.ofNullable(referenceByAtom.get(atom));
    }
}
