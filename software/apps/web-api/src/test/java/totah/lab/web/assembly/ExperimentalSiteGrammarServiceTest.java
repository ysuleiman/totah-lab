package totah.lab.web.assembly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import totah.lab.web.service.DockingTestSchemaSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentalSiteGrammarServiceTest extends DockingTestSchemaSupport {
    static { recreateTestSchema(); }
    private JdbcTemplate jdbc;
    private ExperimentalSiteGrammarService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        String base = System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db");
        source.setUrl(base + (base.contains("?") ? "&" : "?")
                + "currentSchema=" + TEST_SCHEMA + ",public");
        source.setUsername(System.getenv().getOrDefault("DB_USERNAME",
                "postgres"));
        source.setPassword(System.getenv().getOrDefault("DB_PASSWORD",
                "admin"));
        jdbc = new JdbcTemplate(source);
        jdbc.execute("TRUNCATE experimental_target_alignment CASCADE");
        addAlignment("ACCEPTED", true);
        addAlignment("LOW_CONFIDENCE", false);
        service = new ExperimentalSiteGrammarService(jdbc,
                path -> java.util.List.of(), TEST_SCHEMA);
    }

    @Test
    void persistsOrthogonalAcceptedGrammarAndSummariesIdempotently()
            throws Exception {
        var first = service.derive();
        var second = service.derive();
        assertEquals(first, second);
        assertEquals(1, first.acceptedPairsWithSiteGrammar());
        assertEquals(2, first.residueGrammarRows());
        assertEquals(2, first.experimentallySupportedRows());
        assertEquals(0, first.lowConfidenceResidueRows());
        assertEquals(2, count("experimental_site_grammar_residue"));
        assertEquals(1, count("experimental_site_grammar_summary"));
        assertEquals(0.5, jdbc.queryForObject("SELECT "
                + "exact_identity_fraction FROM "
                + "experimental_site_grammar_summary", Double.class),
                1.0e-12);
        assertEquals(1.0, jdbc.queryForObject("SELECT "
                + "chemistry_match_fraction FROM "
                + "experimental_site_grammar_summary", Double.class),
                1.0e-12);
        assertEquals("CONSERVATIVE", jdbc.queryForObject("SELECT "
                + "chemistry_relationship FROM "
                + "experimental_site_grammar_residue WHERE query_residue='D'",
                String.class));
        assertEquals("DIRECT", jdbc.queryForObject("SELECT "
                + "query_contact_role FROM experimental_site_grammar_residue "
                + "WHERE query_residue='D'", String.class));
        assertEquals("NEAR_SHELL", jdbc.queryForObject("SELECT "
                + "candidate_contact_role FROM "
                + "experimental_site_grammar_residue WHERE query_residue='D'",
                String.class));
    }

    private void addAlignment(String status, boolean contact) {
        Long first = target(status + "A");
        Long second = target(status + "B");
        long low = Math.min(first, second);
        long high = Math.max(first, second);
        Long alignment = jdbc.queryForObject("""
                INSERT INTO experimental_target_alignment
                  (query_target_id,candidate_target_id,identity,query_coverage,
                   candidate_coverage,aligned_pair_count,evaluation_status,
                   correspondence_status,method,method_version,
                   identity_threshold,coverage_threshold)
                VALUES (?,?,.5,1,1,2,'EVALUATED',?,'fixture','2',.3,.7)
                RETURNING id
                """, Long.class, low, high, status);
        jdbc.update("""
                INSERT INTO experimental_target_alignment_pair
                  (alignment_id,query_uniprot_position,
                   candidate_uniprot_position,query_residue,
                   candidate_residue,substitution_score,
                   query_direct_contact,candidate_direct_contact,
                   query_near_shell,candidate_near_shell)
                VALUES (?,10,20,'D','E',2,?,false,false,?),
                       (?,11,21,'A','A',2,false,?,true,false)
                """, alignment, contact, contact, alignment, contact);
    }

    private Long target(String accession) {
        return jdbc.queryForObject("INSERT INTO targets(name,uniprot_id) "
                + "VALUES (?,?) RETURNING id", Long.class, accession,
                accession);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table,
                Integer.class);
    }
}
