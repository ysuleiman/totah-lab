package totah.lab.web.assembly;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.evidence.EvaluationStatus;
import totah.lab.athena.pocket.evidence.grammar.ExperimentalContactRole;
import totah.lab.athena.pocket.evidence.grammar.ExperimentalCoordinateObservation;
import totah.lab.athena.pocket.evidence.grammar.ExperimentalResidueCoordinate;
import totah.lab.athena.pocket.evidence.grammar.ExperimentalSiteGrammarFactory;
import totah.lab.athena.pocket.evidence.grammar.ExperimentalStructuralVariabilityCalculator;
import totah.lab.athena.pocket.evidence.grammar.StructuralVariabilityEvidence;
import totah.lab.hermes.file.mmcif.ResidueCoordinateObservation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Persists independent grammar dimensions for accepted target alignments. */
@Service
public class ExperimentalSiteGrammarService {
    public static final String METHOD = "EXPERIMENTAL_SITE_GRAMMAR";
    public static final String METHOD_VERSION = "1";
    public static final String CORRESPONDENCE_VERSION = "2";
    public static final String STRUCTURAL_METHOD =
            ExperimentalStructuralVariabilityCalculator.METHOD;
    public static final String STRUCTURAL_METHOD_VERSION =
            ExperimentalStructuralVariabilityCalculator.VERSION;
    public static final double STRUCTURAL_STABILITY_THRESHOLD_ANGSTROMS = 1.0;

    private final JdbcTemplate jdbc;
    private final ExperimentalResidueCoordinateSourceLoader coordinateLoader;
    private final String schema;
    private final ExperimentalSiteGrammarFactory factory =
            new ExperimentalSiteGrammarFactory();

    public ExperimentalSiteGrammarService(JdbcTemplate jdbc,
            ExperimentalResidueCoordinateSourceLoader coordinateLoader,
            @Value("${totah.persistence.docking-schema:docking}")
            String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.coordinateLoader = Objects.requireNonNull(coordinateLoader);
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid schema: " + schema);
        }
        this.schema = schema;
    }

    @Transactional
    public Result derive() throws IOException {
        useSchema();
        Map<PositionKey, ObservationCounts> observations = observations();
        Map<PositionKey, StructuralVariabilityEvidence> variability =
                structuralVariability();
        jdbc.update("DELETE FROM experimental_site_grammar_summary "
                + "WHERE method_version=?", METHOD_VERSION);
        jdbc.update("DELETE FROM experimental_site_grammar_residue "
                + "WHERE method_version=?", METHOD_VERSION);

        var alignedPairs = jdbc.query("""
                SELECT a.id,a.query_target_id,a.candidate_target_id,
                       p.query_uniprot_position,p.candidate_uniprot_position,
                       p.query_residue,p.candidate_residue,
                       p.query_direct_contact,p.candidate_direct_contact,
                       p.query_near_shell,p.candidate_near_shell
                FROM experimental_target_alignment a
                JOIN experimental_target_alignment_pair p
                  ON p.alignment_id=a.id
                WHERE a.method_version=?
                  AND a.correspondence_status='ACCEPTED'
                ORDER BY a.id,p.query_uniprot_position,
                         p.candidate_uniprot_position
                """, (rs, row) -> new AlignedPair(rs.getLong(1),
                rs.getLong(2), rs.getLong(3), rs.getInt(4), rs.getInt(5),
                rs.getString(6), rs.getString(7), rs.getBoolean(8),
                rs.getBoolean(9), rs.getBoolean(10), rs.getBoolean(11)),
                CORRESPONDENCE_VERSION);
        Counter counter = new Counter();
        for (AlignedPair pair : alignedPairs) {
                long alignment = pair.alignmentId();
                long queryTarget = pair.queryTargetId();
                long candidateTarget = pair.candidateTargetId();
                int queryPosition = pair.queryPosition();
                int candidatePosition = pair.candidatePosition();
                ObservationCounts query = observations.getOrDefault(
                        new PositionKey(queryTarget, queryPosition),
                        ObservationCounts.NONE);
                ObservationCounts candidate = observations.getOrDefault(
                        new PositionKey(candidateTarget, candidatePosition),
                        ObservationCounts.NONE);
                ExperimentalContactRole queryRole = role(pair.queryDirect(),
                        pair.queryShell());
                ExperimentalContactRole candidateRole = role(
                        pair.candidateDirect(), pair.candidateShell());
                var grammar = factory.derive(queryPosition,
                        candidatePosition, pair.queryResidue(),
                        pair.candidateResidue(),
                        queryRole, candidateRole, query.direct(), query.shell(),
                        candidate.direct(), candidate.shell(),
                        variability.getOrDefault(
                                new PositionKey(queryTarget, queryPosition),
                                unavailable(query.structures())),
                        variability.getOrDefault(new PositionKey(
                                candidateTarget, candidatePosition),
                                unavailable(candidate.structures())));
                persist(alignment, grammar);
                counter.rows++;
                if (grammar.hasExperimentalSiteEvidence()) counter.siteRows++;
        }
        int summaries = persistSummaries();
        int lowConfidenceRows = jdbc.queryForObject("""
                SELECT count(*) FROM experimental_site_grammar_residue g
                JOIN experimental_target_alignment a ON a.id=g.alignment_id
                WHERE g.method_version=?
                  AND a.correspondence_status<>'ACCEPTED'
                """, Integer.class, METHOD_VERSION);
        return new Result(summaries, counter.rows, counter.siteRows,
                lowConfidenceRows);
    }

    private Map<PositionKey, ObservationCounts> observations() {
        Map<PositionKey, ObservationCounts> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT st.target_id,m.uniprot_position,
                  count(DISTINCT s.id) FILTER
                    (WHERE r.distance_band='DIRECT'),
                  count(DISTINCT s.id) FILTER
                    (WHERE r.distance_band='NEAR_SHELL'),
                  count(DISTINCT o.assembly_id) FILTER
                    (WHERE m.coordinate_status='RESOLVED')
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
                GROUP BY st.target_id,m.uniprot_position
                """, rs -> {
            while (rs.next()) {
                result.put(new PositionKey(rs.getLong(1), rs.getInt(2)),
                        new ObservationCounts(rs.getInt(3), rs.getInt(4),
                                rs.getInt(5)));
            }
        });
        return result;
    }

    private Map<PositionKey, StructuralVariabilityEvidence>
            structuralVariability() throws IOException {
        List<CoordinateMapping> mappings = jdbc.query("""
                SELECT m.target_id,m.uniprot_position,m.assembly_id,
                       m.auth_asym_id,m.auth_sequence_id,m.insertion_code,
                       source.storage_location
                FROM assembly_residue_uniprot_mapping m
                JOIN assembly_artifact source ON source.assembly_id=m.assembly_id
                 AND source.artifact_type='SOURCE_MMCIF'
                WHERE m.uniprot_position IS NOT NULL
                  AND m.coordinate_status='RESOLVED'
                ORDER BY m.target_id,m.assembly_id,m.auth_asym_id,
                         m.uniprot_position
                """, (rs, row) -> new CoordinateMapping(rs.getLong(1),
                rs.getInt(2), rs.getLong(3), rs.getString(4),
                Integer.parseInt(rs.getString(5)), rs.getString(6),
                Path.of(rs.getString(7))));
        Map<Path, Map<ResidueKey, ResidueCoordinateObservation>> sources =
                new LinkedHashMap<>();
        Map<Long, Map<String, Map<Integer, ExperimentalResidueCoordinate>>>
                byTarget = new LinkedHashMap<>();
        for (CoordinateMapping mapping : mappings) {
            Map<ResidueKey, ResidueCoordinateObservation> source =
                    sources.get(mapping.path());
            if (source == null) {
                source = new LinkedHashMap<>();
                for (ResidueCoordinateObservation observation
                        : coordinateLoader.load(mapping.path())) {
                    source.put(new ResidueKey(observation.authAsymId(),
                            observation.authSequenceId(),
                            observation.insertionCode()), observation);
                }
                sources.put(mapping.path(), source);
            }
            ResidueCoordinateObservation coordinate = source.get(
                    new ResidueKey(mapping.chain(), mapping.sequence(),
                            normalize(mapping.insertion())));
            if (coordinate == null) continue;
            String observationId = mapping.assemblyId() + ":" + mapping.chain();
            byTarget.computeIfAbsent(mapping.targetId(), ignored ->
                            new LinkedHashMap<>())
                    .computeIfAbsent(observationId, ignored ->
                            new LinkedHashMap<>())
                    .put(mapping.position(), new ExperimentalResidueCoordinate(
                            coordinate.ca(), coordinate.sideChainCentroid()));
        }
        var calculator = new ExperimentalStructuralVariabilityCalculator();
        Map<PositionKey, StructuralVariabilityEvidence> result =
                new LinkedHashMap<>();
        byTarget.forEach((target, observations) -> {
            List<ExperimentalCoordinateObservation> values = new ArrayList<>();
            observations.forEach((id, residues) -> values.add(
                    new ExperimentalCoordinateObservation(id, residues)));
            calculator.calculate(values).forEach((position, evidence) ->
                    result.put(new PositionKey(target, position), evidence));
        });
        return result;
    }

    private void persist(long alignment,
            totah.lab.athena.pocket.evidence.grammar.ExperimentalSiteGrammarResidue g) {
        jdbc.update("""
                INSERT INTO experimental_site_grammar_residue
                  (alignment_id,query_uniprot_position,
                   candidate_uniprot_position,query_residue,
                   candidate_residue,identical,substitution_similarity,
                   query_chemistry,candidate_chemistry,
                   chemistry_relationship,query_contact_role,
                   candidate_contact_role,query_direct_observation_count,
                   query_shell_observation_count,
                   candidate_direct_observation_count,
                   candidate_shell_observation_count,
                   query_structure_observation_count,
                   candidate_structure_observation_count,
                   query_structural_status,candidate_structural_status,
                   query_ca_rmsf,candidate_ca_rmsf,
                   query_side_chain_rmsf,candidate_side_chain_rmsf,
                   structural_method,structural_method_version,
                   query_structural_reason,candidate_structural_reason,
                   origin,method,method_version,derived_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,
                        ?,'DERIVED',?,?,now())
                """, alignment, g.queryPosition(), g.candidatePosition(),
                g.queryResidue(), g.candidateResidue(), g.identical(),
                g.substitutionSimilarity(), g.queryChemistry().name(),
                g.candidateChemistry().name(),
                g.chemistryRelationship().name(),
                g.queryContactRole().name(), g.candidateContactRole().name(),
                g.queryDirectObservationCount(), g.queryShellObservationCount(),
                g.candidateDirectObservationCount(),
                g.candidateShellObservationCount(),
                g.queryStructuralVariability().observationCount(),
                g.candidateStructuralVariability().observationCount(),
                g.queryStructuralVariability().status().name(),
                g.candidateStructuralVariability().status().name(),
                nullable(g.queryStructuralVariability().caRmsfAngstroms()),
                nullable(g.candidateStructuralVariability().caRmsfAngstroms()),
                nullable(g.queryStructuralVariability()
                        .sideChainCentroidRmsfAngstroms()),
                nullable(g.candidateStructuralVariability()
                        .sideChainCentroidRmsfAngstroms()), STRUCTURAL_METHOD,
                STRUCTURAL_METHOD_VERSION,
                g.queryStructuralVariability().reason(),
                g.candidateStructuralVariability().reason(), METHOD,
                METHOD_VERSION);
    }

    private int persistSummaries() {
        return jdbc.update("""
                INSERT INTO experimental_site_grammar_summary
                  (alignment_id,aligned_site_residue_count,
                   exact_identity_fraction,mean_substitution_similarity,
                   median_substitution_similarity,chemistry_match_fraction,
                   direct_contact_residue_coverage,
                   direct_contact_conservation_fraction,
                   shell_conservation_fraction,direct_to_shell_shift_count,
                   chemistry_changing_contact_substitution_count,
                   structurally_stable_conserved_count,
                   structurally_variable_conserved_count,
                   evaluable_structural_position_count,
                   unavailable_structural_position_count,
                   structural_stability_threshold,method,
                   method_version,derived_at)
                SELECT alignment_id,count(*),avg(identical::int),
                  avg(substitution_similarity),
                  percentile_cont(0.5) WITHIN GROUP
                    (ORDER BY substitution_similarity),
                  avg((chemistry_relationship<>'DIFFERENT')::int),
                  avg(((query_contact_role='DIRECT' OR
                        candidate_contact_role='DIRECT'))::int),
                  avg(((query_contact_role='DIRECT' AND
                        candidate_contact_role='DIRECT'))::int) FILTER
                    (WHERE query_contact_role='DIRECT' OR
                           candidate_contact_role='DIRECT'),
                  avg(((query_contact_role='NEAR_SHELL' AND
                        candidate_contact_role='NEAR_SHELL'))::int) FILTER
                    (WHERE query_contact_role='NEAR_SHELL' OR
                           candidate_contact_role='NEAR_SHELL'),
                  count(*) FILTER (WHERE
                    (query_contact_role='DIRECT' AND
                     candidate_contact_role='NEAR_SHELL') OR
                    (query_contact_role='NEAR_SHELL' AND
                     candidate_contact_role='DIRECT')),
                  count(*) FILTER (WHERE chemistry_relationship='DIFFERENT'
                    AND (query_contact_role='DIRECT' OR
                         candidate_contact_role='DIRECT')),
                  count(*) FILTER (WHERE identical
                    AND query_structural_status='PRESENT'
                    AND candidate_structural_status='PRESENT'
                    AND query_ca_rmsf<=? AND candidate_ca_rmsf<=?),
                  count(*) FILTER (WHERE identical
                    AND query_structural_status='PRESENT'
                    AND candidate_structural_status='PRESENT'
                    AND (query_ca_rmsf>? OR candidate_ca_rmsf>?)),
                  count(*) FILTER (WHERE query_structural_status='PRESENT'
                    AND candidate_structural_status='PRESENT'),
                  count(*) FILTER (WHERE query_structural_status<>'PRESENT'
                    OR candidate_structural_status<>'PRESENT'),
                  ?,?,?,now()
                FROM experimental_site_grammar_residue
                WHERE method_version=?
                  AND (query_contact_role<>'NONE' OR
                       candidate_contact_role<>'NONE')
                GROUP BY alignment_id
                """, STRUCTURAL_STABILITY_THRESHOLD_ANGSTROMS,
                STRUCTURAL_STABILITY_THRESHOLD_ANGSTROMS,
                STRUCTURAL_STABILITY_THRESHOLD_ANGSTROMS,
                STRUCTURAL_STABILITY_THRESHOLD_ANGSTROMS,
                STRUCTURAL_STABILITY_THRESHOLD_ANGSTROMS,
                METHOD, METHOD_VERSION, METHOD_VERSION);
    }

    private static ExperimentalContactRole role(boolean direct,
            boolean shell) {
        if (direct) return ExperimentalContactRole.DIRECT;
        if (shell) return ExperimentalContactRole.NEAR_SHELL;
        return ExperimentalContactRole.NONE;
    }

    private static StructuralVariabilityEvidence unavailable(int count) {
        return new StructuralVariabilityEvidence(
                EvaluationStatus.EMPTY,
                count, OptionalDouble.empty(), OptionalDouble.empty(),
                STRUCTURAL_METHOD, STRUCTURAL_METHOD_VERSION,
                "INSUFFICIENT_ALIGNED_COORDINATE_OBSERVATIONS");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? "" : value;
    }

    private static Double nullable(OptionalDouble value) {
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private void useSchema() {
        jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
    }

    private record PositionKey(long targetId, int position) {}
    private record CoordinateMapping(long targetId, int position,
            long assemblyId, String chain, int sequence, String insertion,
            Path path) {}
    private record ResidueKey(String chain, int sequence, String insertion) {}
    private record AlignedPair(long alignmentId, long queryTargetId,
            long candidateTargetId, int queryPosition, int candidatePosition,
            String queryResidue, String candidateResidue,
            boolean queryDirect, boolean candidateDirect, boolean queryShell,
            boolean candidateShell) {}
    private record ObservationCounts(int direct, int shell, int structures) {
        private static final ObservationCounts NONE =
                new ObservationCounts(0, 0, 0);
    }
    private static final class Counter { int rows; int siteRows; }
    public record Result(int acceptedPairsWithSiteGrammar,
            int residueGrammarRows, int experimentallySupportedRows,
            int lowConfidenceResidueRows) {}
}
