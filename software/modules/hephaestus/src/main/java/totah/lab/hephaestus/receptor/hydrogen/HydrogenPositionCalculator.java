package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.geometry.ZMatrixMath;
import totah.lab.gaia.structure.Atom;

import java.util.List;
import java.util.Objects;

public final class HydrogenPositionCalculator {

    public List<Point3D> methylPositions(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor) {

        return positions(
                center,
                firstAnchor,
                secondAnchor,
                HydrogenGeometry.C_H_SP3,
                HydrogenGeometry.TETRAHEDRAL_ANGLE,
                HydrogenGeometry.METHYL_DIHEDRALS);
    }

    public List<Point3D> methylenePositions(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor) {

        return positions(
                center,
                firstAnchor,
                secondAnchor,
                HydrogenGeometry.C_H_SP3,
                HydrogenGeometry.TETRAHEDRAL_ANGLE,
                HydrogenGeometry.METHYLENE_DIHEDRALS);
    }

    public List<Point3D> planarNh2Positions(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor) {

        return positions(
                center,
                firstAnchor,
                secondAnchor,
                HydrogenGeometry.N_H_SP2,
                HydrogenGeometry.TRIGONAL_ANGLE,
                HydrogenGeometry.PLANAR_NH2_DIHEDRALS);
    }

    public List<Point3D> ammoniumPositions(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor) {

        return positions(
                center,
                firstAnchor,
                secondAnchor,
                HydrogenGeometry.N_H_SP3,
                HydrogenGeometry.TETRAHEDRAL_ANGLE,
                HydrogenGeometry.METHYL_DIHEDRALS);
    }

    public List<Point3D> secondaryAmmoniumPositions(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor) {

        return positions(
                center,
                firstAnchor,
                secondAnchor,
                HydrogenGeometry.N_H_SP3,
                HydrogenGeometry.TETRAHEDRAL_ANGLE,
                HydrogenGeometry.METHYLENE_DIHEDRALS);
    }

    public Point3D aromaticHydrogenPosition(
            Atom carbon,
            Atom firstNeighbor,
            Atom secondNeighbor) {

        requirePosition(carbon, "carbon");
        requirePosition(firstNeighbor, "firstNeighbor");
        requirePosition(secondNeighbor, "secondNeighbor");

        Point3D center = carbon.getPosition();

        Vector3D firstDirection =
                center.vectorTo(firstNeighbor.getPosition()).normalize();

        Vector3D secondDirection =
                center.vectorTo(secondNeighbor.getPosition()).normalize();

        Vector3D inwardBisector =
                firstDirection.add(secondDirection).normalize();

        Vector3D outwardDirection =
                inwardBisector.scale(-1.0);

        return center.add(
                outwardDirection.scale(
                        HydrogenGeometry.C_H_SP2));
    }

    public Point3D tetrahedralFourthPosition(
            Atom center,
            Atom firstNeighbor,
            Atom secondNeighbor,
            Atom thirdNeighbor,
            double bondLength) {

        requirePosition(center, "center");
        requirePosition(firstNeighbor, "firstNeighbor");
        requirePosition(secondNeighbor, "secondNeighbor");
        requirePosition(thirdNeighbor, "thirdNeighbor");

        Vector3D directionSum =
                center.getPosition()
                        .vectorTo(firstNeighbor.getPosition())
                        .normalize()
                        .add(
                                center.getPosition()
                                        .vectorTo(secondNeighbor.getPosition())
                                        .normalize())
                        .add(
                                center.getPosition()
                                        .vectorTo(thirdNeighbor.getPosition())
                                        .normalize());

        if (directionSum.magnitude() < 1.0e-12) {
            throw new IllegalArgumentException(
                    "Unable to determine tetrahedral fourth position.");
        }

        return center.getPosition().add(
                directionSum.normalize()
                        .scale(-bondLength));
    }

    private List<Point3D> positions(
            Atom center,
            Atom firstAnchor,
            Atom secondAnchor,
            double bondLength,
            double bondAngle,
            double[] dihedrals) {

        requirePosition(center, "center");
        requirePosition(firstAnchor, "firstAnchor");
        requirePosition(secondAnchor, "secondAnchor");

        return java.util.Arrays.stream(dihedrals)
                .mapToObj(dihedral ->
                        ZMatrixMath.calculatePosition(
                                center.getPosition(),
                                firstAnchor.getPosition(),
                                secondAnchor.getPosition(),
                                bondLength,
                                bondAngle,
                                dihedral))
                .toList();
    }

    private void requirePosition(
            Atom atom,
            String fieldName) {

        Objects.requireNonNull(atom, fieldName);

        if (atom.getPosition() == null) {
            throw new IllegalArgumentException(
                    fieldName + " must have a position.");
        }
    }
}
