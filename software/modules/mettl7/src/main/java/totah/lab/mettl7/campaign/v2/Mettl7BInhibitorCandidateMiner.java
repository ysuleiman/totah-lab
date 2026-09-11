package totah.lab.mettl7.campaign.v2;

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

/** Frozen-evidence-only, non-scoring METTL7B candidate profiler. */
public final class Mettl7BInhibitorCandidateMiner {
    private Mettl7BInhibitorCandidateMiner() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: <stage-a> <stage-b> <output-directory>");
        build(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    static void build(Path stageA, Path stageB, Path output) throws IOException {
        Files.createDirectories(output);
        Table matrix = Table.read(stageA.resolve("METTL7_V2_RECEPTOR_SPECIES_MECHANISTIC_MATRIX.csv"));
        Table recurrence = Table.read(stageA.resolve("METTL7_V2_RESIDUE_INTERACTION_RECURRENCE.csv"));
        Table deltas = Table.read(stageA.resolve("METTL7_V2_MATCHED_A_B_RESIDUE_INTERACTION_DELTAS.csv"));
        Table mutations = Table.read(stageB.resolve("METTL7_V2_MUTATION_TRANSFER_MATRIX.csv"));
        Map<String, Row> cells = matrix.rows.stream().filter(r -> r.get("receptor_id").equals("A0") || r.get("receptor_id").equals("B0"))
                .collect(Collectors.toMap(r -> r.get("receptor_id") + "\u001f" + r.get("species_id"), r -> r));
        Map<String, List<Row>> recurrent = recurrence.rows.stream().filter(r -> Boolean.parseBoolean(r.get("recurrent_across_seeds")))
                .collect(Collectors.groupingBy(r -> r.get("receptor_id") + "\u001f" + r.get("species_id")));
        Map<String, List<Row>> differential = deltas.rows.stream().collect(Collectors.groupingBy(r -> r.get("species_id")));
        Map<String, List<Row>> mutationBySpecies = mutations.rows.stream().collect(Collectors.groupingBy(r -> r.get("species_id")));
        List<Profile> profiles = cells.values().stream().filter(r -> r.get("receptor_id").equals("A0"))
                .map(r -> profile(r.get("species_id"), cells, recurrent, differential, mutationBySpecies)).sorted(Comparator.comparing(Profile::species)).toList();
        Map<String, Boolean> robust = profiles.stream().collect(Collectors.groupingBy(Profile::branch)).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream()
                        .map(p -> p.bRecognition + "|" + p.aRecognition + "|" + p.bNetwork + "|" + p.samClass)
                        .distinct().count() == 1));
        writeEvidence(output.resolve("METTL7B_SELECTIVE_INHIBITOR_EVIDENCE_MATRIX.csv"), profiles, robust);
        writeShortlist(output.resolve("METTL7B_B_RECOGNITION_SCAFFOLD_SHORTLIST.csv"), profiles, robust);
        writeExclusions(output.resolve("METTL7B_CANDIDATE_EXCLUSION_LOG.csv"), profiles);
    }

    private static Profile profile(String species, Map<String, Row> cells, Map<String, List<Row>> recurrent,
                                   Map<String, List<Row>> differential, Map<String, List<Row>> mutations) {
        Row a = cells.get("A0\u001f" + species), b = cells.get("B0\u001f" + species);
        List<Row> ar = recurrent.getOrDefault("A0\u001f" + species, List.of());
        List<Row> br = recurrent.getOrDefault("B0\u001f" + species, List.of());
        List<Row> ds = differential.getOrDefault(species, List.of());
        int bRegional = (int) br.stream().filter(r -> typed(r.get("dimension")))
                .filter(r -> bRegion(number(r.get("residue")))).count();
        int bDirectAnchors = (int) br.stream().filter(r -> r.get("dimension").equals("DIRECT_CONTACT_4P5"))
                .filter(r -> Set.of(196, 203, 206).contains(number(r.get("residue")))).count();
        int aRegional = (int) ar.stream().filter(r -> typed(r.get("dimension")))
                .filter(r -> aRegion(number(r.get("residue")))).count();
        int bSpecific = (int) ds.stream().filter(r -> typed(r.get("dimension"))).filter(r -> bRegion(number(r.get("residue"))))
                .filter(r -> d(r, "b_pose_fraction") > d(r, "a_pose_fraction") && Integer.parseInt(r.get("b_seed_count")) >= 2).count();
        int aSpecific = (int) ds.stream().filter(r -> typed(r.get("dimension"))).filter(r -> aRegion(number(r.get("residue"))))
                .filter(r -> d(r, "a_pose_fraction") > d(r, "b_pose_fraction") && Integer.parseInt(r.get("a_seed_count")) >= 2).count();
        String bRecognition = bRegional >= 3 && bDirectAnchors >= 1 && integer(b, "recurrent_family_count") >= 2 ? "B_RECOGNITION_STRONG"
                : bRegional >= 1 || bDirectAnchors >= 1 ? "B_RECOGNITION_MODERATE" : "B_RECOGNITION_WEAK";
        String aRecognition = aRegional >= 3 && integer(a, "recurrent_family_count") >= 2 ? "A_RECOGNITION_STRONG"
                : aRegional >= 1 ? "A_RECOGNITION_MODERATE" : "A_RECOGNITION_WEAK";
        String bNetwork = bRegional >= 3 && bDirectAnchors >= 1 ? "B_196_211_NETWORK_STRONG"
                : bRegional >= 1 || bDirectAnchors >= 1 ? "B_196_211_NETWORK_PARTIAL" : "B_196_211_NETWORK_ABSENT";
        String sam = d(b, "sam_clash_free_fraction") == 1.0 ? "SAM_COMPATIBLE"
                : d(b, "sam_clash_free_fraction") == 0.0 ? "SAM_INTERFERING" : "MIXED";
        List<Row> ms = mutations.getOrDefault(species, List.of());
        long partial = ms.stream().filter(r -> r.get("transfer_class").equals("PARTIALLY_TRANSFERRED")).count();
        long technical = ms.stream().filter(r -> r.get("transfer_class").equals("NON_RECIPROCAL_TECHNICAL")).count();
        String mutant = partial > 0 ? "mixed" : technical == ms.size() ? "unresolved" : "unresolved";
        return new Profile(species, a.get("compound_branch"), bRecognition, aRecognition, bNetwork, sam,
                bRegional, bDirectAnchors, bSpecific, aRegional, aSpecific,
                d(a, "mean_burial_fraction"), d(b, "mean_burial_fraction"), integer(a, "recurrent_family_count"),
                integer(b, "recurrent_family_count"), d(a, "sam_clash_free_fraction"), d(b, "sam_clash_free_fraction"),
                d(a, "mean_sam_contact_count_le_4p5"), d(b, "mean_sam_contact_count_le_4p5"),
                d(a, "central_productive_sector_pose_fraction"), d(b, "central_productive_sector_pose_fraction"),
                d(a, "directional_exit_sector_pose_fraction"), d(b, "directional_exit_sector_pose_fraction"),
                d(a, "near_attack_pass_fraction"), d(b, "near_attack_pass_fraction"), partial, technical, mutant);
    }

    private static void writeEvidence(Path path, List<Profile> ps, Map<String, Boolean> robust) throws IOException {
        StringBuilder out = new StringBuilder("species_id,compound_branch,b_recognition,a_recognition,b_196_211_network,"
                + "b_regional_recurrent_features,b_direct_196_203_206_features,b_enriched_or_only_regional_features,"
                + "a_regional_recurrent_features,a_enriched_or_only_regional_features,a_burial_fraction,b_burial_fraction,"
                + "a_recurrent_families,b_recurrent_families,a_sam_clash_free_fraction,b_sam_clash_free_fraction,"
                + "a_mean_sam_contacts,b_mean_sam_contacts,a_productive_pocket_fraction,b_productive_pocket_fraction,"
                + "a_directional_exit_fraction,b_directional_exit_fraction,a_near_attack_fraction,b_near_attack_fraction,"
                + "sam_class,chemical_state_class,recurrent_across_seeds,mutant_support,inhibitor_evidence,evidence_boundary\n");
        for (Profile p : ps) out.append(join(p.species,p.branch,p.bRecognition,p.aRecognition,p.bNetwork,p.bRegional,p.bDirectAnchors,
                p.bSpecific,p.aRegional,p.aSpecific,p.aBurial,p.bBurial,p.aFamilies,p.bFamilies,p.aSamClear,p.bSamClear,
                p.aSamContacts,p.bSamContacts,p.aProductive,p.bProductive,p.aExit,p.bExit,p.aNear,p.bNear,p.samClass,
                robust.get(p.branch)?"CHEMICAL_STATE_ROBUST":"STATE_DEPENDENT",(p.aFamilies>0||p.bFamilies>0)?"yes":"no",p.mutantSupport,
                inhibitorEvidence(p),"STATIC_DOCKING_RECOGNITION_ONLY;NO_AFFINITY_OR_INHIBITION_CLAIM")).append('\n');
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static void writeShortlist(Path path, List<Profile> ps, Map<String, Boolean> robust) throws IOException {
        StringBuilder out = new StringBuilder("rank,category,species_id,compound_branch,b_recognition,a_recognition,b_196_211_network,"
                + "b_direct_anchors,b_recurrent_families,a_recurrent_families,b_minus_a_burial,chemical_state_class,"
                + "why_selected,a_competing_state,nonproductive_trap_support,missing_before_inhibitor_claim\n");
        List<String> order=List.of("NETARSUDIL_MONOCATION","NETARSUDIL_NEUTRAL","BRICS0003_NEUTRAL","ROMIDEPSIN_MONOTHIOLATE_S1","BI187004_TAUTOMER_1_N3");
        int rank=1;
        for(String id:order){Profile p=ps.stream().filter(x->x.species.equals(id)).findFirst().orElse(null);
            if(p==null)continue;
            String category=id.startsWith("BRICS")?"PROSPECTIVE_B_PROBE_NOT_SELECTIVE_IN_V2":"B_RECOGNITION_SCAFFOLD_FOR_REDESIGN";
            String why=id.startsWith("BRICS")?"prospective non-electrophilic probe with recurrent B 196-211 architecture, but V2 also shows a strong recurrent A state":"B-recognition comparator or productive scaffold architecture; suitable as a structural reference, not an inhibitor claim";
            out.append(join(rank++,category,p.species,p.branch,p.bRecognition,p.aRecognition,p.bNetwork,p.bDirectAnchors,p.bFamilies,p.aFamilies,
                    p.bBurial-p.aBurial,robust.get(p.branch)?"CHEMICAL_STATE_ROBUST":"STATE_DEPENDENT",why,
                    p.aRecognition.equals("A_RECOGNITION_WEAK")?"WEAK":"PRESENT","NOT_ESTABLISHED_BY_DOCKING",
                    "matched A/B direct binding and functional assay with SAM; kinetic/reversibility confirmation")).append('\n');}
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static void writeExclusions(Path path,List<Profile>ps)throws IOException{
        StringBuilder out=new StringBuilder("species_id,compound_branch,disposition,reason,evidence_boundary\n");
        for(Profile p:ps){String reason;
            if(p.branch.equals("TSL")||p.branch.equals("CAPTOPRIL")||p.branch.equals("DTT")||p.branch.startsWith("D_PEN")||p.branch.startsWith("L_PEN")||p.branch.startsWith("PRASUGREL")||p.branch.equals("REDUCED_ROMIDEPSIN")||p.branch.equals("THIOGLUCOSE")||p.branch.equals("HS_MINUS_H2S")) reason="known productive or productive-compatible control; B recognition is not inhibitor evidence";
            else if(p.branch.contains("DCMB"))reason="A-preferential inhibitor calibration anchor, not a B candidate";
            else if(p.branch.equals("NETARSUDIL"))reason="B-recognition reference only under task constraint; no inhibitor claim authorized";
            else if(p.branch.startsWith("BI187004"))reason="historical N-methyl acceptor branch; productive-chemistry comparator rather than inhibitor candidate";
            else if(p.branch.equals("METTL7_BRICS_0003"))continue;
            else reason="negative/contrast compound or insufficient B-specific nonproductive-trap evidence";
            out.append(join(p.species,p.branch,"EXCLUDED_FROM_B_INHIBITOR_SHORTLIST",reason,"static docking cannot establish inhibition")).append('\n');}
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static String inhibitorEvidence(Profile p){return p.branch.equals("METTL7_BRICS_0003")?"candidate only":"none";}
    private static boolean typed(String dimension){return !dimension.equals("DIRECT_CONTACT_4P5")&&!dimension.equals("SHELL_CONTACT_4P5_TO_8P0");}
    private static boolean bRegion(int n){return n>=145&&n<=152||n>=196&&n<=211;}
    private static boolean aRegion(int n){return n>=28&&n<=47||Set.of(99,126,128,149,151,195,196,197,198,199,200,201).contains(n);}
    private static int number(String residue){String digits=residue.replaceAll("\\D","");return digits.isEmpty()?-1:Integer.parseInt(digits);}
    private static int integer(Row r,String k){return Integer.parseInt(r.get(k));}
    private static double d(Row r,String k){return Double.parseDouble(r.get(k));}
    private static String join(Object...v){return java.util.Arrays.stream(v).map(Mettl7BInhibitorCandidateMiner::q).collect(Collectors.joining(","));}
    private static String q(Object v){return "\""+String.valueOf(v).replace("\"","\"\"")+"\"";}
    private record Profile(String species,String branch,String bRecognition,String aRecognition,String bNetwork,String samClass,
                           int bRegional,int bDirectAnchors,int bSpecific,int aRegional,int aSpecific,double aBurial,double bBurial,
                           int aFamilies,int bFamilies,double aSamClear,double bSamClear,double aSamContacts,double bSamContacts,
                           double aProductive,double bProductive,double aExit,double bExit,double aNear,double bNear,long partialMutants,
                           long technicalMutants,String mutantSupport){}
    private record Row(Map<String,String>v){String get(String k){String x=v.get(k);if(x==null)throw new IllegalArgumentException("Missing "+k);return x;}}
    private record Table(List<Row>rows){static Table read(Path p)throws IOException{List<String>l=Files.readAllLines(p);List<String>h=csv(l.getFirst());List<Row>r=new ArrayList<>();for(String s:l.subList(1,l.size())){if(s.isBlank())continue;List<String>x=csv(s);if(x.size()!=h.size())throw new IOException("Malformed "+p);Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<h.size();i++)m.put(h.get(i),x.get(i));r.add(new Row(Map.copyOf(m)));}return new Table(List.copyOf(r));}}
    private static List<String>csv(String s)throws IOException{List<String>f=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='\"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='\"'){b.append('\"');i++;}else q=!q;}else if(c==','&&!q){f.add(b.toString());b.setLength(0);}else b.append(c);}if(q)throw new IOException("Unclosed quote");f.add(b.toString());return f;}
}
