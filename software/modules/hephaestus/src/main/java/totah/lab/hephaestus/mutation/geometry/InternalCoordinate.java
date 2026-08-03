package totah.lab.hephaestus.mutation.geometry;

import totah.lab.gaia.chemistry.Element;

import java.util.Objects;

public record InternalCoordinate(
        String atomName,
        Element element,
        String bondReference,
        String angleReference,
        String dihedralReference,
        double bondLength,
        double bondAngleRadians,
        double dihedralRadians,
        boolean applyFirstChi) {

    public InternalCoordinate {
        atomName = text(atomName, "atomName");
        Objects.requireNonNull(element, "element");
        bondReference = text(bondReference, "bondReference");
        angleReference = text(angleReference, "angleReference");
        dihedralReference = text(dihedralReference, "dihedralReference");
        if (!Double.isFinite(bondLength) || bondLength <= 0.0) {
            throw new IllegalArgumentException("bondLength must be finite and positive");
        }
        if (!Double.isFinite(bondAngleRadians) || !Double.isFinite(dihedralRadians)) {
            throw new IllegalArgumentException("angles must be finite");
        }
    }

    private static String text(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }
}
