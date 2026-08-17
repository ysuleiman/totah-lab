package totah.lab.gaia.geometry;

import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Domain-neutral geometric measurements over structure residues. */
public final class ResidueGeometry {

    private ResidueGeometry() {
    }

    public static OptionalDouble minimumAtomDistance(
            Residue first,
            Residue second,
            AtomSelection selection) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(selection, "selection");

        double minimumSquared = Double.POSITIVE_INFINITY;
        for (Atom firstAtom : first.getAtoms()) {
            if (!selection.includes(firstAtom)) {
                continue;
            }
            for (Atom secondAtom : second.getAtoms()) {
                if (!selection.includes(secondAtom)) {
                    continue;
                }
                minimumSquared = Math.min(
                        minimumSquared,
                        firstAtom.getPosition().distanceSquared(
                                secondAtom.getPosition()));
            }
        }

        return Double.isFinite(minimumSquared)
                ? OptionalDouble.of(Math.sqrt(minimumSquared))
                : OptionalDouble.empty();
    }

    public static OptionalDouble alphaCarbonDistance(
            Residue first,
            Residue second) {

        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");

        return first.getAlphaCarbonPosition().flatMap(firstPosition ->
                        second.getAlphaCarbonPosition().map(firstPosition::distance))
                .map(OptionalDouble::of)
                .orElseGet(OptionalDouble::empty);
    }

    public static Optional<Point3D> centroid(
            Residue residue,
            AtomSelection selection) {

        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(selection, "selection");

        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (Atom atom : residue.getAtoms()) {
            if (!selection.includes(atom)) {
                continue;
            }
            Point3D position = atom.getPosition();
            x += position.x();
            y += position.y();
            z += position.z();
            count++;
        }

        if (count == 0) {
            return Optional.empty();
        }
        return Optional.of(new Point3D(
                x / count,
                y / count,
                z / count));
    }
}
