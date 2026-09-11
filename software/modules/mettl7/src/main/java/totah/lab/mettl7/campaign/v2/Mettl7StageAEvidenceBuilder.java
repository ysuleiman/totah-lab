package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds deterministic, non-interpretive Stage-A evidence tables from frozen pose profiles. */
public final class Mettl7StageAEvidenceBuilder {
    private static final List<String> NUMERIC = List.of(
            "athena_hbond_count", "athena_salt_bridge_count",
            "athena_hydrophobic_raw_count", "athena_hydrophobic_refined_count",
            "athena_pi_parallel_count", "athena_pi_t_count", "athena_pi_cation_count",
            "athena_halogen_bond_count", "protein_severe_clash_count",
            "sam_contact_count_le_4p5", "sam_clash_pairs_lt_2p0", "burial_fraction");

    private Mettl7StageAEvidenceBuilder() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: <pose-level.csv> <authoritative-ledger.csv> <output-directory>");
        build(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    static void build(Path poseCsv, Path ledgerCsv, Path output) throws Exception {
        Files.createDirectories(output);
        Table poses = Table.read(poseCsv);
        Table ledger = Table.read(ledgerCsv);
        List<Row> validLedger = ledger.rows().stream()
                .filter(row -> !row.get("technical_status").equals("TECHNICAL_FAILURE")).toList();
        List<Row> failedLedger = ledger.rows().stream()
                .filter(row -> row.get("technical_status").equals("TECHNICAL_FAILURE")).toList();

        Map<String, Row> ledgerByRun = unique(validLedger, "run_id");
        Set<String> observedRuns = poses.rows().stream().map(row -> row.get("run_id"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Family> families = families(poses.rows());
        List<MatrixRow> matrix = matrix(poses.rows(), families, validLedger);

        writeFamilies(output.resolve("METTL7_V2_POSE_FAMILIES.csv"), families);
        writeMatrix(output.resolve("METTL7_V2_RECEPTOR_SPECIES_MECHANISTIC_MATRIX.csv"), matrix);
        writeTechnicalFailures(output.resolve("METTL7_V2_PREDECLARED_TECHNICAL_FAILURES.csv"),
                ledger.header(), failedLedger);
        writeWtMutant(output.resolve("METTL7_V2_WT_MUTANT_EVIDENCE_DELTAS.csv"), matrix);
        writeParalog(output.resolve("METTL7_V2_WT_PARALOG_DIFFERENTIAL_EVIDENCE.csv"), matrix);

        Set<String> unknownRuns = observedRuns.stream().filter(run -> !ledgerByRun.containsKey(run))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> missingRuns = ledgerByRun.keySet().stream().filter(run -> !observedRuns.contains(run))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long expectedPoses = poses.rows().size();
        long uniquePoseKeys = poses.rows().stream().map(row -> row.get("run_id") + "#"
                + row.get("pose_model")).distinct().count();
        long familyPoseSum = families.stream().mapToLong(Family::poseCount).sum();
        long matrixPoseSum = matrix.stream().mapToLong(MatrixRow::poseCount).sum();
        boolean matrixRunsComplete = matrix.stream().allMatch(row -> row.expectedRuns() == 3
                && row.observedRuns() == 3 && row.seedCount() == 3);
        Set<String> thresholdProvenance = poses.rows().stream()
                .map(row -> row.get("athena_threshold_provenance")).collect(Collectors.toSet());
        boolean interpretationFlagsClosed = poses.rows().stream().allMatch(row ->
                row.get("partial_conclusion_authorized").equals("false")
                        && row.get("evidence_status").equals("RAW_COMPUTATIONAL_EVIDENCE"));
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("stage", "A_EVIDENCE_COMPLETENESS_ONLY");
        audit.put("authoritative_ledger_rows", ledger.rows().size());
        audit.put("valid_run_rows", validLedger.size());
        audit.put("predeclared_technical_failure_rows", failedLedger.size());
        audit.put("observed_valid_runs", observedRuns.size());
        audit.put("pose_rows", expectedPoses);
        audit.put("unique_pose_keys", uniquePoseKeys);
        audit.put("family_rows", families.size());
        audit.put("family_pose_count_sum", familyPoseSum);
        audit.put("receptor_species_rows", matrix.size());
        audit.put("matrix_pose_count_sum", matrixPoseSum);
        audit.put("matrix_each_cell_has_three_runs_and_seeds", matrixRunsComplete);
        audit.put("threshold_provenance_values", thresholdProvenance);
        audit.put("interpretation_flags_closed", interpretationFlagsClosed);
        audit.put("unknown_observed_runs", unknownRuns);
        audit.put("missing_valid_runs", missingRuns);
        audit.put("all_valid_runs_present", missingRuns.isEmpty() && unknownRuns.isEmpty()
                && observedRuns.size() == validLedger.size());
        audit.put("pose_keys_unique", uniquePoseKeys == expectedPoses);
        audit.put("technical_failures_preserved", failedLedger.size() == 516);
        audit.put("biological_interpretation_performed", false);
        audit.put("stage_a_pass", missingRuns.isEmpty() && unknownRuns.isEmpty()
                && observedRuns.size() == 1548 && validLedger.size() == 1548
                && failedLedger.size() == 516 && ledger.rows().size() == 2064
                && expectedPoses == 13835 && uniquePoseKeys == expectedPoses
                && familyPoseSum == expectedPoses && matrixPoseSum == expectedPoses
                && matrix.size() == 516 && matrixRunsComplete
                && thresholdProvenance.size() == 1 && interpretationFlagsClosed);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.resolve("STAGE_A_COMPLETENESS_AUDIT.json").toFile(), audit);
    }

    private static List<Family> families(List<Row> poses) {
        Map<String, List<Row>> groups = new TreeMap<>();
        for (Row row : poses) {
            String signature = String.join("|", row.get("contacts_le_4p5"),
                    row.get("athena_interaction_fingerprint"), row.get("sector_39_47"),
                    row.get("sector_144_175"), row.get("sector_195_207"),
                    row.get("sector_228_237"), row.get("productive_geometry_screen"),
                    Integer.parseInt(row.get("sam_clash_pairs_lt_2p0")) == 0 ? "SAM_CLEAR" : "SAM_CLASH");
            String key = row.get("receptor_id") + "\u001f" + row.get("species_id") + "\u001f" + signature;
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<Family> result = new ArrayList<>();
        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            List<Row> rows = entry.getValue(); Row first = rows.getFirst();
            Set<String> seeds = rows.stream().map(row -> row.get("seed"))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String signature = entry.getKey().substring(entry.getKey().indexOf('\u001f',
                    entry.getKey().indexOf('\u001f') + 1) + 1);
            result.add(new Family("PF_" + sha(signature).substring(0, 12), first.get("receptor_id"),
                    first.get("paralog"), first.get("receptor_mutations"), first.get("species_id"),
                    first.get("compound_branch"), signature, rows.size(), seeds.size(),
                    seeds.stream().sorted().collect(Collectors.joining(";")), seeds.size() >= 2,
                    mean(rows, "vina_affinity"), mean(rows, "burial_fraction"),
                    mean(rows, "athena_hbond_count"), mean(rows, "athena_salt_bridge_count"),
                    mean(rows, "athena_hydrophobic_raw_count"),
                    mean(rows, "athena_hydrophobic_refined_count"),
                    count(rows, "productive_geometry_screen", "GEOMETRY_PASS_CHEMISTRY_UNASSESSED"),
                    countZero(rows, "sam_clash_pairs_lt_2p0")));
        }
        return result.stream().sorted(Comparator.comparing(Family::receptorId)
                .thenComparing(Family::speciesId).thenComparing(Family::familyId)).toList();
    }

    private static List<MatrixRow> matrix(List<Row> poses, List<Family> families,
                                           List<Row> validLedger) {
        Map<String, List<Row>> groups = poses.stream().collect(Collectors.groupingBy(
                row -> row.get("receptor_id") + "\u001f" + row.get("species_id"),
                TreeMap::new, Collectors.toList()));
        Map<String, Long> expectedRuns = validLedger.stream().collect(Collectors.groupingBy(
                row -> row.get("receptor_id") + "\u001f" + row.get("species_id"),
                TreeMap::new, Collectors.counting()));
        Map<String, List<Family>> familyGroups = families.stream().collect(Collectors.groupingBy(
                family -> family.receptorId() + "\u001f" + family.speciesId()));
        Map<String, List<Row>> productiveByReceptor = poses.stream()
                .filter(row -> row.get("compound_branch").equals("TSL")
                        || row.get("compound_branch").equals("CAPTOPRIL"))
                .collect(Collectors.groupingBy(row -> row.get("receptor_id")));
        List<MatrixRow> result = new ArrayList<>();
        for (Map.Entry<String, List<Row>> entry : groups.entrySet()) {
            List<Row> rows = entry.getValue(); Row first = rows.getFirst();
            Set<String> seeds = rows.stream().map(row -> row.get("seed")).collect(Collectors.toSet());
            List<Family> fs = familyGroups.getOrDefault(entry.getKey(), List.of());
            Family dominant = fs.stream().sorted(Comparator.comparingInt(Family::seedCount).reversed()
                    .thenComparing(Comparator.comparingInt(Family::poseCount).reversed())
                    .thenComparing(Family::familyId)).findFirst().orElse(null);
            Map<String, Double> means = new LinkedHashMap<>();
            NUMERIC.forEach(column -> means.put(column, mean(rows, column)));
            result.add(new MatrixRow(first, expectedRuns.getOrDefault(entry.getKey(), 0L),
                    rows.stream().map(row -> row.get("run_id")).distinct().count(), rows.size(),
                    seeds.size(), fs.size(), (int) fs.stream().filter(Family::recurrentAcrossSeeds).count(),
                    dominant == null ? "" : dominant.familyId(), means,
                    fractionNonempty(rows, "sector_39_47"), fractionNonempty(rows, "sector_144_175"),
                    fractionNonempty(rows, "sector_195_207"), fractionNonempty(rows, "sector_228_237"),
                    fraction(rows, "productive_geometry_screen", "GEOMETRY_PASS_CHEMISTRY_UNASSESSED"),
                    fractionZero(rows, "sam_clash_pairs_lt_2p0"),
                    fractionZero(rows, "protein_severe_clash_count"),
                    maxDirectContactJaccard(rows,
                            productiveByReceptor.getOrDefault(first.get("receptor_id"), List.of())),
                    "INDETERMINATE_NO_PRESEALED_PRODUCTIVE_FAMILY_MAPPING",
                    "NOT_CLASSIFIED_RAW_SAM_DESCRIPTORS_REPORTED",
                    "INDETERMINATE_NO_GENERALIZED_MAPPING",
                    "INDETERMINATE_NO_PRESEALED_ESCAPE_CUTOFF"));
        }
        return result;
    }

    private static void writeFamilies(Path path, List<Family> rows) throws IOException {
        String header = "pose_family_id,receptor_id,paralog,receptor_mutations,species_id,compound_branch,"
                + "family_signature,pose_count,seed_count,seeds,recurrent_across_seeds,mean_vina_score,"
                + "mean_burial_fraction,mean_hbond_count,mean_salt_count,mean_hydrophobic_raw,"
                + "mean_hydrophobic_refined,near_attack_pass_pose_count,sam_clear_pose_count\n";
        String body = rows.stream().map(Family::csv).collect(Collectors.joining());
        Files.writeString(path, header + body, StandardCharsets.UTF_8);
    }

    private static void writeMatrix(Path path, List<MatrixRow> rows) throws IOException {
        String header = "receptor_id,paralog,receptor_mutations,window_id,compound_branch,species_id,"
                + "stereoisomer,protonation_or_speciation,tautomer,cofactor_state,expected_runs,observed_runs,"
                + "pose_count,seed_count,pose_family_count,recurrent_family_count,dominant_family_id,"
                + NUMERIC.stream().map(name -> "mean_" + name).collect(Collectors.joining(","))
                + ",entrance_sector_pose_fraction,central_productive_sector_pose_fraction,"
                + "rear_sector_pose_fraction,directional_exit_sector_pose_fraction,near_attack_pass_fraction,"
                + "sam_clash_free_fraction,protein_clash_free_fraction,productive_state_family_membership,"
                + "max_direct_contact_jaccard_to_tsl_or_captopril,"
                + "sam_compatibility_class,dcmb_productive_overlap_class,escape_family_class\n";
        Files.writeString(path, header + rows.stream().map(MatrixRow::csv).collect(Collectors.joining()),
                StandardCharsets.UTF_8);
    }

    private static void writeTechnicalFailures(Path path, List<String> header, List<Row> rows) throws IOException {
        StringBuilder out = new StringBuilder(header.stream().map(Mettl7StageAEvidenceBuilder::q)
                .collect(Collectors.joining(","))).append('\n');
        rows.forEach(row -> out.append(header.stream().map(row::get).map(Mettl7StageAEvidenceBuilder::q)
                .collect(Collectors.joining(","))).append('\n'));
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static void writeWtMutant(Path path, List<MatrixRow> rows) throws IOException {
        Map<String, MatrixRow> byKey = rows.stream().collect(Collectors.toMap(
                row -> row.receptorId() + "\u001f" + row.speciesId(), row -> row));
        StringBuilder out = new StringBuilder("mutant_receptor,wt_receptor,paralog,receptor_mutations,species_id,"
                + "delta_hbond,delta_salt,delta_hydrophobic_refined,delta_burial,delta_near_attack_pass_fraction,"
                + "delta_sam_clash_free_fraction,dominant_family_changed\n");
        for (MatrixRow row : rows) {
            if (row.receptorMutations().isBlank()) continue;
            String wtId = row.paralog().equals("METTL7A") ? "A0" : "B0";
            MatrixRow wt = byKey.get(wtId + "\u001f" + row.speciesId());
            if (wt == null) continue;
            out.append(join(row.receptorId(), wtId, row.paralog(), row.receptorMutations(), row.speciesId(),
                    delta(row, wt, "athena_hbond_count"), delta(row, wt, "athena_salt_bridge_count"),
                    delta(row, wt, "athena_hydrophobic_refined_count"), delta(row, wt, "burial_fraction"),
                    row.nearAttackFraction() - wt.nearAttackFraction(),
                    row.samClearFraction() - wt.samClearFraction(),
                    !row.dominantFamilyId().equals(wt.dominantFamilyId()))).append('\n');
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static void writeParalog(Path path, List<MatrixRow> rows) throws IOException {
        Map<String, MatrixRow> byKey = rows.stream().collect(Collectors.toMap(
                row -> row.receptorId() + "\u001f" + row.speciesId(), row -> row));
        StringBuilder out = new StringBuilder("species_id,a_receptor,b_receptor,"
                + "b_minus_a_hbond,b_minus_a_salt,b_minus_a_hydrophobic_refined,b_minus_a_burial,"
                + "b_minus_a_near_attack_pass_fraction,b_minus_a_sam_clash_free_fraction,"
                + "dominant_family_signature_differs\n");
        for (MatrixRow a : rows) {
            if (!a.receptorId().equals("A0")) continue;
            MatrixRow b = byKey.get("B0\u001f" + a.speciesId()); if (b == null) continue;
            out.append(join(a.speciesId(), "A0", "B0", delta(b, a, "athena_hbond_count"),
                    delta(b, a, "athena_salt_bridge_count"),
                    delta(b, a, "athena_hydrophobic_refined_count"), delta(b, a, "burial_fraction"),
                    b.nearAttackFraction() - a.nearAttackFraction(),
                    b.samClearFraction() - a.samClearFraction(),
                    !b.dominantFamilyId().equals(a.dominantFamilyId()))).append('\n');
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static double delta(MatrixRow left, MatrixRow right, String key) {
        return left.means().get(key) - right.means().get(key);
    }
    private static Map<String, Row> unique(List<Row> rows, String key) throws IOException {
        Map<String, Row> result = new LinkedHashMap<>();
        for (Row row : rows) if (result.put(row.get(key), row) != null)
            throw new IOException("Duplicate " + key + ": " + row.get(key));
        return result;
    }
    private static long count(List<Row> rows, String key, String value) {
        return rows.stream().filter(row -> row.get(key).equals(value)).count();
    }
    private static long countZero(List<Row> rows, String key) {
        return rows.stream().filter(row -> Double.parseDouble(row.get(key)) == 0.0).count();
    }
    private static double mean(List<Row> rows, String key) {
        return rows.stream().map(row -> row.get(key)).filter(value -> !value.isBlank())
                .mapToDouble(Double::parseDouble).average().orElse(Double.NaN);
    }
    private static double fraction(List<Row> rows, String key, String value) {
        return rows.isEmpty() ? Double.NaN : (double) count(rows, key, value) / rows.size();
    }
    private static double fractionZero(List<Row> rows, String key) {
        return rows.isEmpty() ? Double.NaN : (double) countZero(rows, key) / rows.size();
    }
    private static double fractionNonempty(List<Row> rows, String key) {
        return rows.isEmpty() ? Double.NaN
                : (double) rows.stream().filter(row -> !row.get(key).isBlank()).count() / rows.size();
    }
    private static double maxDirectContactJaccard(List<Row> rows, List<Row> references) {
        double maximum = 0.0;
        for (Row row : rows) for (Row reference : references) {
            Set<String> left = tokens(row.get("contacts_le_4p5"));
            Set<String> right = tokens(reference.get("contacts_le_4p5"));
            Set<String> union = new LinkedHashSet<>(left); union.addAll(right);
            if (union.isEmpty()) continue;
            Set<String> intersection = new LinkedHashSet<>(left); intersection.retainAll(right);
            maximum = Math.max(maximum, (double) intersection.size() / union.size());
        }
        return maximum;
    }
    private static Set<String> tokens(String value) {
        if (value.isBlank()) return Set.of();
        return Set.of(value.split(";"));
    }
    private static String join(Object... values) {
        return java.util.Arrays.stream(values).map(Mettl7StageAEvidenceBuilder::q)
                .collect(Collectors.joining(","));
    }
    private static String q(Object value) {
        if (value instanceof Double number && !Double.isFinite(number)) return "";
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
    private static String sha(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record Row(Map<String, String> values) { String get(String key) { return values.get(key); } }
    private record Table(List<String> header, List<Row> rows) {
        static Table read(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path); if (lines.isEmpty()) throw new IOException("Empty " + path);
            List<String> header = csv(lines.getFirst()); List<Row> rows = new ArrayList<>();
            for (String line : lines.subList(1, lines.size())) {
                if (line.isBlank()) continue; List<String> values = csv(line);
                if (values.size() != header.size()) throw new IOException("Malformed CSV row in " + path);
                Map<String, String> mapped = new LinkedHashMap<>();
                for (int i = 0; i < header.size(); i++) mapped.put(header.get(i), values.get(i));
                rows.add(new Row(Map.copyOf(mapped)));
            }
            return new Table(List.copyOf(header), List.copyOf(rows));
        }
    }
    private static List<String> csv(String line) throws IOException {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i=0;i<line.length();i++) { char c=line.charAt(i);
            if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}else quoted=!quoted;}
            else if(c==','&&!quoted){fields.add(field.toString());field.setLength(0);}else field.append(c);}
        if(quoted)throw new IOException("Unterminated CSV field"); fields.add(field.toString()); return fields;
    }

    private record Family(String familyId,String receptorId,String paralog,String mutations,
                          String speciesId,String branch,String signature,int poseCount,int seedCount,
                          String seeds,boolean recurrentAcrossSeeds,double meanVina,double meanBurial,
                          double meanHbond,double meanSalt,double meanHydrophobicRaw,
                          double meanHydrophobicRefined,long nearAttackPass,long samClear) {
        String csv(){return join(familyId,receptorId,paralog,mutations,speciesId,branch,signature,poseCount,
                seedCount,seeds,recurrentAcrossSeeds,meanVina,meanBurial,meanHbond,meanSalt,
                meanHydrophobicRaw,meanHydrophobicRefined,nearAttackPass,samClear)+"\n";}
    }
    private record MatrixRow(Row source,long expectedRuns,long observedRuns,int poseCount,int seedCount,
                             int familyCount,int recurrentFamilies,String dominantFamilyId,
                             Map<String,Double> means,double entranceFraction,double centralFraction,
                             double rearFraction,double exitFraction,double nearAttackFraction,
                             double samClearFraction,double proteinClearFraction,double productiveJaccard,
                             String productiveFamily,
                             String samClass,String dcmbOverlap,String escapeClass) {
        String receptorId(){return source.get("receptor_id");} String paralog(){return source.get("paralog");}
        String receptorMutations(){return source.get("receptor_mutations");} String speciesId(){return source.get("species_id");}
        String csv(){List<Object> values=new ArrayList<>(List.of(receptorId(),paralog(),receptorMutations(),
                source.get("window_id"),source.get("compound_branch"),speciesId(),source.get("stereoisomer"),
                source.get("protonation_or_speciation"),source.get("tautomer"),source.get("cofactor_state"),
                expectedRuns,observedRuns,poseCount,seedCount,familyCount,recurrentFamilies,dominantFamilyId));
            NUMERIC.forEach(key->values.add(means.get(key))); values.addAll(List.of(entranceFraction,centralFraction,
                    rearFraction,exitFraction,nearAttackFraction,samClearFraction,proteinClearFraction,
                    productiveFamily,productiveJaccard,samClass,dcmbOverlap,escapeClass));
            return join(values.toArray())+"\n";}
    }
}
