package totah.lab.athena.pocket.pocketmatch;

import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/**
 * One representative point of a pocket residue in the PocketMatch
 * representation: a typed coordinate carrying its residue identity and
 * PocketMatch chemistry group.
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchPoint(
        ResidueReference residue,
        PocketMatchResidueGroup residueGroup,
        PocketMatchPointType pointType,
        Point3D position
) {

    public PocketMatchPoint {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(residueGroup, "residueGroup");
        Objects.requireNonNull(pointType, "pointType");
        Objects.requireNonNull(position, "position");
    }
}
