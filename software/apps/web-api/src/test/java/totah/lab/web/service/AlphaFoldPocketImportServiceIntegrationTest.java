package totah.lab.web.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import totah.lab.web.persistence.ReceptorEntity;
import totah.lab.web.persistence.ReceptorRepository;
import totah.lab.web.persistence.SchemaRemappingPhysicalNamingStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration test for {@link AlphaFoldPocketImportService} against a
 * throwaway {@code docking_test} schema cloned from the live DDL. The
 * schema is created before the Spring context boots (ddl-auto=validate
 * must find it) and dropped afterwards. The public-side tables
 * (targets, pipeline_runs) are remapped into the test schema via
 * {@link SchemaRemappingPhysicalNamingStrategy}.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.default_schema=docking_test",
        "totah.artifacts.root=target/test-artifacts"
})
class AlphaFoldPocketImportServiceIntegrationTest {

    private static final String TEST_SCHEMA = "docking_test";
    private static final String ACCESSION = "P99901";
    private static final String STRUCTURE_ACCESSION =
            "AF-P99901-F1-model_v4";

    static {
        System.setProperty(
                SchemaRemappingPhysicalNamingStrategy
                        .PUBLIC_SCHEMA_PROPERTY,
                TEST_SCHEMA
        );
        recreateTestSchema();
    }

    @TempDir
    Path tempDir;

    @Autowired
    AlphaFoldPocketImportService importService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ReceptorRepository receptorRepository;

    @AfterEach
    void truncateTestSchema() {
        jdbc.execute("""
                TRUNCATE docking_test.pocket_atom,
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

    @AfterAll
    static void dropTestSchema() throws SQLException {
        try (Connection connection = testConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "DROP SCHEMA IF EXISTS docking_test CASCADE"
            );
        } finally {
            System.clearProperty(
                    SchemaRemappingPhysicalNamingStrategy
                            .PUBLIC_SCHEMA_PROPERTY
            );
        }
    }

    // 1. AlphaFold filename parsing -------------------------------------

    @Test
    void rejectsUnexpectedFilenames() {
        assertThatThrownBy(() -> importService.importStructure(
                Path.of("AF-P99901-F1-model_v4.pdb"),
                fixtureOutDirectory()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(".pdb.gz");

        assertThatThrownBy(() -> importService.importStructure(
                Path.of("P99901.pdb.gz"),
                fixtureOutDirectory()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid AlphaFold filename");

        assertThatThrownBy(() -> importService.importStructure(
                Path.of("AF-P99901-model_v4.pdb.gz"),
                fixtureOutDirectory()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid AlphaFold filename");
    }

    @Test
    void parsesAccessionUniprotAndModelVersionFromFilename()
            throws IOException {

        AlphaFoldPocketImportService.ImportResult result = importFixture();

        assertThat(countWhere("docking_test.structure",
                "source = 'ALPHAFOLD'"
                        + " AND source_accession = '" + STRUCTURE_ACCESSION
                        + "' AND model_number = 4"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.receptor",
                "uniprot_id = '" + ACCESSION + "'"))
                .isEqualTo(1);
        assertThat(result.structureResidues()).isEqualTo(4);
    }

    // 2. Insertion-code normalization ------------------------------------

    @Test
    void normalizesInsertionCodes() throws IOException {
        importFixture();

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT chain, residue_number, insertion_code, residue_name
                FROM docking_test.residue
                WHERE residue_number = 10
                ORDER BY insertion_code
                """);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("insertion_code")).isEqualTo("");
        assertThat(rows.get(1).get("insertion_code")).isEqualTo("A");
    }

    // 3. Creating receptor, structure, residues, pockets -----------------

    @Test
    void createsReceptorStructureResiduesAndPockets() throws IOException {
        AlphaFoldPocketImportService.ImportResult result = importFixture();

        assertThat(result.pockets()).isEqualTo(2);
        assertThat(result.pocketResidues()).isEqualTo(3);
        assertThat(result.pocketAtoms()).isEqualTo(13);

        Map<String, Object> receptor = queryOne("""
                SELECT target_name, uniprot_id, organism
                FROM docking_test.receptor
                """);
        assertThat(receptor.get("target_name")).isEqualTo(ACCESSION);
        assertThat(receptor.get("uniprot_id")).isEqualTo(ACCESSION);
        assertThat(receptor.get("organism")).isEqualTo("Homo sapiens");

        Map<String, Object> structure = queryOne("""
                SELECT source, source_accession, chain, model_number,
                       preparation_state
                FROM docking_test.structure
                """);
        assertThat(structure.get("source")).isEqualTo("ALPHAFOLD");
        assertThat(structure.get("source_accession"))
                .isEqualTo(STRUCTURE_ACCESSION);
        assertThat(structure.get("chain")).isEqualTo("A");
        assertThat(structure.get("model_number")).isEqualTo(4);
        assertThat(structure.get("preparation_state")).isEqualTo("RAW");

        assertThat(countWhere("docking_test.residue", "TRUE"))
                .isEqualTo(4);

        Map<String, Object> pocket = queryOne("""
                SELECT source::text AS source, pocket_number, fpocket_file,
                       volume, score, druggability_score, probability
                FROM docking_test.pocket
                WHERE pocket_number = 1
                """);
        assertThat(pocket.get("source")).isEqualTo("FPOCKET");
        assertThat(pocket.get("fpocket_file"))
                .isEqualTo("pocket1_atm.pdb");
        assertThat((Double) pocket.get("volume"))
                .isCloseTo(123.456, within(1e-6));
        assertThat((Double) pocket.get("score"))
                .isCloseTo(1.234, within(1e-6));
        assertThat((Double) pocket.get("druggability_score"))
                .isCloseTo(0.456, within(1e-6));
        assertThat(pocket.get("probability")).isNull();
    }

    // 4. Reusing an existing receptor -------------------------------------

    @Test
    void reusesExistingReceptor() throws IOException {
        ReceptorEntity existing = new ReceptorEntity();
        existing.setUniProtId(ACCESSION);
        existing.setTargetName("METTL7X");
        existing = receptorRepository.saveAndFlush(existing);

        AlphaFoldPocketImportService.ImportResult result = importFixture();

        assertThat(result.receptorId()).isEqualTo(existing.getId());
        assertThat(countWhere("docking_test.receptor", "TRUE"))
                .isEqualTo(1);
        assertThat(queryOne(
                        "SELECT target_name FROM docking_test.receptor")
                .get("target_name"))
                .isEqualTo("METTL7X");
    }

    // 5. Reimport without duplicates (children replaced) ------------------

    @Test
    void reimportDoesNotDuplicateResiduesOrPockets() throws IOException {
        importFixture();
        AlphaFoldPocketImportService.ImportResult second = importFixture();

        assertThat(second.structureResidues()).isEqualTo(4);
        assertThat(countWhere("docking_test.receptor", "TRUE")).isEqualTo(1);
        assertThat(countWhere("docking_test.structure", "TRUE"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.residue", "TRUE")).isEqualTo(4);
        assertThat(countWhere("docking_test.pocket", "TRUE")).isEqualTo(2);
        assertThat(countWhere("docking_test.pocket_residue", "TRUE"))
                .isEqualTo(3);
        assertThat(countWhere("docking_test.pocket_atom", "TRUE"))
                .isEqualTo(13);
    }

    // 6. Resolving pocket residues to canonical residues ------------------

    @Test
    void resolvesPocketResiduesToCanonicalResidues() throws IOException {
        importFixture();

        List<Map<String, Object>> memberships = jdbc.queryForList("""
                SELECT membership.chain,
                       membership.residue_number,
                       membership.residue_name,
                       residue.structure_id,
                       residue.insertion_code
                FROM docking_test.pocket_residue membership
                JOIN docking_test.residue residue
                    ON residue.id = membership.residue_id
                JOIN docking_test.pocket pocket
                    ON pocket.id = membership.pocket_id
                WHERE pocket.pocket_number = 1
                ORDER BY membership.residue_number
                """);

        assertThat(memberships).hasSize(2);

        Map<String, Object> ala = memberships.get(0);
        assertThat(ala.get("chain")).isEqualTo("A");
        assertThat(ala.get("residue_number")).isEqualTo(1);
        assertThat(ala.get("residue_name")).isEqualTo("ALA");
        assertThat(ala.get("structure_id")).isNotNull();

        // The insertion-code residue GLY 10A resolves to the canonical
        // (A, 10, 'A') row, not to the plain GLY 10 row.
        Map<String, Object> gly = memberships.get(1);
        assertThat(gly.get("residue_number")).isEqualTo(10);
        assertThat(gly.get("residue_name")).isEqualTo("GLY");
        assertThat(gly.get("insertion_code")).isEqualTo("A");
    }

    // 7. Failure when a pocket references a missing residue ---------------

    @Test
    void failsWhenPocketResidueIsMissingFromStructure()
            throws IOException {

        Path out = copyOutDirectory();
        appendAtomLine(out, atomLine(99, "CA", "GLY", "A", 999, " "));

        assertThatThrownBy(() -> importService.importStructure(
                fixtureCompressedPdb(),
                out
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not part of structure")
                .hasMessageContaining(STRUCTURE_ACCESSION);
    }

    // 8. Transaction rollback on failure ----------------------------------

    @Test
    void rollsBackAllRowsWhenImportFails() {
        assertThatThrownBy(() -> {
            try {
                Path out = copyOutDirectory();
                appendAtomLine(
                        out,
                        atomLine(99, "CA", "GLY", "A", 999, " ")
                );
                importService.importStructure(
                        fixtureCompressedPdb(),
                        out
                );
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }).isInstanceOf(IllegalStateException.class);

        for (String table : List.of(
                "pocket_atom",
                "pocket_residue",
                "pocket",
                "residue",
                "structure",
                "artifacts",
                "receptor",
                "targets",
                "pipeline_runs"
        )) {
            assertThat(countWhere(TEST_SCHEMA + "." + table, "TRUE"))
                    .as("no partial rows in %s", table)
                    .isZero();
        }
    }

    // 9. Correct artifact association -------------------------------------

    @Test
    void associatesArtifactsWithLabelLocationRunAndTarget()
            throws IOException {

        importFixture();

        List<Map<String, Object>> artifacts = jdbc.queryForList("""
                SELECT a.label, a.filename, a.storage_location,
                       a.pipeline_run_id, a.target_id
                FROM docking_test.artifacts a
                ORDER BY a.label, a.filename
                """);

        assertThat(artifacts).hasSize(3);

        Map<String, Object> structure = artifacts.stream()
                .filter(row -> "RAW_PDB_FILE".equals(row.get("label")))
                .findFirst()
                .orElseThrow();
        assertThat(structure.get("storage_location"))
                .isEqualTo(fixtureCompressedPdb().toString());

        List<Map<String, Object>> pocketArtifacts = artifacts.stream()
                .filter(row -> "FPOCKET_POCKET".equals(row.get("label")))
                .toList();
        assertThat(pocketArtifacts).hasSize(2);
        assertThat(pocketArtifacts)
                .allSatisfy(row -> assertThat(
                                row.get("storage_location").toString())
                        .endsWith("_atm.pdb")
                        .contains("pockets"));

        Object targetId = artifacts.get(0).get("target_id");
        Object pipelineRunId = artifacts.get(0).get("pipeline_run_id");
        assertThat(artifacts)
                .allSatisfy(row -> {
                    assertThat(row.get("target_id")).isEqualTo(targetId);
                    assertThat(row.get("pipeline_run_id"))
                            .isEqualTo(pipelineRunId);
                });

        Map<String, Object> target = queryOne("""
                SELECT uniprot_id FROM docking_test.targets
                """);
        assertThat(target.get("uniprot_id")).isEqualTo(ACCESSION);

        Map<String, Object> run = queryOne("""
                SELECT status FROM docking_test.pipeline_runs
                """);
        assertThat(run.get("status")).isEqualTo("FINISHED");

        // Reimport reuses target, run and artifacts.
        importFixture();
        assertThat(countWhere("docking_test.targets", "TRUE"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.pipeline_runs", "TRUE"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.artifacts", "TRUE"))
                .isEqualTo(3);
    }

    // 10. Cascade/orphan behavior when replacing memberships ---------------

    @Test
    void reimportReplacesPocketMembershipsAndAtoms() throws IOException {
        importFixture();

        // Second import: pocket 1 loses its ALA 1 membership.
        Path out = copyOutDirectory();
        Path pocketFile = out.resolve("pockets")
                .resolve("pocket1_atm.pdb");
        String content = Files.readString(pocketFile);
        String reduced = content.lines()
                .filter(line -> !line.contains(" ALA "))
                .reduce("", (left, right) -> left + right + "\n");
        Files.writeString(pocketFile, reduced);

        importService.importStructure(fixtureCompressedPdb(), out);

        // No orphans from the first import survive.
        assertThat(countWhere("docking_test.pocket_residue", "TRUE"))
                .isEqualTo(2);
        assertThat(countWhere("docking_test.pocket_atom", "TRUE"))
                .isEqualTo(9);
        assertThat(countWhere(
                "docking_test.pocket_residue",
                "residue_name = 'ALA'"))
                .isZero();
        assertThat(countWhere(
                "docking_test.pocket_residue",
                "residue_name = 'GLY' AND residue_number = 10"))
                .isEqualTo(1);
    }

    // Zero-pocket structures ------------------------------------------------

    @Test
    void importsStructureWithoutPocketsWhenFpocketFindsNone()
            throws IOException {

        Path out = Files.createTempDirectory("zero-pocket-out");
        Files.createDirectory(out.resolve("pockets"));
        Files.writeString(
                out.resolve(STRUCTURE_ACCESSION + "_info.txt"),
                "fpocket header only, no pocket sections\n"
        );

        AlphaFoldPocketImportService.ImportResult result =
                importService.importStructure(fixtureCompressedPdb(), out);

        assertThat(result.pockets()).isZero();
        assertThat(result.pocketResidues()).isZero();
        assertThat(result.pocketAtoms()).isZero();
        assertThat(countWhere("docking_test.structure", "TRUE"))
                .isEqualTo(1);
        assertThat(countWhere("docking_test.residue", "TRUE"))
                .isEqualTo(4);
        assertThat(countWhere("docking_test.pocket", "TRUE")).isZero();
    }

    // helpers ---------------------------------------------------------------

    private AlphaFoldPocketImportService.ImportResult importFixture()
            throws IOException {

        return importService.importStructure(
                fixtureCompressedPdb(),
                fixtureOutDirectory()
        );
    }

    private Path fixtureCompressedPdb() {
        return fixturePath("import/" + STRUCTURE_ACCESSION + ".pdb.gz");
    }

    private Path fixtureOutDirectory() {
        return fixturePath("import/" + STRUCTURE_ACCESSION + "_out");
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

    private Path copyOutDirectory() throws IOException {
        Path source = fixtureOutDirectory();
        Path target = tempDir.resolve(source.getFileName().toString());

        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(
                        source.relativize(path).toString()
                );
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
        return target;
    }

    private void appendAtomLine(Path outDirectory, String line)
            throws IOException {

        Path pocketFile = outDirectory
                .resolve("pockets")
                .resolve("pocket1_atm.pdb");
        String content = Files.readString(pocketFile);
        Files.writeString(
                pocketFile,
                content.replaceFirst("TER", line + "\nTER")
        );
    }

    private static String atomLine(
            int serial,
            String name,
            String residueName,
            String chain,
            int residueNumber,
            String insertionCode
    ) {
        return String.format(
                "ATOM  %5d %-4s %3s %1s%4d%1s   "
                        + "%8.3f%8.3f%8.3f%6.2f%6.2f          %-2s",
                serial,
                name,
                residueName,
                chain,
                residueNumber,
                insertionCode,
                1.0,
                2.0,
                3.0,
                1.00,
                0.00,
                "C"
        );
    }

    private Map<String, Object> queryOne(String sql) {
        return jdbc.queryForMap(sql);
    }

    private long countWhere(String table, String where) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + where,
                Long.class
        );
        return count == null ? 0 : count;
    }

    private static void recreateTestSchema() {
        String ddl;
        try (InputStream in =
                     AlphaFoldPocketImportServiceIntegrationTest.class
                             .getResourceAsStream(
                                     "/docking_test_schema.sql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "docking_test_schema.sql not on the classpath"
                );
            }
            ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }

        try (Connection connection = testConnection();
             Statement statement = connection.createStatement()) {
            for (String command : ddl.split(";")) {
                if (!command.isBlank()) {
                    statement.execute(command);
                }
            }
        } catch (SQLException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Connection testConnection() throws SQLException {
        String url = System.getenv().getOrDefault(
                "DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db"
        );
        String user = System.getenv().getOrDefault(
                "DB_USERNAME",
                "postgres"
        );
        String password = System.getenv().getOrDefault(
                "DB_PASSWORD",
                "admin"
        );
        return DriverManager.getConnection(url, user, password);
    }
}
