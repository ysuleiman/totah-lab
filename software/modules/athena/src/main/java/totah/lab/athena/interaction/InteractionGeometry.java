package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.athena.interaction.perception.ChargedGroup;
import totah.lab.gaia.geometry.Plane3D;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared identifier synthesis and ring-plane fitting for the detectors. */
final class InteractionGeometry {

    private InteractionGeometry() {
    }

    /**
     * Synthesizes the stable identifier of a charged group as
     * {@code "<ChargedGroupType> <chainId>:<residueNumber>"}, e.g.
     * {@code "RESIDUE_HIS A:42"}. The format is part of the
     * {@link Interaction} record contract.
     */
    static String chargedGroupId(ChargedGroup group) {
        Objects.requireNonNull(group, "group");
        return group.type().name() + " "
                + group.owner().chainId() + ":"
                + group.owner().residueNumber();
    }

    /**
     * Fits a least-squares plane to the ring atoms. Empty when the ring
     * was perceived via a degraded fallback (its topology, hence its
     * plane, is unknown and never guessed) or when the atoms are
     * coincident/(near-)collinear so no unique plane exists.
     */
    static Optional<Plane3D> ringPlane(AromaticRing ring) {
        Objects.requireNonNull(ring, "ring");
        if (ring.degraded()) {
            return Optional.empty();
        }
        List<Point3D> points = ring.atoms().stream()
                .map(Atom::getPosition)
                .toList();
        try {
            return Optional.of(Plane3D.fit(points));
        } catch (IllegalArgumentException unfit) {
            return Optional.empty();
        }
    }
}
