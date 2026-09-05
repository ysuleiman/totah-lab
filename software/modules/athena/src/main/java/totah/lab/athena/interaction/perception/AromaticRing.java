package totah.lab.athena.interaction.perception;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * A perceived aromatic ring.
 *
 * @param ringId stable identifier, e.g. {@code "PHE A:43 ring0"}; unique
 *               within one perception run
 * @param owner identity of the residue (protein residue or ligand residue)
 *              that owns the ring
 * @param atoms ring atoms, ordered along the ring starting from the
 *              lowest canonical atom reference
 * @param centroid geometric centroid of the ring atoms
 * @param source how the ring was perceived; the degraded
 *               {@link PerceptionProvenance#AD4_FALLBACK} source means the
 *               ring topology is unknown and {@code atoms} merely groups the
 *               AD4 aromatic-typed atoms of the owning residue
 * @param note human-readable provenance note
 */
public record AromaticRing(
        String ringId,
        ResidueId owner,
        List<Atom> atoms,
        Point3D centroid,
        PerceptionProvenance source,
        String note) {

    public AromaticRing {
        Objects.requireNonNull(ringId, "ringId");
        if (ringId.isBlank()) {
            throw new IllegalArgumentException("ringId must not be blank");
        }
        Objects.requireNonNull(owner, "owner");
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        if (atoms.size() < 3) {
            throw new IllegalArgumentException(
                    "an aromatic ring requires at least 3 atoms: " + ringId);
        }
        Objects.requireNonNull(centroid, "centroid");
        Objects.requireNonNull(source, "source");
        note = HydrophobicAtoms.requireNote(note);
    }

    /** Returns {@code true} when the ring was perceived via a degraded fallback. */
    public boolean degraded() {
        return source.isDegraded();
    }
}
