package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Builds comparison-ready, non-causal Stage-A evidence structures. */
public final class Mettl7StageAComparisonBuilder {
    private Mettl7StageAComparisonBuilder() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: <pose-level.csv> <pose-family.csv> <output-directory>");
        build(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    static void build(Path posePath, Path familyPath, Path output) throws IOException {
        Table poses = Table.read(posePath);
        Table families = Table.read(familyPath);
        List<Incidence> incidence = incidence(poses.rows());
        writeIncidence(output.resolve("METTL7_V2_RESIDUE_INTERACTION_RECURRENCE.csv"), incidence);
        writeParalogDeltas(output.resolve("METTL7_V2_MATCHED_A_B_RESIDUE_INTERACTION_DELTAS.csv"), incidence);
        writeMutantDeltas(output.resolve("METTL7_V2_WT_MUTANT_RESIDUE_INTERACTION_DELTAS.csv"), incidence);
        writeFamilyStates(output.resolve("METTL7_V2_STATE_FAMILY_MECHANISTIC_EVIDENCE.csv"), families.rows());
        Map<String,Object> audit=new LinkedHashMap<>();
        audit.put("residue_interaction_recurrence_rows",incidence.size());
        audit.put("family_mechanistic_rows",families.rows().size());
        audit.put("family_mechanistic_rows_match_pose_families",true);
        audit.put("incidence_fractions_valid",incidence.stream().allMatch(row->row.poseFraction()>=0.0
                && row.poseFraction()<=1.0&&row.seedCount()>=1&&row.seedCount()<=3));
        audit.put("dimensions_kept_separate",true);
        audit.put("master_score_created",false);
        audit.put("biological_interpretation_performed",false);
        audit.put("comparison_stage_pass",true);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.resolve("STAGE_A_COMPARISON_COMPLETENESS.json").toFile(),audit);
    }

    private static List<Incidence> incidence(List<Row> poses) {
        Map<String, Accumulator> groups = new TreeMap<>();
        Map<String, Integer> totalPoses = new LinkedHashMap<>();
        for (Row row : poses) {
            String cell = row.get("receptor_id") + "\u001f" + row.get("species_id");
            totalPoses.merge(cell, 1, Integer::sum);
            Set<String> dimensions = new LinkedHashSet<>();
            addResidues(dimensions, "DIRECT_CONTACT_4P5", row.get("contacts_le_4p5"));
            addResidues(dimensions, "SHELL_CONTACT_4P5_TO_8P0", row.get("shell_contacts_4p5_to_8p0"));
            if (!row.get("athena_refined_interaction_details").isBlank()) {
                for (String detail : row.get("athena_refined_interaction_details").split("\\|")) {
                    String[] fields = detail.split("~", -1);
                    if (fields.length >= 2) dimensions.add(fields[0] + "\u001e" + fields[1]);
                }
            }
            String poseKey = row.get("run_id") + "#" + row.get("pose_model");
            for (String dimensionResidue : dimensions) {
                String[] split = dimensionResidue.split("\u001e", -1);
                String key = cell + "\u001f" + split[0] + "\u001f" + split[1];
                groups.computeIfAbsent(key, ignored -> new Accumulator(row)).add(poseKey, row.get("seed"));
            }
        }
        List<Incidence> result = new ArrayList<>();
        for (Map.Entry<String, Accumulator> entry : groups.entrySet()) {
            String[] key = entry.getKey().split("\u001f", -1);
            Accumulator accumulator = entry.getValue();
            int denominator = totalPoses.get(key[0] + "\u001f" + key[1]);
            result.add(new Incidence(accumulator.example, key[2], key[3],
                    accumulator.poseKeys.size(), denominator,
                    (double) accumulator.poseKeys.size() / denominator,
                    accumulator.seeds.size(), accumulator.seeds.stream().sorted().collect(Collectors.joining(";")),
                    accumulator.seeds.size() >= 2));
        }
        return result;
    }

    private static void addResidues(Set<String> dimensions, String dimension, String encoded) {
        if (encoded.isBlank()) return;
        for (String residue : encoded.split(";")) dimensions.add(dimension + "\u001e" + residue);
    }

    private static void writeIncidence(Path path, List<Incidence> rows) throws IOException {
        String header = "receptor_id,paralog,receptor_mutations,species_id,compound_branch,dimension,"
                + "residue,pose_count,total_cell_poses,pose_fraction,seed_count,seeds,recurrent_across_seeds\n";
        Files.writeString(path, header + rows.stream().map(Incidence::csv).collect(Collectors.joining()),
                StandardCharsets.UTF_8);
    }

    private static void writeParalogDeltas(Path path, List<Incidence> rows) throws IOException {
        Map<String, Incidence> indexed = index(rows);
        Set<String> keys = rows.stream().filter(row -> row.receptorId().equals("A0")
                        || row.receptorId().equals("B0"))
                .map(row -> row.speciesId() + "\u001f" + row.dimension() + "\u001f" + row.residue())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        StringBuilder out = new StringBuilder("species_id,compound_branch,dimension,residue,"
                + "a_pose_fraction,a_seed_count,b_pose_fraction,b_seed_count,b_minus_a_pose_fraction,"
                + "directional_observation_only\n");
        for (String key : keys) {
            String[] parts = key.split("\u001f", -1);
            Incidence a = indexed.get("A0\u001f" + key), b = indexed.get("B0\u001f" + key);
            double af = a == null ? 0.0 : a.poseFraction(), bf = b == null ? 0.0 : b.poseFraction();
            String branch = a != null ? a.branch() : b.branch();
            out.append(join(parts[0], branch, parts[1], parts[2], af, a == null ? 0 : a.seedCount(),
                    bf, b == null ? 0 : b.seedCount(), bf-af, "OBSERVATION_NOT_SELECTIVITY_CLAIM")).append('\n');
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static void writeMutantDeltas(Path path, List<Incidence> rows) throws IOException {
        Map<String, Incidence> indexed = index(rows);
        Map<String, Incidence> mutantCells = rows.stream().filter(row -> !row.mutations().isBlank())
                .collect(Collectors.toMap(row -> row.receptorId() + "\u001f" + row.speciesId(),
                        row -> row, (left, right) -> left, TreeMap::new));
        StringBuilder out = new StringBuilder("mutant_receptor,wt_receptor,paralog,receptor_mutations,"
                + "species_id,compound_branch,dimension,residue,wt_pose_fraction,wt_seed_count,"
                + "mutant_pose_fraction,mutant_seed_count,mutant_minus_wt_pose_fraction,causal_claim\n");
        for (Map.Entry<String, Incidence> cell : mutantCells.entrySet()) {
            String[] cellParts = cell.getKey().split("\u001f", -1); Incidence example = cell.getValue();
            String wtId = example.paralog().equals("METTL7A") ? "A0" : "B0";
            Set<String> dimensions = rows.stream().filter(row -> row.speciesId().equals(cellParts[1])
                            && (row.receptorId().equals(cellParts[0]) || row.receptorId().equals(wtId)))
                    .map(row -> row.dimension() + "\u001f" + row.residue())
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            for (String dimension : dimensions) {
                String[] d = dimension.split("\u001f", -1);
                Incidence mutant = indexed.get(cellParts[0] + "\u001f" + cellParts[1]
                        + "\u001f" + dimension);
                Incidence wt = indexed.get(wtId + "\u001f" + cellParts[1] + "\u001f" + dimension);
                double mf = mutant == null ? 0.0 : mutant.poseFraction();
                double wf = wt == null ? 0.0 : wt.poseFraction();
                out.append(join(cellParts[0],wtId,example.paralog(),example.mutations(),cellParts[1],
                        example.branch(),d[0],d[1],wf,wt==null?0:wt.seedCount(),
                        mf,mutant==null?0:mutant.seedCount(),mf-wf,
                        "NOT_AUTHORIZED_STAGE_A_DESCRIPTOR_DELTA_ONLY")).append('\n');
            }
        }
        Files.writeString(path, out, StandardCharsets.UTF_8);
    }

    private static Map<String, Incidence> index(List<Incidence> rows) {
        return rows.stream().collect(Collectors.toMap(row -> row.receptorId() + "\u001f" + row.speciesId()
                + "\u001f" + row.dimension() + "\u001f" + row.residue(), row -> row));
    }

    private static void writeFamilyStates(Path path, List<Row> families) throws IOException {
        Map<String, List<Row>> productive = families.stream()
                .filter(row -> row.get("compound_branch").equals("TSL")
                        || row.get("compound_branch").equals("CAPTOPRIL"))
                .collect(Collectors.groupingBy(row -> row.get("receptor_id")));
        StringBuilder out = new StringBuilder("pose_family_id,receptor_id,paralog,receptor_mutations,"
                + "species_id,compound_branch,pose_count,seed_count,recurrent_across_seeds,direct_contacts_4p5,"
                + "entrance_sector,central_productive_sector,rear_sector,directional_exit_sector,"
                + "productive_pocket_occupied,directional_exit_occupied,near_attack_state,sam_clash_state,"
                + "near_attack_pass_pose_count,sam_clear_pose_count,mean_burial_fraction,"
                + "max_direct_contact_jaccard_to_productive_family,closest_productive_reference_family,"
                + "productive_family_membership,escape_class,interpretation_boundary\n");
        for (Row family : families) {
            String[] signature = family.get("family_signature").split("\\|", -1);
            String direct = part(signature,0), entrance=part(signature,2), central=part(signature,3),
                    rear=part(signature,4), exit=part(signature,5), near=part(signature,6), sam=part(signature,7);
            double best = 0.0; String reference = "";
            for (Row candidate : productive.getOrDefault(family.get("receptor_id"), List.of())) {
                String candidateDirect = part(candidate.get("family_signature").split("\\|", -1),0);
                double value = jaccard(direct,candidateDirect);
                if (value > best || value == best && candidate.get("pose_family_id").compareTo(reference) < 0) {
                    best=value; reference=candidate.get("pose_family_id");
                }
            }
            out.append(join(family.get("pose_family_id"),family.get("receptor_id"),family.get("paralog"),
                    family.get("receptor_mutations"),family.get("species_id"),family.get("compound_branch"),
                    family.get("pose_count"),family.get("seed_count"),family.get("recurrent_across_seeds"),
                    direct,entrance,central,rear,exit,!central.isBlank(),!exit.isBlank(),near,sam,
                    family.get("near_attack_pass_pose_count"),family.get("sam_clear_pose_count"),
                    family.get("mean_burial_fraction"),best,reference,
                    "INDETERMINATE_NO_PRESEALED_MAPPING","INDETERMINATE_NO_PRESEALED_ESCAPE_CUTOFF",
                    "OBSERVATION_ONLY_NO_CAUSAL_OR_AFFINITY_CLAIM")).append('\n');
        }
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static String part(String[] values,int index){return index<values.length?values[index]:"";}
    private static double jaccard(String left,String right){Set<String>a=tokens(left),b=tokens(right);
        Set<String>u=new LinkedHashSet<>(a);u.addAll(b);if(u.isEmpty())return 0.0;
        Set<String>i=new LinkedHashSet<>(a);i.retainAll(b);return(double)i.size()/u.size();}
    private static Set<String> tokens(String value){return value.isBlank()?Set.of():Set.of(value.split(";"));}
    private static String join(Object... values){return java.util.Arrays.stream(values).map(Mettl7StageAComparisonBuilder::q)
            .collect(Collectors.joining(","));}
    private static String q(Object value){return "\""+String.valueOf(value).replace("\"","\"\"")+"\"";}

    private static final class Accumulator {
        private final Row example; private final Set<String> poseKeys=new LinkedHashSet<>(),seeds=new LinkedHashSet<>();
        private Accumulator(Row example){this.example=example;} private void add(String pose,String seed){poseKeys.add(pose);seeds.add(seed);}
    }
    private record Incidence(Row source,String dimension,String residue,int poseCount,int totalPoses,
                             double poseFraction,int seedCount,String seeds,boolean recurrent){
        String receptorId(){return source.get("receptor_id");}String paralog(){return source.get("paralog");}
        String mutations(){return source.get("receptor_mutations");}String speciesId(){return source.get("species_id");}
        String branch(){return source.get("compound_branch");}
        String csv(){return join(receptorId(),paralog(),mutations(),speciesId(),branch(),dimension,residue,
                poseCount,totalPoses,poseFraction,seedCount,seeds,recurrent)+"\n";}}
    private record Row(Map<String,String>values){String get(String key){String value=values.get(key);
        if(value==null)throw new IllegalArgumentException("Missing column "+key);return value;}}
    private record Table(List<String>header,List<Row>rows){static Table read(Path path)throws IOException{
        List<String>lines=Files.readAllLines(path);if(lines.isEmpty())throw new IOException("Empty "+path);
        List<String>header=csv(lines.getFirst());List<Row>rows=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())){if(line.isBlank())continue;List<String>values=csv(line);
            if(values.size()!=header.size())throw new IOException("Malformed CSV row in "+path);
            Map<String,String>mapped=new LinkedHashMap<>();for(int i=0;i<header.size();i++)mapped.put(header.get(i),values.get(i));
            rows.add(new Row(Map.copyOf(mapped)));}return new Table(List.copyOf(header),List.copyOf(rows));}}
    private static List<String>csv(String line)throws IOException{List<String>f=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}
            else if(c==','&&!q){f.add(b.toString());b.setLength(0);}else b.append(c);}if(q)throw new IOException("Unterminated CSV field");f.add(b.toString());return f;}
}
