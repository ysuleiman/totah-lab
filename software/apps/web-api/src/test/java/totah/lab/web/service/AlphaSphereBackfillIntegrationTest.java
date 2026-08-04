package totah.lab.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers {@link AlphaSphereBackfillService}: pockets imported before
 * alpha-sphere persistence (simulated by deleting the sphere rows the
 * importer now writes) are backfilled from the vert files that are the
 * siblings of their pocket artifacts.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=docking_test",
        "totah.artifacts.root=target/test-artifacts",
        "totah.import.min-pocket-residues=1"
})
class AlphaSphereBackfillIntegrationTest
        extends DockingTestSchemaSupport {

    static {
        recreateTestSchema();
    }

    @TempDir
    Path tempDir;

    @Autowired
    AlphaFoldPocketImportService importService;

    @Autowired
    AlphaSphereBackfillService backfillService;

    @Autowired
    JdbcTemplate jdbc;

    private long structureId;

    @BeforeEach
    void importFixtureWithoutSpheres() throws IOException {
        AlphaFoldPocketImportService.ImportResult result =
                importService.importStructure(
                        fixturePath(
                                "import/AF-P99901-F1-model_v4.pdb.gz"),
                        fixturePath(
                                "import/AF-P99901-F1-model_v4_out")
                );
        structureId = result.structureId();

        // Simulate a pre-sphere-persistence import.
        jdbc.update("DELETE FROM docking_test.pocket_alpha_sphere");
    }

    @AfterEach
    void truncateTestSchema() {
        jdbc.execute("""
                TRUNCATE docking_test.pocket_alpha_sphere,
                         docking_test.pocket_atom,
                         docking_test.pocket_residue,
                         docking_test.pocket,
                         docking_test.residue,
                         docking_test.structure,
                         docking_test.artifacts,
                         docking_test.receptor,
                         docking_test.targets,
                         docking_test.pipeline_runs
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void selectsStructuresWithSpherelessFpocketPockets() {
        assertThat(backfillService.findStructureIdsMissingSpheres(null))
                .containsExactly(structureId);
        assertThat(backfillService.findStructureIdsMissingSpheres(
                "AF-P99901-F1-model_v4"))
                .containsExactly(structureId);
        assertThat(backfillService.findStructureIdsMissingSpheres(
                "AF-OTHER-F1-model_v4"))
                .isEmpty();
    }

    @Test
    void backfillsSpheresFromVertFiles() throws IOException {
        AlphaSphereBackfillService.StructureBackfillResult result =
                backfillService.backfillStructure(structureId);

        assertThat(result.pocketsBackfilled()).isEqualTo(2);
        assertThat(result.spheresInserted()).isEqualTo(5);
        assertThat(result.alreadyHadSpheres()).isZero();
        assertThat(result.missingVertFiles()).isEmpty();
        assertThat(result.unparseableVertFiles()).isEmpty();
        assertThat(result.ambiguousArtifacts()).isEmpty();

        List<Number> indices = jdbc.queryForList("""
                SELECT sphere.sphere_index
                FROM docking_test.pocket_alpha_sphere sphere
                JOIN docking_test.pocket pocket
                    ON pocket.id = sphere.pocket_id
                WHERE pocket.pocket_number = 1
                ORDER BY sphere.sphere_index
                """, Number.class);
        assertThat(indices).hasSize(3);
        assertThat(countWhere("""
                        docking_test.pocket_alpha_sphere sphere
                        JOIN docking_test.pocket pocket
                            ON pocket.id = sphere.pocket_id
                        """,
                "pocket.pocket_number = 1"
                        + " AND sphere.sphere_index = 0"
                        + " AND abs(sphere.center_x - (-3.651)) < 1e-9"
                        + " AND abs(sphere.radius - 4.53) < 1e-9"))
                .isEqualTo(1);

        // Afterwards nothing is left to backfill.
        assertThat(backfillService.findStructureIdsMissingSpheres(null))
                .isEmpty();
    }

    @Test
    void backfillsOnlyPocketsMissingSpheres() throws IOException {
        // Pocket 2 keeps its spheres (re-add them), pocket 1 has none.
        jdbc.update("""
                INSERT INTO docking_test.pocket_alpha_sphere (
                    pocket_id, sphere_index,
                    center_x, center_y, center_z, radius
                )
                SELECT pocket.id, 0, 5.1, 6.2, 7.3, 3.9
                FROM docking_test.pocket pocket
                WHERE pocket.pocket_number = 2
                """);

        AlphaSphereBackfillService.StructureBackfillResult result =
                backfillService.backfillStructure(structureId);

        assertThat(result.pocketsBackfilled()).isEqualTo(1);
        assertThat(result.spheresInserted()).isEqualTo(3);
        assertThat(countWhere("""
                        docking_test.pocket_alpha_sphere sphere
                        JOIN docking_test.pocket pocket
                            ON pocket.id = sphere.pocket_id
                        """,
                "pocket.pocket_number = 2"))
                .isEqualTo(1);
    }

    @Test
    void reportsMissingVertFileAndContinues() throws IOException {
        // Point pocket 1's artifact at a location without a vert sibling.
        String bogus = tempDir.resolve("nowhere")
                .resolve("pocket1_atm.pdb")
                .toString();
        jdbc.update(
                "UPDATE docking_test.artifacts SET storage_location = ?"
                        + " WHERE filename = 'pocket1_atm.pdb'",
                bogus
        );

        AlphaSphereBackfillService.StructureBackfillResult result =
                backfillService.backfillStructure(structureId);

        assertThat(result.pocketsBackfilled()).isEqualTo(1);
        assertThat(result.spheresInserted()).isEqualTo(2);
        assertThat(result.missingVertFiles()).hasSize(1);
        assertThat(result.missingVertFiles().get(0))
                .endsWith("pocket1_vert.pqr");
    }

    @Test
    void reportsAmbiguousArtifactAndContinues() throws IOException {
        jdbc.update(
                "UPDATE docking_test.artifacts SET storage_location = ?"
                        + " WHERE filename = 'pocket1_atm.pdb'",
                "/somewhere/else.txt"
        );

        AlphaSphereBackfillService.StructureBackfillResult result =
                backfillService.backfillStructure(structureId);

        assertThat(result.pocketsBackfilled()).isEqualTo(1);
        assertThat(result.ambiguousArtifacts()).hasSize(1);
        assertThat(result.ambiguousArtifacts().get(0))
                .contains("/somewhere/else.txt");
    }

    private Path fixturePath(String resource) {
        try {
            return Path.of(getClass()
                    .getClassLoader()
                    .getResource(resource)
                    .toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long countWhere(String table, String where) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + where,
                Long.class
        );
        return count == null ? 0 : count;
    }
}
