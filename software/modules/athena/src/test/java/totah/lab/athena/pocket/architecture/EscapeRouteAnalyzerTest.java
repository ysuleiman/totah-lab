package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

class EscapeRouteAnalyzerTest {

    private static final String PROVENANCE =
            "test: stage8_11-derived synthetic grid";

    /**
     * Corridor with a known bottleneck (verified against a
     * line-by-line replica of the stage8_11 algorithm): grid
     * [0,8]x[0,4]x[0,4] at 1.0 A spacing; all boundary faces except
     * x=8 are blocked by 0.5 A spheres; a 0.25 A wall at x=5 leaves a
     * single hole at (5,2,2). The only escape runs straight down +x
     * with bottleneck clearance 1.0 - 0.25 = 0.75 A.
     */
    private static List<OccupancySphere> corridorOccupancy() {
        List<OccupancySphere> spheres = new ArrayList<>();
        for (int x = 0; x <= 8; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) {
                    if (x == 0 || y == 0 || y == 4 || z == 0 || z == 4) {
                        spheres.add(new OccupancySphere(
                                new Point3D(x, y, z), 0.5));
                    }
                    if (x == 5 && !(y == 2 && z == 2)) {
                        spheres.add(new OccupancySphere(
                                new Point3D(x, y, z), 0.25));
                    }
                }
            }
        }
        return spheres;
    }

    private static EscapeRouteAnalyzer corridorAnalyzer() {
        return new EscapeRouteAnalyzer(new EscapeRouteOptions(
                1.0, 0.5, 2.0, PROVENANCE));
    }

    @Test
    void openPocketEscapesWithKnownBottleneckWidth() {
        EscapeRouteAnalysis result = corridorAnalyzer().analyze(
                List.of(new Point3D(2, 2, 2), new Point3D(6, 2, 2)),
                corridorOccupancy(),
                new Point3D(2, 2, 2)
        );

        assertThat(result.classification())
                .isEqualTo(EscapeRouteClassification.ESCAPE_ROUTE_EXISTS);
        assertThat(result.originVoxel()).isEqualTo(new Point3D(2, 2, 2));
        assertThat(result.originClearanceAngstroms())
                .isCloseTo(1.5, offset(1.0e-12));
        assertThat(result.bottleneckClearanceAngstroms())
                .isCloseTo(0.75, offset(1.0e-12));
        assertThat(result.bottleneckVoxel())
                .isEqualTo(new Point3D(5, 2, 2));
        assertThat(result.escapePath()).containsExactly(
                new Point3D(2, 2, 2),
                new Point3D(3, 2, 2),
                new Point3D(4, 2, 2),
                new Point3D(5, 2, 2),
                new Point3D(6, 2, 2),
                new Point3D(7, 2, 2),
                new Point3D(8, 2, 2)
        );
        assertThat(result.reachableVoxelCount()).isEqualTo(64);
        assertThat(result.reachableVolumeCubicAngstroms())
                .isCloseTo(64.0, offset(1.0e-12));
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).label()).isEqualTo(1);
        assertThat(result.components().get(0).voxelCount()).isEqualTo(64);
        assertThat(result.options().probeRadiusAngstroms()).isEqualTo(0.5);
    }

    /**
     * Fully enclosed pocket: a dense 100-sphere shell of radius 1.0 A
     * atoms on a sphere of radius 3.0 A around the origin, grid
     * [-4,4]^3 at 1.0 A spacing, probe 0.5 A. The shell seals the
     * interior; the passable mask splits into an exterior (367
     * voxels) and an interior (19 voxels) component.
     */
    private static List<OccupancySphere> shellOccupancy() {
        List<OccupancySphere> spheres = new ArrayList<>();
        int count = 100;
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int k = 0; k < count; k++) {
            double z = 1.0 - 2.0 * (k + 0.5) / count;
            double radial = Math.sqrt(1.0 - z * z);
            double theta = goldenAngle * k;
            spheres.add(new OccupancySphere(
                    new Point3D(
                            3.0 * radial * Math.cos(theta),
                            3.0 * radial * Math.sin(theta),
                            3.0 * z),
                    1.0
            ));
        }
        return spheres;
    }

    @Test
    void fullyEnclosedPocketHasNoEscapeAndTwoComponents() {
        EscapeRouteAnalyzer analyzer = new EscapeRouteAnalyzer(
                new EscapeRouteOptions(1.0, 0.5, 4.0, PROVENANCE));

        EscapeRouteAnalysis result = analyzer.analyze(
                List.of(new Point3D(0, 0, 0)),
                shellOccupancy(),
                new Point3D(0, 0, 0)
        );

        assertThat(result.classification())
                .isEqualTo(EscapeRouteClassification.NO_ESCAPE_ROUTE);
        assertThat(result.originClearanceAngstroms())
                .isCloseTo(2.0, offset(1.0e-9));
        assertThat(result.reachableVoxelCount()).isEqualTo(19);
        assertThat(result.reachableVolumeCubicAngstroms())
                .isCloseTo(19.0, offset(1.0e-12));

        // The widest path still punches through the wall; its
        // bottleneck clearance falls below the probe radius.
        assertThat(result.bottleneckClearanceAngstroms()).isLessThan(0.0);
        assertThat(result.escapePath().get(0))
                .isEqualTo(new Point3D(0, 0, 0));
        Point3D exit = result.escapePath()
                .get(result.escapePath().size() - 1);
        assertThat(Math.abs(exit.x()) == 4.0
                || Math.abs(exit.y()) == 4.0
                || Math.abs(exit.z()) == 4.0).isTrue();

        assertThat(result.components()).hasSize(2);
        EscapeRouteComponent exterior = result.components().get(0);
        EscapeRouteComponent interior = result.components().get(1);
        assertThat(exterior.label()).isEqualTo(1);
        assertThat(exterior.voxelCount()).isEqualTo(367);
        assertThat(interior.label()).isEqualTo(2);
        assertThat(interior.voxelCount()).isEqualTo(19);
        assertThat(interior.volumeCubicAngstroms())
                .isCloseTo(19.0, offset(1.0e-12));
        assertThat(interior.centroid()).isEqualTo(new Point3D(0, 0, 0));
    }

    @Test
    void originInOccupiedVoxelIsClassifiedOccupied() {
        EscapeRouteAnalyzer analyzer = new EscapeRouteAnalyzer(
                new EscapeRouteOptions(1.0, 0.5, 2.0, PROVENANCE));

        EscapeRouteAnalysis result = analyzer.analyze(
                List.of(new Point3D(0, 0, 0)),
                List.of(new OccupancySphere(new Point3D(0, 0, 0), 1.0)),
                new Point3D(0, 0, 0)
        );

        assertThat(result.classification())
                .isEqualTo(EscapeRouteClassification.ORIGIN_OCCUPIED);
        assertThat(result.originVoxel()).isEqualTo(new Point3D(0, 0, 0));
        assertThat(result.originClearanceAngstroms())
                .isCloseTo(-1.0, offset(1.0e-12));
        assertThat(result.reachableVoxelCount()).isZero();
        assertThat(result.reachableVolumeCubicAngstroms()).isZero();

        // The widest path is still reported; its bottleneck is the
        // occupied origin cell itself.
        assertThat(result.bottleneckClearanceAngstroms())
                .isCloseTo(-1.0, offset(1.0e-12));
        assertThat(result.bottleneckVoxel())
                .isEqualTo(new Point3D(0, 0, 0));
        assertThat(result.escapePath().get(0))
                .isEqualTo(new Point3D(0, 0, 0));

        // Cells at distance >= 1.5 A from the blocking sphere remain
        // passable and form a single connected component.
        assertThat(result.components()).hasSize(1);
        assertThat(result.components().get(0).voxelCount()).isEqualTo(106);
    }

    @Test
    void resultsAreDeterministic() {
        EscapeRouteAnalyzer analyzer = corridorAnalyzer();
        List<Point3D> region =
                List.of(new Point3D(2, 2, 2), new Point3D(6, 2, 2));
        List<OccupancySphere> occupancy = corridorOccupancy();

        EscapeRouteAnalysis first =
                analyzer.analyze(region, occupancy, new Point3D(2, 2, 2));
        EscapeRouteAnalysis second =
                analyzer.analyze(region, occupancy, new Point3D(2, 2, 2));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void rejectsInvalidOptions() {
        assertThatThrownBy(() -> new EscapeRouteOptions(
                0.0, 1.7, 8.0, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EscapeRouteOptions(
                0.5, -1.0, 8.0, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EscapeRouteOptions(
                0.5, 1.7, Double.NaN, PROVENANCE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EscapeRouteOptions(
                0.5, 1.7, 8.0, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidInputs() {
        EscapeRouteAnalyzer analyzer = corridorAnalyzer();
        assertThatThrownBy(() -> analyzer.analyze(
                List.of(), List.of(), new Point3D(0, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OccupancySphere(
                new Point3D(0, 0, 0), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
