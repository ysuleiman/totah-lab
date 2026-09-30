package totah.lab.mettl7.phase2;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;

import java.util.List;
import java.util.Objects;

/** Reports, but does not score, a frozen attachment vector and its protein/SAM clearances. */
public final class RegionAGrowthVector {
    private RegionAGrowthVector() { }

    public static Result evaluate(String definitionId, Point3D attachment, Point3D outward,
                                  List<Point3D> proteinHeavyAtoms, List<Point3D> samHeavyAtoms) {
        if (definitionId == null || definitionId.isBlank()) throw new IllegalArgumentException("definition id required");
        Objects.requireNonNull(attachment, "attachment"); Objects.requireNonNull(outward, "outward");
        Vector3D vector = attachment.vectorTo(outward);
        if (vector.magnitude() == 0) throw new IllegalArgumentException("growth vector must be non-zero");
        return new Result(definitionId, attachment, outward, vector.magnitude(),
                minimum(outward, proteinHeavyAtoms), minimum(outward, samHeavyAtoms));
    }

    private static double minimum(Point3D point, List<Point3D> atoms) {
        Objects.requireNonNull(atoms, "atoms");
        return atoms.stream().mapToDouble(point::distance).min().orElse(Double.POSITIVE_INFINITY);
    }

    public record Result(String definitionId, Point3D attachment, Point3D outward,
                         double vectorLengthAngstroms, double proteinClearanceAngstroms,
                         double samClearanceAngstroms) { }
}
