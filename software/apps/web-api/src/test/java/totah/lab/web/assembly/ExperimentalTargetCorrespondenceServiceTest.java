package totah.lab.web.assembly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import totah.lab.hermes.file.mmcif.UniProtSequenceReference;
import totah.lab.web.service.DockingTestSchemaSupport;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentalTargetCorrespondenceServiceTest
        extends DockingTestSchemaSupport {
    static { recreateTestSchema(); }
    private JdbcTemplate jdbc;
    private ExperimentalTargetCorrespondenceService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        String base = System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db");
        dataSource.setUrl(base + (base.contains("?") ? "&" : "?")
                + "currentSchema=" + TEST_SCHEMA + ",public");
        dataSource.setUsername(System.getenv().getOrDefault("DB_USERNAME",
                "postgres"));
        dataSource.setPassword(System.getenv().getOrDefault("DB_PASSWORD",
                "admin"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE experimental_assembly CASCADE");
        jdbc.execute("TRUNCATE experimental_target_sequence CASCADE");
        addSource("QTESTA", "a.cif");
        addSource("QTESTB", "b.cif");
        addSource("QTESTC", "c.cif");
        ExperimentalTargetSequenceSourceLoader loader = path -> switch (
                path.toString()) {
            case "a.cif" -> reference("QTESTA", "AAAA");
            case "b.cif" -> reference("QTESTB", "AAAT");
            case "c.cif" -> reference("QTESTC", "WWWW");
            default -> List.of();
        };
        service = new ExperimentalTargetCorrespondenceService(jdbc, loader,
                accession -> java.util.Optional.empty(), TEST_SCHEMA);
    }

    @Test
    void persistsAcceptedAndLowConfidenceEvaluationsIdempotently()
            throws Exception {
        var first = service.build();
        var second = service.build();
        assertEquals(first, second);
        assertEquals(3, first.targets());
        assertEquals(0, first.unavailableTargets());
        assertEquals(1, first.acceptedAlignments());
        assertEquals(2, first.lowConfidenceAlignments());
        assertEquals(4, first.persistedPairs());
        assertEquals(3, count("experimental_target_sequence"));
        assertEquals(3, count("experimental_target_alignment"));
        assertEquals(4, count("experimental_target_alignment_pair"));
    }

    private void addSource(String accession, String path) {
        Long target = jdbc.queryForObject("INSERT INTO targets(name,uniprot_id)"
                + " VALUES (?,?) ON CONFLICT(uniprot_id) DO UPDATE SET "
                + "name=EXCLUDED.name RETURNING id", Long.class,
                accession, accession);
        Long assembly = jdbc.queryForObject("INSERT INTO experimental_assembly"
                + "(pdb_id,assembly_id) VALUES (?, '1') RETURNING id",
                Long.class, accession.substring(accession.length() - 4));
        Long artifact = jdbc.queryForObject("INSERT INTO assembly_artifact"
                + "(assembly_id,artifact_type,filename,storage_location) "
                + "VALUES (?,'ENTRY_MMCIF',?,?) RETURNING id", Long.class,
                assembly, path, path);
        jdbc.update("INSERT INTO assembly_residue_mapping_evaluation"
                + "(assembly_id,target_id,entry_artifact_id,evaluation_status,"
                + "method,method_version) VALUES (?,?,?,'EVALUATED','fixture','1')",
                assembly, target, artifact);
    }

    private static List<UniProtSequenceReference> reference(String accession,
            String sequence) {
        return List.of(new UniProtSequenceReference(accession, accession,
                sequence));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table,
                Integer.class);
    }
}
