package totah.lab.web.assembly;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.SequenceResidue;

import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds confidence-gated pairwise correspondence between experimental targets. */
@Service
public class ExperimentalTargetCorrespondenceService {
    public static final String SEQUENCE_METHOD = "RCSB_MMCIF_STRUCT_REF_UNIPROT_SEQUENCE";
    public static final String UNIPROT_SEQUENCE_METHOD = "UNIPROT_REST_ENTRY_JSON";
    public static final String ALIGNMENT_METHOD = "ATHENA_NEEDLEMAN_WUNSCH";
    public static final String METHOD_VERSION = "2";
    public static final double IDENTITY_THRESHOLD = 0.30;
    public static final double COVERAGE_THRESHOLD = 0.70;

    private final JdbcTemplate jdbc;
    private final ExperimentalTargetSequenceSourceLoader sourceLoader;
    private final UniProtSequenceResolver uniProtResolver;
    private final NeedlemanWunschSequenceAligner aligner =
            new NeedlemanWunschSequenceAligner();
    private final String schema;

    public ExperimentalTargetCorrespondenceService(JdbcTemplate jdbc,
            ExperimentalTargetSequenceSourceLoader sourceLoader,
            UniProtSequenceResolver uniProtResolver,
            @Value("${totah.persistence.docking-schema:docking}") String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.sourceLoader = Objects.requireNonNull(sourceLoader);
        this.uniProtResolver = Objects.requireNonNull(uniProtResolver);
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid schema: " + schema);
        }
        this.schema = schema;
    }

    @Transactional(rollbackFor = IOException.class)
    public Result build() throws IOException {
        useSchema();
        LoadedSequences loaded = loadSequences();
        Map<Long, TargetSequence> sequences = loaded.sequences();
        persistSequences(sequences);
        Map<Long, ContactPositions> contacts = contactPositions();
        int accepted = 0;
        int lowConfidence = 0;
        int pairs = 0;
        List<TargetSequence> targets = new ArrayList<>(sequences.values());
        targets.sort(java.util.Comparator.comparingLong(
                TargetSequence::targetId));
        for (int first = 0; first < targets.size(); first++) {
            for (int second = first + 1; second < targets.size(); second++) {
                TargetSequence query = targets.get(first);
                TargetSequence candidate = targets.get(second);
                var alignment = aligner.align(residues(query.sequence()),
                        residues(candidate.sequence()));
                double queryCoverage = (double) alignment.pairs().size()
                        / query.sequence().length();
                double candidateCoverage = (double) alignment.pairs().size()
                        / candidate.sequence().length();
                boolean usable = alignment.identity() >= IDENTITY_THRESHOLD
                        && Math.min(queryCoverage, candidateCoverage)
                        >= COVERAGE_THRESHOLD;
                long id = upsertAlignment(query, candidate, alignment.identity(),
                        queryCoverage, candidateCoverage, alignment.pairs().size(),
                        usable ? "ACCEPTED" : "LOW_CONFIDENCE");
                jdbc.update("DELETE FROM experimental_target_alignment_pair "
                        + "WHERE alignment_id=?", id);
                if (usable) {
                    accepted++;
                    for (var pair : alignment.pairs()) {
                        ContactPositions queryContact = contacts.getOrDefault(
                                query.targetId(), ContactPositions.empty());
                        ContactPositions candidateContact = contacts.getOrDefault(
                                candidate.targetId(), ContactPositions.empty());
                        jdbc.update("""
                                INSERT INTO experimental_target_alignment_pair
                                  (alignment_id,query_uniprot_position,
                                   candidate_uniprot_position,query_residue,
                                   candidate_residue,substitution_score,
                                   query_direct_contact,candidate_direct_contact,
                                   query_near_shell,candidate_near_shell)
                                VALUES (?,?,?,?,?,?,?,?,?,?)
                                """, id, pair.queryResidueNumber(),
                                pair.candidateResidueNumber(),
                                oneLetter(pair.queryResidueName()),
                                oneLetter(pair.candidateResidueName()),
                                pair.queryResidueName().equals(
                                        pair.candidateResidueName()) ? 2 : -1,
                                queryContact.direct().contains(
                                        pair.queryResidueNumber()),
                                candidateContact.direct().contains(
                                        pair.candidateResidueNumber()),
                                queryContact.near().contains(
                                        pair.queryResidueNumber()),
                                candidateContact.near().contains(
                                        pair.candidateResidueNumber()));
                        pairs++;
                    }
                } else {
                    lowConfidence++;
                }
            }
        }
        return new Result(targets.size(), loaded.unavailableTargets(), accepted,
                lowConfidence, pairs);
    }

    private LoadedSequences loadSequences() {
        List<Source> sources = jdbc.query("""
                SELECT DISTINCT e.target_id,t.uniprot_id,a.id,a.storage_location
                FROM assembly_residue_mapping_evaluation e
                JOIN targets t ON t.id=e.target_id
                JOIN assembly_artifact a ON a.id=e.entry_artifact_id
                WHERE e.evaluation_status='EVALUATED'
                ORDER BY e.target_id,a.id
                """, (rs, row) -> new Source(rs.getLong(1), rs.getString(2),
                rs.getLong(3), Path.of(rs.getString(4))));
        Map<Long, TargetSequence> result = new LinkedHashMap<>();
        Set<Long> allTargets = new LinkedHashSet<>();
        Set<Long> conflictedTargets = new LinkedHashSet<>();
        for (Source source : sources) {
            allTargets.add(source.targetId());
            String sequence;
            try {
                sequence = sourceLoader.load(source.path()).stream()
                        .filter(value -> source.accession().equalsIgnoreCase(
                                value.accession())).map(value -> value.sequence())
                        .findFirst().orElse(null);
            } catch (IOException exception) {
                persistSourceEvaluation(source, "FAILED", "FAILED",
                        exception.getMessage());
                continue;
            }
            if (sequence == null) {
                persistSourceEvaluation(source, "EVALUATED", "NO_EVIDENCE",
                        null);
                continue;
            }
            TargetSequence existing = result.get(source.targetId());
            if (existing != null && !existing.sequence().equals(sequence)) {
                conflictedTargets.add(source.targetId());
                persistSourceEvaluation(source, "FAILED", "FAILED",
                        "Conflicting source sequences for " + source.accession());
                continue;
            }
            if (existing == null) {
                existing = new TargetSequence(source.targetId(),
                        source.accession(), sequence, SEQUENCE_METHOD,
                        "file:" + source.path(), new LinkedHashSet<>());
                result.put(source.targetId(), existing);
            }
            existing.artifactIds().add(source.artifactId());
            persistSourceEvaluation(source, "EVALUATED", "EVIDENCE_PRESENT",
                    null);
        }
        conflictedTargets.forEach(result::remove);
        Map<Long, Source> representativeSources = new LinkedHashMap<>();
        sources.forEach(source -> representativeSources.putIfAbsent(
                source.targetId(), source));
        for (Source source : representativeSources.values()) {
            try {
                String sequence = uniProtResolver.resolve(source.accession())
                        .orElse(null);
                if (sequence != null && !sequence.isBlank()) {
                    TargetSequence mmcif = result.get(source.targetId());
                    Set<Long> corroboratingArtifacts = mmcif != null
                            && mmcif.sequence().equals(sequence)
                            ? mmcif.artifactIds() : new LinkedHashSet<>();
                    result.put(source.targetId(), new TargetSequence(
                            source.targetId(), source.accession(), sequence,
                            UNIPROT_SEQUENCE_METHOD,
                            "https://rest.uniprot.org/uniprotkb/"
                                    + source.accession() + ".json",
                            corroboratingArtifacts));
                }
            } catch (Exception ignored) {
                // A consistent mmCIF sequence remains usable when REST is unavailable.
            }
        }
        return new LoadedSequences(result,
                allTargets.size() - result.size());
    }

    private void persistSourceEvaluation(Source source, String status,
            String availability, String error) {
        jdbc.update("""
                INSERT INTO experimental_target_sequence_evaluation
                  (target_id,artifact_id,evaluation_status,availability,error,
                   method,method_version,evaluated_at)
                VALUES (?,?,?,?,?,?,?,now())
                ON CONFLICT(target_id,artifact_id) DO UPDATE SET
                  evaluation_status=EXCLUDED.evaluation_status,
                  availability=EXCLUDED.availability,error=EXCLUDED.error,
                  method=EXCLUDED.method,method_version=EXCLUDED.method_version,
                  evaluated_at=EXCLUDED.evaluated_at
                """, source.targetId(), source.artifactId(), status,
                availability, error, SEQUENCE_METHOD, METHOD_VERSION);
    }

    private void persistSequences(Map<Long, TargetSequence> sequences) {
        for (TargetSequence sequence : sequences.values()) {
            jdbc.update("""
                    INSERT INTO experimental_target_sequence
                      (target_id,uniprot_accession,sequence,sequence_sha256,
                       origin,method,method_version,source_uri,retrieved_at)
                    VALUES (?,?,?,?,'SOURCE_REPORTED',?,?,?,now())
                    ON CONFLICT(target_id) DO UPDATE SET
                      uniprot_accession=EXCLUDED.uniprot_accession,
                      sequence=EXCLUDED.sequence,
                      sequence_sha256=EXCLUDED.sequence_sha256,
                      origin=EXCLUDED.origin,method=EXCLUDED.method,
                      method_version=EXCLUDED.method_version,
                      source_uri=EXCLUDED.source_uri,
                      retrieved_at=EXCLUDED.retrieved_at
                    """, sequence.targetId(), sequence.accession(),
                    sequence.sequence(), sha256(sequence.sequence()),
                    sequence.sourceMethod(), METHOD_VERSION,
                    sequence.sourceUri());
            jdbc.update("DELETE FROM experimental_target_sequence_source "
                    + "WHERE target_id=?", sequence.targetId());
            for (long artifact : sequence.artifactIds()) {
                jdbc.update("INSERT INTO experimental_target_sequence_source"
                        + "(target_id,artifact_id) VALUES (?,?)",
                        sequence.targetId(), artifact);
            }
        }
    }

    private Map<Long, ContactPositions> contactPositions() {
        Map<Long, ContactPositions> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DISTINCT st.target_id,m.uniprot_position,r.distance_band
                FROM experimental_binding_site s
                JOIN assembly_component_occurrence o ON o.id=s.occurrence_id
                JOIN experimental_binding_site_target st ON st.site_id=s.id
                JOIN experimental_binding_site_residue r ON r.site_id=s.id
                JOIN assembly_residue_uniprot_mapping m
                  ON m.assembly_id=o.assembly_id AND m.target_id=st.target_id
                 AND m.auth_asym_id=r.auth_asym_id
                 AND m.auth_sequence_id=r.residue_number::text
                 AND m.insertion_code=COALESCE(r.insertion_code,'')
                WHERE o.component_id IN ('SAM','SAH','SFG')
                  AND m.uniprot_position IS NOT NULL
                  AND (SELECT count(*) FROM experimental_binding_site_target x
                       WHERE x.site_id=s.id)=1
                """, rs -> {
            while (rs.next()) {
                ContactPositions positions = result.computeIfAbsent(rs.getLong(1),
                        ignored -> new ContactPositions(new LinkedHashSet<>(),
                                new LinkedHashSet<>()));
                if ("DIRECT".equals(rs.getString(3))) {
                    positions.direct().add(rs.getInt(2));
                } else {
                    positions.near().add(rs.getInt(2));
                }
            }
        });
        return result;
    }

    private long upsertAlignment(TargetSequence query, TargetSequence candidate,
            double identity, double queryCoverage, double candidateCoverage,
            int count, String status) {
        Long id = jdbc.queryForObject("""
                INSERT INTO experimental_target_alignment
                  (query_target_id,candidate_target_id,identity,query_coverage,
                   candidate_coverage,aligned_pair_count,evaluation_status,
                   correspondence_status,method,method_version,
                   identity_threshold,coverage_threshold,evaluated_at)
                VALUES (?,?,?,?,?,?,'EVALUATED',?,?,?,?,?,now())
                ON CONFLICT(query_target_id,candidate_target_id,method_version)
                DO UPDATE SET identity=EXCLUDED.identity,
                  query_coverage=EXCLUDED.query_coverage,
                  candidate_coverage=EXCLUDED.candidate_coverage,
                  aligned_pair_count=EXCLUDED.aligned_pair_count,
                  evaluation_status=EXCLUDED.evaluation_status,
                  correspondence_status=EXCLUDED.correspondence_status,
                  method=EXCLUDED.method,
                  identity_threshold=EXCLUDED.identity_threshold,
                  coverage_threshold=EXCLUDED.coverage_threshold,
                  evaluated_at=EXCLUDED.evaluated_at
                RETURNING id
                """, Long.class, query.targetId(), candidate.targetId(), identity,
                queryCoverage, candidateCoverage, count, status,
                ALIGNMENT_METHOD, METHOD_VERSION, IDENTITY_THRESHOLD,
                COVERAGE_THRESHOLD);
        return Objects.requireNonNull(id);
    }

    private static List<SequenceResidue> residues(String sequence) {
        List<SequenceResidue> result = new ArrayList<>(sequence.length());
        for (int index = 0; index < sequence.length(); index++) {
            result.add(new SequenceResidue(index + 1,
                    String.valueOf(sequence.charAt(index))));
        }
        return result;
    }

    private static String oneLetter(String value) {
        return value.substring(0, 1).toUpperCase();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void useSchema() {
        jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
    }

    private record Source(long targetId, String accession, long artifactId,
            Path path) {}
    private record LoadedSequences(Map<Long, TargetSequence> sequences,
            int unavailableTargets) {}
    private record TargetSequence(long targetId, String accession,
            String sequence, String sourceMethod, String sourceUri,
            Set<Long> artifactIds) {}
    private record ContactPositions(Set<Integer> direct, Set<Integer> near) {
        static ContactPositions empty() {
            return new ContactPositions(Set.of(), Set.of());
        }
    }
    public record Result(int targets, int unavailableTargets,
            int acceptedAlignments,
            int lowConfidenceAlignments, int persistedPairs) {}
}
