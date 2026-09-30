package totah.lab.web.research;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public final class Mettl7EvidenceReportService {
    private final JdbcTemplate jdbc;

    public Mettl7EvidenceReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReportView generate() {
        return new ReportView(
                Instant.now(),
                jdbc.queryForList("select * from docking.mettl7_research_source order by publication_year, source_key"),
                jdbc.queryForList("select * from docking.mettl7_mechanistic_evidence order by enzyme, activity, evidence_key"),
                jdbc.queryForList("select f.*, p.volume as pocket_volume, p.pocket_number, p.source as pocket_source from docking.mettl7_implicated_feature f left join docking.pocket p on p.id=f.pocket_id order by f.enzyme, f.feature_type, f.residue_number nulls last, f.feature_key"),
                jdbc.queryForList("select * from docking.mettl7_computational_run order by completed_on, run_key"),
                jdbc.queryForList("select enzyme, residue_number, residue_name, sum(severe_atom_pairs) as severe_atom_pairs, sum(close_atom_pairs) as close_atom_pairs, count(distinct site_id) as site_count from docking.mettl7_residue_clash where focus_position group by enzyme, residue_number, residue_name order by residue_number, enzyme"),
                jdbc.queryForList("select enzyme, site_id, residue_number, residue_name, severe_atom_pairs, close_atom_pairs, minimum_distance_a from docking.mettl7_residue_clash where focus_position order by site_id, residue_number, enzyme"),
                jdbc.queryForList("select enzyme, site_id, count(*) as refined_models, count(*) filter (where distance_window_satisfied and angle_window_satisfied) as catalytic_window_models, min(severe_atom_pairs) as minimum_severe_clashes, min(haddock_score) filter (where distance_window_satisfied and angle_window_satisfied) as best_window_haddock_score from docking.mettl7_flexible_rna_model group by enzyme, site_id order by enzyme, site_id"),
                jdbc.queryForList("select enzyme, mutation, site_id, avg(delta_distance_window) as delta_distance_window, avg(delta_angle_window) as delta_angle_window, avg(delta_both_windows) as delta_both_windows, avg(delta_rna_deformation_a) as delta_rna_deformation_a, avg(delta_backbone_escape_a) as delta_backbone_escape_a, avg(delta_severe_clashes) as delta_severe_clashes, avg(delta_close_pairs) as delta_close_pairs, avg(delta_contact_pairs) as delta_contact_pairs, avg(delta_restraint_energy) as delta_restraint_energy, sum(case when delta_both_windows > 0 then 1 else 0 end) as conformers_positive, sum(case when delta_both_windows < 0 then 1 else 0 end) as conformers_negative, sum(case when delta_both_windows = 0 then 1 else 0 end) as conformers_unchanged from docking.mettl7_rna_selectivity_contrast group by enzyme, mutation, site_id order by enzyme, mutation, site_id"),
                jdbc.queryForList("select enzyme, mutation, residue_number, avg(delta_target_fraction) as delta_target_fraction, avg(delta_rna_fraction) as delta_rna_fraction from docking.mettl7_rna_residue_contact_contrast where focus_position group by enzyme, mutation, residue_number order by residue_number, enzyme, mutation"),
                jdbc.queryForList("select * from docking.mettl7_long_rna_fragment order by rna_key"),
                jdbc.queryForList("select enzyme, rna_key, cognate, count(*) as retained_models, count(*) filter (where distance_window) as distance_window_models, count(*) filter (where angle_window) as angle_window_models, count(*) filter (where both_windows) as catalytic_window_models, count(*) filter (where severe_clashes=0) as severe_clash_free_models from docking.mettl7_long_rna_model group by enzyme, rna_key, cognate order by rna_key, enzyme"),
                jdbc.queryForList("select enzyme, count(*) as retained_models, count(*) filter (where distance_window) as distance_window_models, count(*) filter (where angle_window) as angle_window_models, count(*) filter (where both_windows) as admissible_models, count(distinct conformer) filter (where both_windows) as admissible_conformers from docking.mettl7_klf4_final_validation_model group by enzyme order by enzyme"),
                jdbc.queryForList("select * from docking.mettl7_reproducibility_artifact order by run_key, artifact_type, artifact_key"),
                List.of(
                        "Resolve exact KLF4 and NFKBIA m7G nucleotides.",
                        "Test purified METTL7A against defined BMP2 and lncRNA substrates with SAM.",
                        "Test purified METTL7B against a defined FILIP1L RNA and site mutant.",
                        "Measure METTL7B kinetics on defined RNA substrates.",
                        "Test catalytic mutants in purified-protein assays.",
                        "Reconcile the METTL7B expression-system discrepancy between main text and supplement.",
                        "Determine whether netarsudil changes purified METTL7B catalysis.",
                        "Repeat netarsudil binding across a concentration range appropriate for the reported millimolar KD.",
                        "Test netarsudil against METTL7A and pocket mutants.",
                        "Determine whether RNA and thiol substrates occupy overlapping or allosterically coupled surfaces."
                )
        );
    }

    public String artifactRepositoryPath(String artifactKey) {
        return jdbc.queryForObject(
                "select repository_path from docking.mettl7_reproducibility_artifact where artifact_key=?",
                String.class, artifactKey);
    }

    public record ReportView(Instant generatedAt, List<Map<String, Object>> sources,
                             List<Map<String, Object>> evidence,
                             List<Map<String, Object>> implicatedFeatures,
                             List<Map<String, Object>> computationalRuns,
                             List<Map<String, Object>> focusClashSummary,
                             List<Map<String, Object>> focusClashes,
                             List<Map<String, Object>> flexibleRnaSummary,
                             List<Map<String, Object>> rnaSelectivityContrasts,
                             List<Map<String, Object>> rnaSelectivityResidues,
                             List<Map<String, Object>> longRnaFragments,
                             List<Map<String, Object>> longRnaSummary,
                             List<Map<String, Object>> klf4FinalValidation,
                             List<Map<String, Object>> reproducibilityArtifacts,
                             List<String> unansweredQuestions) {}
}
