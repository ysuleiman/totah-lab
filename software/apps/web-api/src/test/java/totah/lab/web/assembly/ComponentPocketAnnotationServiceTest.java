package totah.lab.web.assembly;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import totah.lab.athena.pocket.component.PocketSphere;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.pocket.FpocketAtomObservation;
import totah.lab.web.service.DockingTestSchemaSupport;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentPocketAnnotationServiceTest extends DockingTestSchemaSupport {
    static { recreateTestSchema(); }

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db"));
        dataSource.setUsername(System.getenv().getOrDefault("DB_USERNAME", "postgres"));
        dataSource.setPassword(System.getenv().getOrDefault("DB_PASSWORD", "admin"));
        jdbc = new JdbcTemplate(dataSource);
        try (var connection = testConnection(); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE " + TEST_SCHEMA
                    + ".experimental_assembly CASCADE");
        }
    }

    @Test
    void annotatesCoordinatesOnceAndRerunsIdempotently() {
        TransactionTemplate transactions = new TransactionTemplate(
                new JdbcTransactionManager(dataSource));
        long assembly = transactions.execute(status -> {
            jdbc.execute("SET LOCAL search_path TO " + TEST_SCHEMA + ", public");
            Long id = jdbc.queryForObject("""
                    INSERT INTO experimental_assembly(pdb_id,assembly_id)
                    VALUES ('1N6A','1') RETURNING id
                    """, Long.class);
            jdbc.update("""
                    INSERT INTO assembly_artifact(assembly_id,artifact_type,
                        filename,storage_location)
                    VALUES (?, 'SOURCE_MMCIF','source.cif','source.cif'),
                           (?, 'FPOCKET_OUTPUT','out','out')
                    """, id, id);
            long artifact = jdbc.queryForObject("""
                    SELECT id FROM assembly_artifact
                    WHERE assembly_id=? AND artifact_type='FPOCKET_OUTPUT'
                    """, Long.class, id);
            jdbc.update("""
                    INSERT INTO assembly_pocket(assembly_id,artifact_id,
                        pocket_number,fpocket_rank) VALUES (?, ?, 1, 1),
                        (?, ?, 2, 2)
                    """, id, artifact, id, artifact);
            jdbc.update("""
                    INSERT INTO assembly_pocket_alpha_sphere
                        (pocket_id,sphere_number,x,y,z,radius)
                    SELECT id,1,CASE pocket_number WHEN 1 THEN 0 ELSE 5 END,
                           0,0,2 FROM assembly_pocket WHERE assembly_id=?
                    """, id);
            jdbc.update("""
                    INSERT INTO assembly_component_occurrence(assembly_id,
                        component_id,label_asym_id,auth_asym_id,
                        auth_sequence_id,alternate_location,model_number)
                    VALUES (?, 'SAM','C','C','501','',1),
                           (?, 'EDO','D','D','601','',1)
                    """, id, id);
            return id;
        });
        ComponentPocketSourceLoader loader = new FixtureLoader();
        var service = new ComponentPocketAnnotationService(jdbc,
                new ObjectMapper(), loader, TEST_SCHEMA);

        transactions.executeWithoutResult(status -> annotate(service, assembly));
        transactions.executeWithoutResult(status -> annotate(service, assembly));

        transactions.executeWithoutResult(status -> {
            jdbc.execute("SET LOCAL search_path TO " + TEST_SCHEMA + ", public");
            assertEquals(3, count("component_pocket_annotation"));
            assertEquals(3, count("assembly_component_atom"));
            assertEquals(2, count("assembly_pocket_atom"));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM component_pocket_annotation a
                    JOIN assembly_component_occurrence o ON o.id=a.occurrence_id
                    WHERE o.component_id='SAM'
                      AND a.relationship_class='OCCUPIES_POCKET'
                    """, Integer.class));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM component_pocket_annotation a
                    JOIN assembly_component_occurrence o ON o.id=a.occurrence_id
                    WHERE o.component_id='EDO'
                      AND a.relationship_class='NOT_ASSOCIATED'
                    """, Integer.class));
        });

        var analysis = new ExperimentalBindingSiteAnalysisService(jdbc,
                TEST_SCHEMA);
        var persistence = new ExperimentalBindingSitePersistenceService(jdbc,
                new ObjectMapper(), analysis, TEST_SCHEMA);
        var sam = analysis.meaningfulOccurrences(List.of(), List.of("SAM"))
                .getFirst();
        transactions.executeWithoutResult(status -> persistence.persist(sam));
        long stableSiteId = jdbc.queryForObject("SELECT id FROM " + TEST_SCHEMA
                + ".experimental_binding_site", Long.class);
        transactions.executeWithoutResult(status -> persistence.persist(sam));

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + TEST_SCHEMA
                + ".experimental_binding_site", Integer.class));
        assertEquals(stableSiteId, jdbc.queryForObject("SELECT id FROM "
                + TEST_SCHEMA + ".experimental_binding_site", Long.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM " + TEST_SCHEMA
                + ".experimental_binding_site_candidate WHERE disposition='CONTRIBUTING'",
                Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM " + TEST_SCHEMA
                + ".experimental_binding_site_pocket_pair", Integer.class));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static void annotate(ComponentPocketAnnotationService service, long id) {
        try { service.annotate(id); }
        catch (Exception exception) { throw new RuntimeException(exception); }
    }

    private static BoundComponentAtom atom(String id, String element, double x) {
        return new BoundComponentAtom(id, id, element, new Point3D(x, 0, 0),
                1.0, 10.0, null, null, id);
    }

    private static final class FixtureLoader implements ComponentPocketSourceLoader {
        @Override
        public List<BoundComponentOccurrence> components(Path path, String pdb,
                String assembly) {
            return List.of(new BoundComponentOccurrence(pdb,
                            BoundComponentOccurrence.SourceKind.ASSEMBLY,
                            assembly, 1, "SAM", "C", null, "C", "501",
                            null, List.of(atom("C1", "C", 0),
                                    atom("N1", "N", 1))),
                    new BoundComponentOccurrence(pdb,
                            BoundComponentOccurrence.SourceKind.ASSEMBLY,
                            assembly, 1, "EDO", "D", null, "D", "601",
                            null, List.of(atom("C2", "C", 30))));
        }

        @Override
        public List<PocketSource> pockets(Path output) {
            return List.of(new PocketSource(1, List.of(
                    new FpocketAtomObservation("1", "CA", "C", "A", 10,
                            null, "ALA", new Point3D(3, 0, 0))),
                    List.of(new PocketSphere(new Point3D(0, 0, 0), 2))),
                    new PocketSource(2, List.of(
                            new FpocketAtomObservation("2", "CB", "C", "A", 11,
                                    null, "VAL", new Point3D(4, 0, 0))),
                            List.of(new PocketSphere(new Point3D(5, 0, 0), 2))));
        }
    }
}
