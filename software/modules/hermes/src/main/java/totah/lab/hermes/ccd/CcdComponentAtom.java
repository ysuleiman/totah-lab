package totah.lab.hermes.ccd;

import totah.lab.gaia.geometry.Point3D;

import java.util.Objects;

/** One atom definition reported by the Chemical Component Dictionary. */
public record CcdComponentAtom(
        String atomId,
        String element,
        int formalCharge,
        boolean aromatic,
        boolean leavingAtom,
        Point3D modelPosition,
        Point3D idealPosition) {

    public CcdComponentAtom {
        atomId = requireText(atomId, "atomId");
        element = requireText(element, "element");
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank.");
        }
        return normalized;
    }
}
