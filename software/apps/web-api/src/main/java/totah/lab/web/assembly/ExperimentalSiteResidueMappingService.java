package totah.lab.web.assembly;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.hermes.file.mmcif.PolymerResidueMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Persists residue correspondence for single-target SAM/SAH/SFG sites. */
@Service
public class ExperimentalSiteResidueMappingService {
    public static final String METHOD =
            "RCSB_MMCIF_STRUCT_REF_SEQ_PLUS_POLY_SEQ_SCHEME";
    public static final String METHOD_VERSION = "1";

    private final JdbcTemplate jdbc;
    private final ExperimentalResidueMappingSourceLoader sourceLoader;
    private final String schema;

    public ExperimentalSiteResidueMappingService(JdbcTemplate jdbc,
            ExperimentalResidueMappingSourceLoader sourceLoader,
            @Value("${totah.persistence.docking-schema:docking}") String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.sourceLoader = Objects.requireNonNull(sourceLoader);
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid persistence schema: "
                    + schema);
        }
        this.schema = schema;
    }

    @Transactional(readOnly = true)
    public List<Candidate> candidates() {
        useSchema();
        return jdbc.query("""
                SELECT DISTINCT a.id,a.pdb_id,a.assembly_id,t.id,t.uniprot_id,
                       source.storage_location
                FROM experimental_binding_site s
                JOIN assembly_component_occurrence o ON o.id=s.occurrence_id
                JOIN experimental_assembly a ON a.id=o.assembly_id
                JOIN experimental_binding_site_target st ON st.site_id=s.id
                JOIN targets t ON t.id=st.target_id
                JOIN assembly_artifact source ON source.assembly_id=a.id
                 AND source.artifact_type='SOURCE_MMCIF'
                WHERE o.component_id IN ('SAM','SAH','SFG')
                  AND (SELECT count(*) FROM experimental_binding_site_target x
                       WHERE x.site_id=s.id)=1
                ORDER BY a.pdb_id,a.assembly_id,t.uniprot_id
                """, (rs, row) -> new Candidate(rs.getLong(1), rs.getString(2),
                rs.getString(3), rs.getLong(4), rs.getString(5),
                Path.of(rs.getString(6))));
    }

    @Transactional(rollbackFor = IOException.class)
    public MappingResult map(Candidate candidate, Path entryMmcif)
            throws IOException {
        Objects.requireNonNull(candidate);
        Objects.requireNonNull(entryMmcif);
        useSchema();
        long artifact = upsertEntryArtifact(candidate.assemblyDatabaseId(),
                entryMmcif);
        List<PolymerResidueMapping> sourceMappings = sourceLoader.load(
                entryMmcif, candidate.assemblyMmcif());
        List<SiteResidue> residues = siteResidues(candidate);
        jdbc.update("DELETE FROM assembly_residue_uniprot_mapping "
                + "WHERE assembly_id=? AND target_id=?",
                candidate.assemblyDatabaseId(), candidate.targetId());
        int mapped = 0;
        int unresolved = 0;
        int ambiguous = 0;
        for (SiteResidue residue : residues) {
            ChainRef chain = chain(candidate, residue.authAsymId());
            List<PolymerResidueMapping> matches = equivalentMappings(
                    sourceMappings.stream()
                    .filter(mapping -> candidate.uniProtAccession()
                            .equalsIgnoreCase(mapping.uniProtAccession()))
                    .filter(mapping -> chain == null
                            ? residue.authAsymId().equals(mapping.authAsymId())
                            : chain.entityId().equals(mapping.entityId()))
                    .filter(mapping -> Integer.toString(residue.residueNumber())
                            .equals(mapping.authSequenceId()))
                    .filter(mapping -> normalize(residue.insertionCode())
                            .equals(normalize(mapping.insertionCode())))
                    .toList());
            if (matches.size() == 1) {
                persistMapped(candidate, artifact, residue, chain,
                        matches.getFirst());
                mapped++;
                if (matches.getFirst().coordinateStatus()
                        == PolymerResidueMapping.CoordinateStatus.UNRESOLVED) {
                    unresolved++;
                }
            } else {
                persistUnmapped(candidate, artifact, residue,
                        matches.isEmpty() ? "NOT_MAPPED" : "AMBIGUOUS");
                if (matches.size() > 1) ambiguous++;
            }
        }
        upsertEvaluation(candidate, artifact, "EVALUATED", null);
        return new MappingResult(residues.size(), mapped,
                residues.size() - mapped, unresolved, ambiguous);
    }

    @Transactional
    public void recordFailure(Candidate candidate, Path entryMmcif,
            Exception failure) {
        useSchema();
        Long artifact = null;
        try {
            if (Files.isRegularFile(entryMmcif)) {
                artifact = upsertEntryArtifact(candidate.assemblyDatabaseId(),
                        entryMmcif);
            }
        } catch (IOException ignored) {
            // The original failure remains the evaluation error.
        }
        upsertEvaluation(candidate, artifact, "FAILED",
                failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private List<SiteResidue> siteResidues(Candidate candidate) {
        return jdbc.query("""
                SELECT DISTINCT r.auth_asym_id,r.residue_number,
                       COALESCE(r.insertion_code,''),r.residue_name
                FROM experimental_binding_site_residue r
                JOIN experimental_binding_site s ON s.id=r.site_id
                JOIN assembly_component_occurrence o ON o.id=s.occurrence_id
                JOIN experimental_binding_site_target st ON st.site_id=s.id
                WHERE o.assembly_id=? AND st.target_id=?
                  AND o.component_id IN ('SAM','SAH','SFG')
                  AND (SELECT count(*) FROM experimental_binding_site_target x
                       WHERE x.site_id=s.id)=1
                ORDER BY r.auth_asym_id,r.residue_number,
                         COALESCE(r.insertion_code,'')
                """, (rs, row) -> new SiteResidue(rs.getString(1),
                rs.getInt(2), rs.getString(3), rs.getString(4)),
                candidate.assemblyDatabaseId(), candidate.targetId());
    }

    private static List<PolymerResidueMapping> equivalentMappings(
            List<PolymerResidueMapping> mappings) {
        Map<SourceMappingKey, PolymerResidueMapping> unique =
                new LinkedHashMap<>();
        for (PolymerResidueMapping mapping : mappings) {
            unique.putIfAbsent(new SourceMappingKey(mapping.entityId(),
                    mapping.labelSequenceId(), mapping.uniProtAccession(),
                    mapping.uniProtPosition(), mapping.structureResidueName(),
                    mapping.uniProtResidueName(), mapping.sequenceRelation()),
                    mapping);
        }
        return List.copyOf(unique.values());
    }

    private long upsertEntryArtifact(long assembly, Path path)
            throws IOException {
        Long id = jdbc.queryForObject("""
                INSERT INTO assembly_artifact
                    (assembly_id,artifact_type,filename,storage_location,
                     sha256,producer)
                VALUES (?, 'ENTRY_MMCIF', ?, ?, ?, 'RCSB')
                ON CONFLICT (assembly_id,artifact_type,storage_location)
                DO UPDATE SET sha256=EXCLUDED.sha256,producer=EXCLUDED.producer
                RETURNING id
                """, Long.class, assembly, path.getFileName().toString(),
                path.toString(), sha256(path));
        return Objects.requireNonNull(id);
    }

    private ChainRef chain(Candidate candidate, String authAsymId) {
        return jdbc.query("""
                SELECT pc.id,pc.label_asym_id,pe.source_entity_id
                FROM assembly_polymer_chain pc
                JOIN assembly_polymer_entity pe ON pe.id=pc.polymer_entity_id
                JOIN assembly_polymer_target pt ON pt.polymer_entity_id=pe.id
                WHERE pe.assembly_id=? AND pt.target_id=?
                  AND pc.auth_asym_id=?
                ORDER BY pc.model_number LIMIT 1
                """, rs -> rs.next() ? new ChainRef(rs.getLong(1),
                        rs.getString(2), rs.getString(3)) : null,
                candidate.assemblyDatabaseId(), candidate.targetId(),
                authAsymId);
    }

    private void persistMapped(Candidate candidate, long artifact,
            SiteResidue residue, ChainRef chain,
            PolymerResidueMapping mapping) {
        jdbc.update("""
                INSERT INTO assembly_residue_uniprot_mapping
                    (assembly_id,polymer_chain_id,target_id,source_artifact_id,
                     entity_id,label_asym_id,auth_asym_id,label_sequence_id,
                     auth_sequence_id,residue_number_source,insertion_code,
                     structure_residue_name,
                     uniprot_accession,uniprot_position,uniprot_residue_name,
                     coordinate_status,mapping_outcome,difference_details,
                     origin,method,method_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DERIVED',?,?)
                """, candidate.assemblyDatabaseId(),
                chain == null ? null : chain.id(),
                candidate.targetId(), artifact, mapping.entityId(),
                chain == null ? mapping.labelAsymId() : chain.labelAsymId(),
                residue.authAsymId(),
                mapping.labelSequenceId(), mapping.authSequenceId(),
                mapping.residueNumberSource().name(),
                normalize(mapping.insertionCode()),
                mapping.structureResidueName(), mapping.uniProtAccession(),
                mapping.uniProtPosition(), mapping.uniProtResidueName(),
                mapping.coordinateStatus().name(),
                mapping.sequenceRelation().name(), mapping.differenceDetails(),
                METHOD, METHOD_VERSION);
    }

    private void persistUnmapped(Candidate candidate, long artifact,
            SiteResidue residue, String outcome) {
        jdbc.update("""
                INSERT INTO assembly_residue_uniprot_mapping
                    (assembly_id,target_id,source_artifact_id,auth_asym_id,
                     auth_sequence_id,residue_number_source,insertion_code,
                     structure_residue_name,
                     uniprot_accession,coordinate_status,mapping_outcome,
                     origin,method,method_version)
                VALUES (?,?,?,?,?,'UNKNOWN',?,?,?, 'UNKNOWN',?,'DERIVED',?,?)
                """, candidate.assemblyDatabaseId(), candidate.targetId(),
                artifact, residue.authAsymId(),
                Integer.toString(residue.residueNumber()),
                normalize(residue.insertionCode()), residue.residueName(),
                candidate.uniProtAccession(), outcome, METHOD, METHOD_VERSION);
    }

    private void upsertEvaluation(Candidate candidate, Long artifact,
            String status, String error) {
        jdbc.update("""
                INSERT INTO assembly_residue_mapping_evaluation
                    (assembly_id,target_id,entry_artifact_id,evaluation_status,
                     method,method_version,error,evaluated_at)
                VALUES (?,?,?,?,?,?,?,now())
                ON CONFLICT (assembly_id,target_id) DO UPDATE SET
                    entry_artifact_id=EXCLUDED.entry_artifact_id,
                    evaluation_status=EXCLUDED.evaluation_status,
                    method=EXCLUDED.method,method_version=EXCLUDED.method_version,
                    error=EXCLUDED.error,evaluated_at=EXCLUDED.evaluated_at
                """, candidate.assemblyDatabaseId(), candidate.targetId(),
                artifact, status, METHOD, METHOD_VERSION, error);
    }

    private void useSchema() {
        jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? "" : value;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Candidate(long assemblyDatabaseId, String pdbId,
            String assemblyId, long targetId, String uniProtAccession,
            Path assemblyMmcif) {}

    public record MappingResult(int requestedResidues, int mappedResidues,
            int unmappedResidues, int unresolvedCoordinates,
            int ambiguousResidues) {}

    private record SiteResidue(String authAsymId, int residueNumber,
            String insertionCode, String residueName) {}

    private record ChainRef(long id, String labelAsymId, String entityId) {}

    private record SourceMappingKey(String entityId, int labelSequenceId,
            String uniProtAccession, int uniProtPosition,
            String structureResidueName, String uniProtResidueName,
            PolymerResidueMapping.SequenceRelation sequenceRelation) {}
}
