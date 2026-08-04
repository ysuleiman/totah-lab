package totah.lab.web.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the totah.import.min-pocket-residues filter with an explicit
 * threshold of 8 (the persistence default is 1: the 8-residue cutoff
 * applies only to search eligibility): the fixture's pocket1 has 8
 * residues and persists, its pocket2 has a single residue and is
 * skipped without leaving any rows.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=docking_test",
        "totah.artifacts.root=target/test-artifacts",
        "totah.import.min-pocket-residues=8"
})
class AlphaFoldPocketFilterIntegrationTest
        extends DockingTestSchemaSupport {

    static {
        recreateTestSchema();
    }

    @Autowired
    AlphaFoldPocketImportService importService;

    @Autowired
    JdbcTemplate jdbc;

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
    void skipsPocketBelowResidueThresholdButImportsStructure()
            throws IOException {

        AlphaFoldPocketImportService.ImportResult result =
                importService.importStructure(
                        fixturePath(
                                "import/AF-P99901-F1-model_v4.pdb.gz"),
                        fixturePath(
                                "import/AF-P99901-F1-model_v4_out")
                );

        assertThat(result.pockets()).isEqualTo(1);
        assertThat(result.skippedPockets()).isEqualTo(1);
        assertThat(result.structureResidues()).isEqualTo(12);

        // The structure itself and its residues are imported.
        assertThat(countWhere("docking_test.structure", "TRUE"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.residue", "TRUE"))
                .isEqualTo(12);

        // Only pocket 1 survives; pocket 2 leaves no rows anywhere.
        assertThat(countWhere("docking_test.pocket", "TRUE"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.pocket",
                "pocket_number = 1"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.pocket_residue", "TRUE"))
                .isEqualTo(8);
        assertThat(countWhere("docking_test.pocket_atom", "TRUE"))
                .isEqualTo(32);
        assertThat(countWhere("docking_test.pocket_alpha_sphere", "TRUE"))
                .isEqualTo(3);
        // No artifact for the skipped pocket (structure + pocket1 only).
        assertThat(countWhere("docking_test.artifacts", "TRUE"))
                .isEqualTo(2);
        assertThat(countWhere("docking_test.artifacts",
                "filename = 'pocket2_atm.pdb'"))
                .isZero();
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
