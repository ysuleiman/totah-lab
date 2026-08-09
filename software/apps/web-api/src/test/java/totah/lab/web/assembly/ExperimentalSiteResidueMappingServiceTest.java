package totah.lab.web.assembly;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import totah.lab.hermes.file.mmcif.PolymerResidueMapping;
import totah.lab.web.service.DockingTestSchemaSupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentalSiteResidueMappingServiceTest
        extends DockingTestSchemaSupport {
    static { recreateTestSchema(); }

    private JdbcTemplate jdbc;
    private ExperimentalSiteResidueMappingService service;
    private ExperimentalSiteResidueMappingService.Candidate candidate;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        String baseUrl = System.getenv().getOrDefault("DB_URL",
                "jdbc:postgresql://localhost:5432/totah_lab_db");
        dataSource.setUrl(baseUrl + (baseUrl.contains("?") ? "&" : "?")
                + "currentSchema=" + TEST_SCHEMA + ",public");
        dataSource.setUsername(System.getenv().getOrDefault("DB_USERNAME",
                "postgres"));
        dataSource.setPassword(System.getenv().getOrDefault("DB_PASSWORD",
                "admin"));
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("SET search_path TO " + TEST_SCHEMA + ", public");
        jdbc.execute("TRUNCATE " + TEST_SCHEMA
                + ".experimental_assembly CASCADE");
        long target = id("INSERT INTO targets(name,uniprot_id) "
                + "VALUES ('test','QTEST1') ON CONFLICT (uniprot_id) "
                + "DO UPDATE SET name=EXCLUDED.name RETURNING id");
        long assembly = id("INSERT INTO experimental_assembly"
                + "(pdb_id,assembly_id) VALUES ('1ABC','1') RETURNING id");
        jdbc.update("INSERT INTO assembly_artifact(assembly_id,artifact_type,"
                + "filename,storage_location) VALUES "
                + "(?,'SOURCE_MMCIF','1ABC-assembly1.cif','assembly.cif')",
                assembly);
        long entity = id("INSERT INTO assembly_polymer_entity"
                + "(assembly_id,source_entity_id) VALUES (?, '1') RETURNING id",
                assembly);
        jdbc.update("INSERT INTO assembly_polymer_chain(polymer_entity_id,"
                + "label_asym_id,auth_asym_id) VALUES (?, 'A','A'),"
                + "(?,'A-2','A-2')", entity, entity);
        jdbc.update("INSERT INTO assembly_polymer_target(polymer_entity_id,"
                + "target_id,uniprot_accession,mapping_provenance,is_human) "
                + "VALUES (?,?,'QTEST1','fixture',true)", entity, target);
        long occurrence = id("INSERT INTO assembly_component_occurrence"
                + "(assembly_id,component_id,label_asym_id,auth_asym_id,"
                + "auth_sequence_id,alternate_location) "
                + "VALUES (?,'SAM','L','L','501','') RETURNING id", assembly);
        long site = id("INSERT INTO experimental_binding_site"
                + "(occurrence_id,site_number,localization_status,"
                + "ligand_centroid_x,ligand_centroid_y,ligand_centroid_z,"
                + "contributing_pocket_count,direct_contact_residue_count,"
                + "near_shell_residue_count,covered_ligand_atom_count,"
                + "contacted_ligand_atom_count,method,method_version,"
                + "grouping_rule,run_token) VALUES (?,1,'STRONG',0,0,0,"
                + "1,1,1,1,1,'fixture','1','{}',"
                + "'00000000-0000-0000-0000-000000000001') RETURNING id",
                occurrence);
        jdbc.update("INSERT INTO experimental_binding_site_target"
                + "(site_id,target_id) VALUES (?,?)", site, target);
        jdbc.update("INSERT INTO experimental_binding_site_residue"
                + "(site_id,auth_asym_id,residue_number,insertion_code,"
                + "residue_name,distance_band) VALUES "
                + "(?,'A',102,'','LYS','DIRECT'),"
                + "(?,'A-2',103,'','ALA','DIRECT'),"
                + "(?,'A',999,'','GLY','NEAR_SHELL')", site, site, site);
        ExperimentalResidueMappingSourceLoader loader = (entry, source) ->
                List.of(new PolymerResidueMapping("1", "A", "A", 2,
                        "102",
                        PolymerResidueMapping.ResidueNumberSource.AUTH_SEQ_NUM,
                        "", "LYS", "QTEST1", 10, "LYS",
                        PolymerResidueMapping.CoordinateStatus.RESOLVED,
                        PolymerResidueMapping.SequenceRelation.MATCH, null),
                        new PolymerResidueMapping("1", "A", "A", 3,
                                "103", PolymerResidueMapping
                                .ResidueNumberSource.AUTH_SEQ_NUM,
                                "", "ALA", "QTEST1", 11, "ALA",
                                PolymerResidueMapping.CoordinateStatus.RESOLVED,
                                PolymerResidueMapping.SequenceRelation.MATCH,
                                null));
        service = new ExperimentalSiteResidueMappingService(jdbc, loader,
                TEST_SCHEMA);
        candidate = new ExperimentalSiteResidueMappingService.Candidate(
                assembly, "1ABC", "1", target, "QTEST1",
                Path.of("assembly.cif"));
    }

    @Test
    void persistsMappedAndExplicitNoEvidenceStatesIdempotently(
            @TempDir Path temporaryDirectory) throws Exception {
        Path entry = temporaryDirectory.resolve("1ABC.cif");
        Files.writeString(entry, "data_fixture\n");

        var first = service.map(candidate, entry);
        var second = service.map(candidate, entry);

        assertEquals(first, second);
        assertEquals(3, first.requestedResidues());
        assertEquals(2, first.mappedResidues());
        assertEquals(1, first.unmappedResidues());
        assertEquals(3, count("assembly_residue_uniprot_mapping"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM "
                + "assembly_residue_uniprot_mapping WHERE auth_asym_id='A-2' "
                + "AND uniprot_position=11", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM "
                + "assembly_residue_uniprot_mapping WHERE "
                + "mapping_outcome='NOT_MAPPED' AND uniprot_position IS NULL",
                Integer.class));
        assertEquals("EVALUATED", jdbc.queryForObject("SELECT "
                + "evaluation_status FROM assembly_residue_mapping_evaluation",
                String.class));
    }

    @Test
    void recordsFailureWithoutCollapsingItIntoNoMapping(
            @TempDir Path temporaryDirectory) throws Exception {
        Path entry = temporaryDirectory.resolve("1ABC.cif");
        Files.writeString(entry, "data_fixture\n");

        service.recordFailure(candidate, entry,
                new IllegalStateException("broken mapping"));

        assertEquals("FAILED", jdbc.queryForObject("SELECT evaluation_status "
                + "FROM assembly_residue_mapping_evaluation", String.class));
        assertEquals(0, count("assembly_residue_uniprot_mapping"));
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table,
                Integer.class);
    }
}
