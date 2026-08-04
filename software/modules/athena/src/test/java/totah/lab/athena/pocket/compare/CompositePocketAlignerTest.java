package totah.lab.athena.pocket.compare;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositePocketAlignerTest {

    private static final double TOLERANCE = 1.0e-6;

    @Test
    void returnedTransformReproducesAlignedCandidate() {
        PocketPointCloud query = new PocketPointCloud(
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(2.0, 0.0, 0.0),
                        new Point3D(0.0, 3.0, 0.0),
                        new Point3D(0.0, 0.0, 4.0),
                        new Point3D(1.0, 2.0, 3.0),
                        new Point3D(-1.0, 1.0, 2.0)
                ),
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketPointCloud candidate = new PocketPointCloud(
                List.of(
                        new Point3D(10.0, 5.0, -2.0),
                        new Point3D(10.0, 7.0, -2.0),
                        new Point3D(7.0, 5.0, -2.0),
                        new Point3D(10.0, 5.0, 2.0),
                        new Point3D(8.0, 6.0, 1.0),
                        new Point3D(9.0, 4.0, 0.0)
                ),
                PocketGeometryBasis.RESIDUE_ATOMS
        );

        PocketAlignment result =
                new CompositePocketAligner().align(
                        query,
                        candidate
                );

        List<Point3D> transformed =
                result.transform().apply(
                        candidate.points()
                );

        List<Point3D> aligned =
                result.alignedCandidate().points();

        assertEquals(
                aligned.size(),
                transformed.size()
        );

        for (int index = 0; index < aligned.size(); index++) {
            assertEquals(
                    aligned.get(index).x(),
                    transformed.get(index).x(),
                    TOLERANCE
            );

            assertEquals(
                    aligned.get(index).y(),
                    transformed.get(index).y(),
                    TOLERANCE
            );

            assertEquals(
                    aligned.get(index).z(),
                    transformed.get(index).z(),
                    TOLERANCE
            );
        }
    }
}