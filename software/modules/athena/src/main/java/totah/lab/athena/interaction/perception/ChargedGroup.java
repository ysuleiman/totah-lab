package totah.lab.athena.interaction.perception;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;

/**
 * A perceived charged group.
 *
 * @param sign sign of the group charge
 * @param type chemical type label of the group
 * @param owner identity of the residue (protein residue or ligand residue)
 *              that owns the group
 * @param atoms atoms forming the group, in structure traversal order
 * @param chargeCenter geometric centroid of the group atoms
 * @param provenance how the group was perceived
 * @param note human-readable provenance note (e.g. pH dependence of HIS)
 */
public record ChargedGroup(
        ChargeSign sign,
        ChargedGroupType type,
        ResidueId owner,
        List<Atom> atoms,
        Point3D chargeCenter,
        PerceptionProvenance provenance,
        String note) {

    public ChargedGroup {
        Objects.requireNonNull(sign, "sign");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException(
                    "a charged group requires at least one atom");
        }
        Objects.requireNonNull(chargeCenter, "chargeCenter");
        Objects.requireNonNull(provenance, "provenance");
        note = HydrophobicAtoms.requireNote(note);
    }

    /** Returns {@code true} when the group was perceived via a degraded fallback. */
    public boolean degraded() {
        return provenance.isDegraded();
    }
}
