package totah.lab.athena.pocket.architecture;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regression on the real METTL7A pocket 32 / METTL7B
 * pocket 3 fixtures. The alpha-sphere CSVs carry centers only, so a
 * fixed sphere radius of 2.0 A is assigned (documented fixture
 * convention); receptor residues carry a single CA at the CSV
 * representative position.
 */
class Mettl7PocketArchitectureRegressionTest {

    private static final double FIXTURE_SPHERE_RADIUS = 2.0;

    @Test
    void mettl7ComparisonRunsEndToEndWithFiniteMetrics() {
        Structure receptorA = receptor("/mettl7/query_residues.csv");
        Structure receptorB = receptor("/mettl7/candidate_residues.csv");
        Pocket pocketA = pocket("32",
                "/mettl7/query_residues.csv",
                "/mettl7/query_alpha_spheres.csv");
        Pocket pocketB = pocket("3",
                "/mettl7/candidate_residues.csv",
                "/mettl7/candidate_alpha_spheres.csv");

        Ligand poseA = poseNearCentroid("pose-7A", pocketA);
        Ligand poseB = poseNearCentroid("pose-7B", pocketB);

        PocketArchitectureReport report =
                new PocketArchitectureAnalyzer().analyze(
                        receptorA, pocketA, poseA,
                        receptorB, pocketB, poseB
                );

        assertThat(report.backbone().caRmsd()).isFinite()
                .isGreaterThanOrEqualTo(0.0);
        assertThat(report.backbone().displacementProfile())
                .isNotEmpty();
        assertThat(report.alphaSpheres().componentsA()
                .componentCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.alphaSpheres().componentsB()
                .componentCount()).isGreaterThanOrEqualTo(1);
        assertThat(report.alphaSpheres().principalAxisAngleDegrees())
                .isFinite();
        assertThat(report.pocketA().cavityDepth()).isFinite();
        assertThat(report.pocketB().cavityDepth()).isFinite();
        assertThat(report.wall().meanWallDistanceA()).isFinite();
        assertThat(report.ligandSpace().dominantDifference())
                .isNotNull();

        String rendered = report.render();
        assertThat(rendered).contains("Pocket architecture comparison");
        assertThat(rendered).contains("Shape explanation");
        assertThat(rendered).contains("extra cavity:");
        assertThat(rendered).contains("backbone:");
        assertThat(rendered).contains("shifted wall:");
        assertThat(rendered).contains("aligned centroid displacement");
        assertThat(rendered).contains("Loop region (225-236)");
        assertThat(rendered).contains("verdict:");
    }

    private static Ligand poseNearCentroid(String id, Pocket pocket) {
        Point3D centroid = pocket.center();

        return ArchitectureTestFixtures.pose(id, new double[][]{
                {centroid.x(), centroid.y(), centroid.z()},
                {centroid.x() + 1, centroid.y(), centroid.z()},
                {centroid.x(), centroid.y() + 1, centroid.z()},
                {centroid.x(), centroid.y(), centroid.z() + 1}
        });
    }

    private static Structure receptor(String residuesResource) {
        List<Residue> residues = new ArrayList<>();
        int serial = 1;

        for (String line : readLines(residuesResource)) {
            String[] columns = line.split(",");
            residues.add(new Residue(
                    columns[1],
                    Integer.parseInt(columns[0]),
                    List.of(ArchitectureTestFixtures.atom(
                            serial++,
                            "CA",
                            new Point3D(
                                    Double.parseDouble(columns[3]),
                                    Double.parseDouble(columns[4]),
                                    Double.parseDouble(columns[5]))))
            ));
        }

        return new Structure(List.of(new Chain("A", residues)));
    }

    private static Pocket pocket(
            String id,
            String residuesResource,
            String spheresResource
    ) {
        List<ResidueId> residues = new ArrayList<>();
        for (String line : readLines(residuesResource)) {
            residues.add(new ResidueId(
                    "A",
                    Integer.parseInt(line.split(",")[0]),
                    null
            ));
        }

        List<AlphaSphere> spheres = new ArrayList<>();
        long sphereId = 1;
        for (String line : readLines(spheresResource)) {
            String[] columns = line.split(",");
            spheres.add(new AlphaSphere(
                    sphereId++,
                    new Point3D(
                            Double.parseDouble(columns[0]),
                            Double.parseDouble(columns[1]),
                            Double.parseDouble(columns[2])),
                    FIXTURE_SPHERE_RADIUS
            ));
        }

        return new Pocket(
                new PocketId(id),
                "pocket-" + id,
                PocketSource.FPOCKET,
                spheres.get(0).center(),
                residues,
                List.of(),
                Optional.empty(),
                Optional.of(new AlphaSphereSet(spheres)),
                Map.of()
        );
    }

    private static List<String> readLines(String resource) {
        InputStream input =
                Mettl7PocketArchitectureRegressionTest.class
                        .getResourceAsStream(resource);

        if (input == null) {
            throw new IllegalStateException(
                    "Missing test resource: " + resource
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
        )) {
            return reader.lines().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
