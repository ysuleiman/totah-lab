package totah.lab.athena.pocket.compare.residue;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@link PocketResiduePointTransformer} applies rigid
 * transforms to residue positions while preserving references,
 * chemistry classes and input order.
 */
class PocketResiduePointTransformerTest {

    private static final double TOLERANCE = 1.0e-9;

    // 90 degrees about the z-axis: (x, y, z) -> (-y, x, z).
    private static final double[][] ROTATION_90_Z = {
            {0.0, -1.0, 0.0},
            {1.0, 0.0, 0.0},
            {0.0, 0.0, 1.0}
    };

    private static final RigidTransform TRANSFORM =
            new RigidTransform(
                    ROTATION_90_Z,
                    new Point3D(3.0, -2.0, 5.0)
            );

    private final PocketResiduePointTransformer transformer =
            new PocketResiduePointTransformer();

    @Test
    void appliesTransformToPositionsOnly() {
        List<PocketResiduePoint> points = List.of(
                point("A", 10, "LEU",
                        ResidueChemistry.HYDROPHOBIC,
                        1.0, 2.0, 3.0),
                point("B", 20, "LYS",
                        ResidueChemistry.POSITIVE,
                        -4.0, 0.5, -1.0),
                point("A", 30, "GLY",
                        ResidueChemistry.GLYCINE,
                        0.0, 0.0, 0.0)
        );

        List<PocketResiduePoint> transformed =
                transformer.transform(points, TRANSFORM);

        assertEquals(3, transformed.size());

        assertPosition(
                transformed.get(0),
                // (-2 + 3, 1 - 2, 3 + 5)
                1.0, -1.0, 8.0
        );
        assertPosition(
                transformed.get(1),
                // (-0.5 + 3, -4 - 2, -1 + 5)
                2.5, -6.0, 4.0
        );
        assertPosition(
                transformed.get(2),
                // (0 + 3, 0 - 2, 0 + 5)
                3.0, -2.0, 5.0
        );

        for (int index = 0; index < points.size(); index++) {
            assertEquals(
                    points.get(index).reference(),
                    transformed.get(index).reference()
            );
            assertEquals(
                    points.get(index).chemistry(),
                    transformed.get(index).chemistry()
            );
        }
    }

    @Test
    void returnsImmutableList() {
        List<PocketResiduePoint> transformed =
                transformer.transform(
                        List.of(point("A", 1, "ALA",
                                ResidueChemistry.HYDROPHOBIC,
                                0.0, 0.0, 0.0)),
                        TRANSFORM
                );

        assertThrows(
                UnsupportedOperationException.class,
                () -> transformed.add(point("A", 2, "ALA",
                        ResidueChemistry.HYDROPHOBIC,
                        1.0, 1.0, 1.0))
        );
    }

    private static void assertPosition(
            PocketResiduePoint point,
            double x,
            double y,
            double z
    ) {
        assertEquals(x, point.position().x(), TOLERANCE, "x");
        assertEquals(y, point.position().y(), TOLERANCE, "y");
        assertEquals(z, point.position().z(), TOLERANCE, "z");
    }

    private static PocketResiduePoint point(
            String chainId,
            int residueNumber,
            String residueName,
            ResidueChemistry chemistry,
            double x,
            double y,
            double z
    ) {
        return new PocketResiduePoint(
                new ResidueReference(
                        chainId,
                        residueNumber,
                        ' ',
                        residueName
                ),
                new Point3D(x, y, z),
                chemistry
        );
    }
}
