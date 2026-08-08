package totah.lab.web.assembly;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.hermes.file.mmcif.AssemblyChain;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.mmcif.EntryExperimentalMetadata;
import totah.lab.hermes.file.mmcif.StructureReference;
import totah.lab.web.service.DockingTestSchemaSupport;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentalAssemblyImportServiceTest extends DockingTestSchemaSupport {
    static { recreateTestSchema(); }

    @BeforeEach
    void clearAssemblyFixtures() throws Exception {
        try (var connection = testConnection();
             var statement = connection.createStatement()) {
            statement.execute("TRUNCATE " + TEST_SCHEMA
                    + ".experimental_assembly CASCADE");
        }
    }

    @Test
    void storesKnownMultiTargetAssemblyWithoutDuplicatingPockets() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db"));
        dataSource.setUsername(System.getenv().getOrDefault("DB_USERNAME",
                "postgres"));
        dataSource.setPassword(System.getenv().getOrDefault("DB_PASSWORD",
                "admin"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ExperimentalAssemblySourceLoader loader = request -> fixture();
        ExperimentalAssemblyImportService service =
                new ExperimentalAssemblyImportService(jdbc,
                        new ObjectMapper(), loader, TEST_SCHEMA);
        TransactionTemplate transactions = new TransactionTemplate(
                new JdbcTransactionManager(dataSource));
        var request = new ExperimentalAssemblyImportService.ImportRequest(
                "5IL0", "1", Path.of("5IL0.cif"),
                Path.of("5IL0-assembly1.cif"), "a".repeat(64),
                Path.of("5IL0-assembly1_out"), "4.0", "fpocket -f ...",
                Instant.parse("2026-08-09T00:00:00Z"),
                Instant.parse("2026-08-09T00:01:00Z"));

        transactions.executeWithoutResult(status -> importUnchecked(service, request));
        transactions.executeWithoutResult(status -> importUnchecked(service, request));

        transactions.executeWithoutResult(status -> {
        jdbc.execute("SET LOCAL search_path TO " + TEST_SCHEMA + ", public");
        assertEquals(1, count(jdbc, "experimental_assembly"));
        assertEquals(2, count(jdbc, "assembly_polymer_entity"));
        assertEquals(2, count(jdbc, "assembly_polymer_chain"));
        assertEquals(2, count(jdbc, "assembly_target"));
        assertEquals(3, count(jdbc, "assembly_pocket"));
        assertEquals(1, countWhere(jdbc, """
                SELECT count(*) FROM (
                  SELECT p.id FROM assembly_pocket p
                  JOIN assembly_pocket_target pt ON pt.pocket_id=p.id
                  GROUP BY p.id HAVING count(DISTINCT pt.target_id)=1
                ) x
                """));
        assertEquals(1, countWhere(jdbc, """
                SELECT count(*) FROM (
                  SELECT p.id FROM assembly_pocket p
                  JOIN assembly_pocket_target pt ON pt.pocket_id=p.id
                  GROUP BY p.id HAVING count(DISTINCT pt.target_id)>1
                ) x
                """));
        assertEquals(1, countWhere(jdbc, """
                SELECT count(*) FROM assembly_pocket p
                WHERE NOT EXISTS (SELECT 1 FROM assembly_pocket_target pt
                                  WHERE pt.pocket_id=p.id)
                """));
        assertEquals(1, countWhere(jdbc, """
                SELECT count(*) FROM assembly_component_occurrence
                WHERE component_id='SAM'
                """));
        assertEquals(0, countWhere(jdbc,
                "SELECT count(*) FROM targets WHERE uniprot_id='SAM'"));
        assertEquals(0, countWhere(jdbc, """
                SELECT count(*) FROM (
                  SELECT assembly_id, pocket_number, count(*)
                  FROM assembly_pocket GROUP BY assembly_id, pocket_number
                  HAVING count(*) > 1
                ) duplicates
                """));
        });
    }

    @Test
    void mapsSingleTargetAssemblyToOneSourceAndPocketAssociation() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ExperimentalAssemblySourceLoader loader = request ->
                new ExperimentalAssemblySourceLoader.ParsedAssembly(
                        new EntryExperimentalMetadata("X-RAY DIFFRACTION", 1.5),
                        List.of(new StructureReference("1", "Q8WTS6",
                                "SET7_HUMAN", "Homo sapiens", "9606",
                                "Histone-lysine N-methyltransferase SETD7",
                                List.of("A"))),
                        List.of(new AssemblyChain("1", "A", "A", 1)),
                        List.of(), List.of(pocket(1,
                                List.of(new ResidueId("A", 42, null)))));
        ExperimentalAssemblyImportService service =
                new ExperimentalAssemblyImportService(jdbc,
                        new ObjectMapper(), loader, TEST_SCHEMA);
        TransactionTemplate transactions = new TransactionTemplate(
                new JdbcTransactionManager(dataSource));
        var request = new ExperimentalAssemblyImportService.ImportRequest(
                "1N6A", "1", Path.of("1N6A.cif"),
                Path.of("1N6A-assembly1.cif"), "b".repeat(64),
                Path.of("1N6A-assembly1_out"), "4.0", "fpocket -f ...",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(30));

        transactions.executeWithoutResult(status -> importUnchecked(service, request));

        transactions.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL search_path TO " + TEST_SCHEMA + ", public");
            assertEquals(1, countWhere(jdbc, """
                    SELECT count(*) FROM assembly_pocket_target pt
                    JOIN assembly_pocket p ON p.id=pt.pocket_id
                    JOIN experimental_assembly a ON a.id=p.assembly_id
                    WHERE a.pdb_id='1N6A'
                    """));
            assertEquals(1, countWhere(jdbc, """
                    SELECT count(*) FROM assembly_target at
                    JOIN experimental_assembly a ON a.id=at.assembly_id
                    WHERE a.pdb_id='1N6A'
                    """));
        });
    }

    private static ExperimentalAssemblySourceLoader.ParsedAssembly fixture() {
        return new ExperimentalAssemblySourceLoader.ParsedAssembly(
                new EntryExperimentalMetadata("X-RAY DIFFRACTION", 1.80),
                List.of(
                        new StructureReference("1", "Q86U44", "MTA70_HUMAN",
                                "Homo sapiens", "9606", "METTL3", List.of("A")),
                        new StructureReference("2", "Q9HCE5", "MET14_HUMAN",
                                "Homo sapiens", "9606", "METTL14", List.of("B"))),
                List.of(new AssemblyChain("1", "A", "A", 1),
                        new AssemblyChain("2", "B", "B", 1)),
                List.of(new BoundComponentOccurrence("5IL0",
                        BoundComponentOccurrence.SourceKind.ASSEMBLY, "1", 1,
                        "SAM", "C", null, "C", "501", null, List.of())),
                List.of(pocket(1, List.of(new ResidueId("A", 10, null))),
                        pocket(2, List.of(new ResidueId("A", 11, null),
                                new ResidueId("B", 20, null))),
                        pocket(3, List.of(new ResidueId("X", 30, null)))));
    }

    private static DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db"));
        dataSource.setUsername(System.getenv().getOrDefault("DB_USERNAME",
                "postgres"));
        dataSource.setPassword(System.getenv().getOrDefault("DB_PASSWORD",
                "admin"));
        return dataSource;
    }

    private static Pocket pocket(int number, List<ResidueId> residues) {
        return new Pocket(PocketId.of(number), "Pocket " + number,
                PocketSource.FPOCKET, new Point3D(1, 2, 3), residues,
                List.of(new PocketMetric(PocketMetricType.FPOCKET_SCORE, 0.5),
                        new PocketMetric(PocketMetricType.VOLUME, 100.0)),
                Optional.empty(), Optional.of(new AlphaSphereSet(List.of(
                        new AlphaSphere(number, new Point3D(1, 2, 3), 1.5)))),
                Map.of());
    }

    private static void importUnchecked(ExperimentalAssemblyImportService service,
            ExperimentalAssemblyImportService.ImportRequest request) {
        try { service.importAssembly(request); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return countWhere(jdbc, "SELECT count(*) FROM " + table);
    }

    private static int countWhere(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
