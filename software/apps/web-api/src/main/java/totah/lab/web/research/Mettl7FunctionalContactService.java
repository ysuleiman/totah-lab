package totah.lab.web.research;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class Mettl7FunctionalContactService {
    static final String RUN_KEY =
            "METTL7_FUNCTIONAL_GROUP_CONTACT_CHEMISTRY_2026_08_31";

    private final JdbcTemplate jdbc;

    public Mettl7FunctionalContactService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReportView report() {
        DatabaseScope scope = jdbc.queryForObject("""
                select
                  (select count(*) from docking.docking_run) as docking_runs,
                  (select count(distinct run_id) from docking.docking_pose)
                    as runs_with_poses,
                  (select count(*) from docking.docking_pose) as pose_records,
                  (select count(distinct coalesce(ligand_label, ligand_id))
                     from docking.docking_pose) as distinct_ligands,
                  count(*) filter (where cohort='AUDITED_74') as cohort_rows,
                  count(distinct ligand) filter (where cohort='AUDITED_74')
                    as cohort_ligands,
                  count(*) filter (where cohort='DCMB_EXTERNAL') as dcmb_rows
                from docking.mettl7_functional_group_contact_analysis
                where run_key=?
                """, (result, row) -> new DatabaseScope(
                        result.getLong("docking_runs"),
                        result.getLong("runs_with_poses"),
                        result.getLong("pose_records"),
                        result.getLong("distinct_ligands"),
                        result.getLong("cohort_rows"),
                        result.getLong("cohort_ligands"),
                        result.getLong("dcmb_rows")
                ), RUN_KEY);

        List<ResidueFunctionalGroupView> residues = jdbc.query("""
                with totals as (
                  select paralog,residue,count(*) as denominator
                  from docking.mettl7_functional_group_contact_analysis
                  where run_key=? and cohort='AUDITED_74'
                  group by paralog,residue
                )
                select a.paralog,a.residue,a.residue_number,
                       a.functional_group,count(*) as contact_count,
                       count(distinct a.ligand) as unique_ligands,
                       count(*)::double precision/t.denominator as fraction,
                       t.denominator
                from docking.mettl7_functional_group_contact_analysis a
                join totals t using(paralog,residue)
                where a.run_key=? and a.cohort='AUDITED_74'
                group by a.paralog,a.residue,a.residue_number,
                         a.functional_group,t.denominator
                order by a.paralog,a.residue_number,contact_count desc,
                         a.functional_group
                """, (result, row) -> new ResidueFunctionalGroupView(
                        result.getString("paralog"),
                        result.getString("residue"),
                        result.getInt("residue_number"),
                        result.getString("functional_group"),
                        result.getLong("contact_count"),
                        result.getLong("unique_ligands"),
                        result.getDouble("fraction"),
                        result.getLong("denominator"),
                        result.getLong("unique_ligands") < 5
                                ? "UNDERPOWERED" : "ADEQUATE_FOR_SCREENING"
                ), RUN_KEY, RUN_KEY);

        List<DcmbContactView> dcmb = jdbc.query("""
                select ligand,paralog,condition,enantiomer,residue,
                       residue_number,functional_group,minimum_distance_a
                from docking.mettl7_functional_group_contact_analysis
                where run_key=? and cohort='DCMB_EXTERNAL'
                order by ligand,paralog,residue_number
                """, (result, row) -> new DcmbContactView(
                        result.getString("ligand"),
                        result.getString("paralog"),
                        result.getString("condition"),
                        result.getString("enantiomer"),
                        result.getString("residue"),
                        result.getInt("residue_number"),
                        result.getString("functional_group"),
                        result.getDouble("minimum_distance_a")
                ), RUN_KEY);

        return new ReportView(
                RUN_KEY,
                scope,
                evidenceDerivedHeadlines(residues),
                residues,
                dcmb
        );
    }

    static HeadlineView evidenceDerivedHeadlines(List<ResidueFunctionalGroupView> rows) {
        Set<String> oxygenGroups = Set.of("ether oxygen", "amide carbonyl", "alcohol/phenol");
        List<ResidueFunctionalGroupView> k151 = rows.stream().filter(r -> r.paralog().equals("7A")
                && r.residueNumber() == 151).toList();
        long oxygen = k151.stream().filter(r -> oxygenGroups.contains(r.functionalGroup()))
                .mapToLong(ResidueFunctionalGroupView::contactCount).sum();
        long ether = k151.stream().filter(r -> r.functionalGroup().equals("ether oxygen"))
                .mapToLong(ResidueFunctionalGroupView::contactCount).sum();
        long k151Total = k151.stream().mapToLong(ResidueFunctionalGroupView::contactCount).sum();

        Set<Integer> faceResidues = Set.of(36, 40, 145);
        Set<String> carbonGroups = Set.of("phenyl", "heteroaromatic ring", "alkyl carbon",
                "methylene linker", "fused aromatic system", "halogenated aryl");
        List<ResidueFunctionalGroupView> face = rows.stream().filter(r -> r.paralog().equals("7B")
                && faceResidues.contains(r.residueNumber())).toList();
        long carbon = face.stream().filter(r -> carbonGroups.contains(r.functionalGroup()))
                .mapToLong(ResidueFunctionalGroupView::contactCount).sum();
        long phenyl = face.stream().filter(r -> r.functionalGroup().equals("phenyl"))
                .mapToLong(ResidueFunctionalGroupView::contactCount).sum();
        long faceTotal = face.stream().mapToLong(ResidueFunctionalGroupView::contactCount).sum();
        return new HeadlineView(summary("oxygen-facing", oxygen, k151Total, "ether oxygen", ether),
                summary("carbon groups", carbon, faceTotal, "phenyl", phenyl),
                "NOT_EVALUATED_BY_THIS_QUERY", "NOT_EVALUATED_BY_THIS_QUERY",
                "NOT_EVALUATED_BY_THIS_QUERY");
    }

    private static String summary(String group, long count, long total, String subgroup, long subcount) {
        return group + " " + count + "/" + total + " (" + percent(count, total) + "); "
                + subgroup + " is " + subcount + "/" + count + " (" + percent(subcount, count) + ")";
    }

    private static String percent(long numerator, long denominator) {
        return denominator == 0 ? "UNAVAILABLE" : String.format(Locale.ROOT, "%.1f%%", 100.0*numerator/denominator);
    }

    public record DatabaseScope(long dockingRuns, long runsWithPoses,
                                long poseRecords, long distinctLigands,
                                long cohortContactRows, long cohortLigands,
                                long dcmbContactRows) {}

    public record HeadlineView(String mettl7aLys151Preference,
                               String mettl7bHydrophobicFacePreference,
                               String mettl7aMixedPharmacophorePattern,
                               String dcmbChemistryMatches7aGrammar,
                               String functionalGroupSelectivityMechanism) {}

    public record ResidueFunctionalGroupView(
            String paralog, String residue, int residueNumber,
            String functionalGroup, long contactCount, long uniqueLigands,
            double fraction, long denominator, String powerFlag) {}

    public record DcmbContactView(
            String ligand, String paralog, String condition,
            String enantiomer, String residue, int residueNumber,
            String functionalGroup, double minimumDistanceA) {}

    public record ReportView(
            String runKey, DatabaseScope databaseScope,
            HeadlineView headlines,
            List<ResidueFunctionalGroupView> residueFunctionalGroups,
            List<DcmbContactView> dcmbContacts) {}
}
