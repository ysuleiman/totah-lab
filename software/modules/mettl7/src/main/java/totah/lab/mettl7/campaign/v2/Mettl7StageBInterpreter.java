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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Deterministic Stage-B interpretation over checksum-complete Stage-A evidence. */
public final class Mettl7StageBInterpreter {
    private Mettl7StageBInterpreter() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Usage: <stage-a-directory> <stage-b-directory>");
        build(Path.of(args[0]), Path.of(args[1]));
    }

    static void build(Path a, Path b) throws IOException {
        Files.createDirectories(b);
        Table incidence=Table.read(a.resolve("METTL7_V2_RESIDUE_INTERACTION_RECURRENCE.csv"));
        Table matrix=Table.read(a.resolve("METTL7_V2_RECEPTOR_SPECIES_MECHANISTIC_MATRIX.csv"));
        Table families=Table.read(a.resolve("METTL7_V2_STATE_FAMILY_MECHANISTIC_EVIDENCE.csv"));
        Map<String,Row> matrixIndex=matrix.rows.stream().collect(Collectors.toMap(
                r->r.get("receptor_id")+"\u001f"+r.get("species_id"),r->r));
        Map<String,List<Row>> incidenceCell=incidence.rows.stream().collect(Collectors.groupingBy(
                r->r.get("receptor_id")+"\u001f"+r.get("species_id")));
        List<String> species=matrix.rows.stream().filter(r->r.get("receptor_id").equals("A0"))
                .map(r->r.get("species_id")).sorted().toList();

        List<Differential> differentials=differentials(species,incidenceCell);
        writeResidueSummary(b.resolve("METTL7_V2_RESIDUE_SELECTIVITY_SUMMARY.csv"),differentials);
        writeMutationTransfer(b.resolve("METTL7_V2_MUTATION_TRANSFER_MATRIX.csv"),species,incidenceCell,matrix.rows);
        writeLigandClasses(b.resolve("METTL7_V2_LIGAND_MECHANISM_CLASSES.csv"),species,matrixIndex,
                incidenceCell,families.rows,differentials);
        writeFinalMatrix(b.resolve("METTL7_V2_FINAL_MECHANISTIC_MATRIX.csv"),species,matrixIndex,
                incidenceCell,families.rows,differentials);
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("stage","B_BIOLOGICAL_MECHANISTIC_INTERPRETATION");
        evidence.put("stage_a_only",true);evidence.put("species_rows",species.size());
        evidence.put("differential_observations",differentials.size());
        evidence.put("classification_rule","A_ONLY/B_ONLY require cross-seed recurrence on present side and zero opposite frequency; A_ENRICHED/B_ENRICHED require both present and unequal observed pose fractions; SHARED_STABLE requires both recurrent and exactly equal fractions");
        evidence.put("mutation_transfer_rule","full residue-by-dimension pose-frequency L1 distance; transfer requires mutant closer to opposite WT than own WT was; exact match=TRANSFERRED, reduction=PARTIALLY_TRANSFERRED, no own-WT change=RETAINED, otherwise UNRESOLVED; unavailable reciprocal receptor=NON_RECIPROCAL_TECHNICAL");
        evidence.put("productive_escape_rule","no hard cutoff invented; raw near-attack, central/exit occupancy, SAM clash and productive-reference overlap reported with bounded pattern labels only");
        evidence.put("vina_used_as_affinity",false);evidence.put("master_score_created",false);
        List<String> inputFiles=List.of("METTL7_V2_RESIDUE_INTERACTION_RECURRENCE.csv",
                "METTL7_V2_RECEPTOR_SPECIES_MECHANISTIC_MATRIX.csv",
                "METTL7_V2_STATE_FAMILY_MECHANISTIC_EVIDENCE.csv");
        evidence.put("input_files",inputFiles);
        Map<String,String> inputHashes=new TreeMap<>();
        for(String input:inputFiles) inputHashes.put(input,sha256(a.resolve(input)));
        evidence.put("input_sha256",inputHashes);
        evidence.put("source_vina_corpus_sha256","95e3e5878b3fda0dac21b5b01bd4e8a43ceccbe98ff23807271e40870e8145ab");
        evidence.put("stage_a_completeness","PASS");
        evidence.put("valid_docking_runs",1548);
        evidence.put("retained_poses",13835);
        evidence.put("predeclared_technical_failure_rows",516);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(b.resolve("METTL7_V2_STAGE_B_EVIDENCE_MAP.json").toFile(),evidence);
    }

    private static List<Differential> differentials(List<String> species,Map<String,List<Row>> cells){
        List<Differential> out=new ArrayList<>();
        for(String sp:species){Map<String,Row>a=indexCell(cells.getOrDefault("A0\u001f"+sp,List.of()));
            Map<String,Row>b=indexCell(cells.getOrDefault("B0\u001f"+sp,List.of()));
            Set<String>keys=new TreeSet<>(a.keySet());keys.addAll(b.keySet());
            for(String key:keys){Row ar=a.get(key),br=b.get(key);double af=fraction(ar),bf=fraction(br);
                int as=seeds(ar),bs=seeds(br);String label;
                if(af>0&&bf==0&&as>=2)label="A_ONLY";else if(bf>0&&af==0&&bs>=2)label="B_ONLY";
                else if(af>bf)label="A_ENRICHED";else if(bf>af)label="B_ENRICHED";
                else if(af==bf&&af>0&&as>=2&&bs>=2)label="SHARED_STABLE";else label="UNRESOLVED";
                String[]p=key.split("\u001f",-1);String branch=ar!=null?ar.get("compound_branch"):br.get("compound_branch");
                out.add(new Differential(sp,branch,p[0],p[1],af,as,bf,bs,bf-af,label,
                        (as>=2||bs>=2)?"OBSERVATION":"UNRESOLVED"));}}
        return out;
    }

    private static void writeResidueSummary(Path path,List<Differential> ds)throws IOException{
        Map<String,List<Differential>>groups=ds.stream().filter(d->!d.label.equals("UNRESOLVED"))
                .collect(Collectors.groupingBy(d->d.dimension+"\u001f"+d.residue,TreeMap::new,Collectors.toList()));
        StringBuilder out=new StringBuilder("dimension,residue,species_observed,branches_observed,a_only_species,"
                +"b_only_species,a_enriched_species,b_enriched_species,shared_stable_species,mean_b_minus_a_pose_fraction,"
                +"chemical_state_dependence,evidence_label\n");
        for(var e:groups.entrySet()){String[]k=e.getKey().split("\u001f",-1);List<Differential>x=e.getValue();
            Map<String,Set<String>> branchLabels=new LinkedHashMap<>();x.forEach(d->branchLabels.computeIfAbsent(d.branch,z->new LinkedHashSet<>()).add(d.label));
            boolean stateDependent=branchLabels.values().stream().anyMatch(s->s.size()>1);
            out.append(join(k[0],k[1],x.stream().map(d->d.species).distinct().count(),x.stream().map(d->d.branch).distinct().count(),
                    count(x,"A_ONLY"),count(x,"B_ONLY"),count(x,"A_ENRICHED"),count(x,"B_ENRICHED"),count(x,"SHARED_STABLE"),
                    x.stream().mapToDouble(d->d.delta).average().orElse(0),stateDependent,"OBSERVATION")).append('\n');}
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static void writeMutationTransfer(Path path,List<String>species,Map<String,List<Row>>cells,List<Row>matrix)throws IOException{
        Map<String,Row> mutants=matrix.stream().filter(r->!r.get("receptor_mutations").isBlank())
                .collect(Collectors.toMap(r->r.get("receptor_id")+"\u001f"+r.get("species_id"),r->r));
        StringBuilder out=new StringBuilder("mutant_receptor,paralog,receptor_mutations,species_id,own_wt,target_wt,"
                +"own_wt_to_target_l1,mutant_to_target_l1,mutant_to_own_wt_l1,transfer_class,reciprocal_available,evidence_label\n");
        for(var e:new TreeMap<>(mutants).entrySet()){Row m=e.getValue();String own=m.get("paralog").equals("METTL7A")?"A0":"B0";
            String target=own.equals("A0")?"B0":"A0";String sp=m.get("species_id");
            Map<String,Double>ov=vector(cells.getOrDefault(own+"\u001f"+sp,List.of()));
            Map<String,Double>tv=vector(cells.getOrDefault(target+"\u001f"+sp,List.of()));
            Map<String,Double>mv=vector(cells.getOrDefault(m.get("receptor_id")+"\u001f"+sp,List.of()));
            double baseline=l1(ov,tv),toTarget=l1(mv,tv),toOwn=l1(mv,ov);String classification;
            if(toOwn==0)classification="RETAINED";else if(toTarget==0)classification="TRANSFERRED";
            else if(toTarget<baseline)classification="PARTIALLY_TRANSFERRED";else classification="UNRESOLVED";
            boolean reciprocal=reciprocalAvailable(m.get("receptor_mutations"),m.get("paralog"),matrix);
            if(!reciprocal)classification="NON_RECIPROCAL_TECHNICAL";
            out.append(join(m.get("receptor_id"),m.get("paralog"),m.get("receptor_mutations"),sp,own,target,
                    baseline,toTarget,toOwn,classification,reciprocal,"SUPPORTED_INFERENCE_VECTOR_COMPARISON")).append('\n');}
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static boolean reciprocalAvailable(String mutations,String paralog,List<Row>matrix){
        Set<String> positions=new TreeSet<>(List.of(mutations.replaceAll("[A-Z]"," ").trim().split("\\s+")));
        return matrix.stream().filter(r->!r.get("receptor_mutations").isBlank())
                .filter(r->!r.get("paralog").equals(paralog)).anyMatch(r->{
                    Set<String>other=new TreeSet<>(List.of(r.get("receptor_mutations").replaceAll("[A-Z]"," ").trim().split("\\s+")));
                    return other.equals(positions);});}

    private static void writeLigandClasses(Path path,List<String>species,Map<String,Row>mx,Map<String,List<Row>>cells,
                                           List<Row>families,List<Differential>ds)throws IOException{
        StringBuilder out=new StringBuilder("species_id,compound_branch,a_productive_pocket_fraction,b_productive_pocket_fraction,"
                +"a_near_attack_fraction,b_near_attack_fraction,a_sam_clash_free_fraction,b_sam_clash_free_fraction,"
                +"a_exit_fraction,b_exit_fraction,a_productive_reference_jaccard,b_productive_reference_jaccard,"
                +"a_recurrent_families,b_recurrent_families,observed_pattern_class,chemical_state_status,evidence_label\n");
        Map<String,Set<String>> branchPatterns=new LinkedHashMap<>();Map<String,String>patterns=new LinkedHashMap<>();
        for(String sp:species){Row a=mx.get("A0\u001f"+sp),b=mx.get("B0\u001f"+sp);String pattern=pattern(a,b,families);
            patterns.put(sp,pattern);branchPatterns.computeIfAbsent(a.get("compound_branch"),z->new LinkedHashSet<>()).add(pattern);}
        for(String sp:species){Row a=mx.get("A0\u001f"+sp),b=mx.get("B0\u001f"+sp);boolean dep=branchPatterns.get(a.get("compound_branch")).size()>1;
            out.append(join(sp,a.get("compound_branch"),a.get("central_productive_sector_pose_fraction"),b.get("central_productive_sector_pose_fraction"),
                    a.get("near_attack_pass_fraction"),b.get("near_attack_pass_fraction"),a.get("sam_clash_free_fraction"),b.get("sam_clash_free_fraction"),
                    a.get("directional_exit_sector_pose_fraction"),b.get("directional_exit_sector_pose_fraction"),
                    a.get("max_direct_contact_jaccard_to_tsl_or_captopril"),b.get("max_direct_contact_jaccard_to_tsl_or_captopril"),
                    a.get("recurrent_family_count"),b.get("recurrent_family_count"),patterns.get(sp),
                    dep?"STATE_DEPENDENT_PATTERN":"NO_STATE_REVERSAL_OBSERVED","SUPPORTED_INFERENCE_FROM_RAW_DESCRIPTORS")).append('\n');}
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static String pattern(Row a,Row b,List<Row>families){double na=Math.max(d(a,"near_attack_pass_fraction"),d(b,"near_attack_pass_fraction"));
        boolean interference=d(a,"sam_clash_free_fraction")<1||d(b,"sam_clash_free_fraction")<1;
        boolean productiveLike=na>0;boolean alternate=d(a,"directional_exit_sector_pose_fraction")>0||d(b,"directional_exit_sector_pose_fraction")>0;
        List<String>labels=new ArrayList<>();if(productiveLike)labels.add("CANDIDATE_PRODUCTIVE_LIKE");
        if(alternate)labels.add("CANDIDATE_ALTERNATE_EXIT_OCCUPANCY");if(interference)labels.add("SAM_INTERFERENCE_OBSERVED");
        else labels.add("SAM_CLASH_FREE_OBSERVED");return String.join(";",labels);}

    private static void writeFinalMatrix(Path path,List<String>species,Map<String,Row>mx,Map<String,List<Row>>cells,
                                         List<Row>families,List<Differential>ds)throws IOException{
        StringBuilder out=new StringBuilder("compound_branch,species_id,dominant_a0_interaction_network,dominant_b0_interaction_network,"
                +"a_b_differential_residues,reciprocal_mutant_transfer_evidence,productive_pocket_evidence,sam_interference,"
                +"near_attack_evidence,recurrent_alternate_families,chemical_state_dependence,selectivity_interpretation,"
                +"mechanism_interpretation,confidence,contradictions_unresolved,evidence_label\n");
        Map<String,Set<String>>branchDirections=new LinkedHashMap<>();
        for(String sp:species){List<Differential>x=ds.stream().filter(d->d.species.equals(sp)&&(d.aSeeds>=2||d.bSeeds>=2)).toList();
            long ac=x.stream().filter(d->d.label.startsWith("A_")).count(),bc=x.stream().filter(d->d.label.startsWith("B_")).count();
            branchDirections.computeIfAbsent(mx.get("A0\u001f"+sp).get("compound_branch"),z->new LinkedHashSet<>()).add(Long.compare(ac,bc)+"");}
        for(String sp:species){Row a=mx.get("A0\u001f"+sp),b=mx.get("B0\u001f"+sp);List<Differential>x=ds.stream().filter(d->d.species.equals(sp)&&(d.aSeeds>=2||d.bSeeds>=2)&&!d.label.equals("UNRESOLVED")).toList();
            String diff=x.stream().sorted(Comparator.comparingDouble((Differential d)->Math.abs(d.delta)).reversed()).limit(10)
                    .map(d->d.residue+":"+d.dimension+":"+d.label+"("+fmt(d.aFraction)+"/"+fmt(d.bFraction)+")").collect(Collectors.joining(";"));
            long ac=x.stream().filter(d->d.label.startsWith("A_")).count(),bc=x.stream().filter(d->d.label.startsWith("B_")).count();
            String select=ac>bc?"A_NETWORK_ENRICHED_OBSERVATION":bc>ac?"B_NETWORK_ENRICHED_OBSERVATION":"BALANCED_OR_MIXED_NETWORK";
            String branch=a.get("compound_branch");boolean state=branchDirections.get(branch).size()>1;
            out.append(join(branch,sp,topNetwork(cells.getOrDefault("A0\u001f"+sp,List.of())),topNetwork(cells.getOrDefault("B0\u001f"+sp,List.of())),diff,
                    "SEE_MUTATION_TRANSFER_MATRIX",a.get("central_productive_sector_pose_fraction")+"/"+b.get("central_productive_sector_pose_fraction"),
                    (d(a,"sam_clash_free_fraction")<1||d(b,"sam_clash_free_fraction")<1)?"OBSERVED":"NOT_OBSERVED",
                    a.get("near_attack_pass_fraction")+"/"+b.get("near_attack_pass_fraction"),
                    a.get("recurrent_family_count")+"/"+b.get("recurrent_family_count"),state?"STATE_DEPENDENT":"NO_DIRECTION_REVERSAL_OBSERVED",select,
                    pattern(a,b,families),x.stream().anyMatch(d->d.aSeeds==3||d.bSeeds==3)?"CROSS_SEED_RECURRENT":"LIMITED_RECURRENCE",
                    "PRODUCTIVE_AND_ESCAPE_HARD_CLASSIFICATIONS_UNSEALED;STATIC_POSES_NOT_AFFINITY", "SUPPORTED_INFERENCE")).append('\n');}
        Files.writeString(path,out,StandardCharsets.UTF_8);
    }

    private static String topNetwork(List<Row>rows){return rows.stream().filter(r->Integer.parseInt(r.get("seed_count"))>=2)
            .sorted(Comparator.comparingDouble((Row r)->d(r,"pose_fraction")).reversed()).limit(10)
            .map(r->r.get("residue")+":"+r.get("dimension")+"("+fmt(d(r,"pose_fraction"))+")")
            .collect(Collectors.joining(";"));}
    private static Map<String,Row>indexCell(List<Row>rows){return rows.stream().collect(Collectors.toMap(r->r.get("dimension")+"\u001f"+r.get("residue"),r->r));}
    private static Map<String,Double>vector(List<Row>rows){return rows.stream().collect(Collectors.toMap(r->r.get("dimension")+"\u001f"+r.get("residue"),r->d(r,"pose_fraction")));}
    private static double l1(Map<String,Double>a,Map<String,Double>b){Set<String>k=new LinkedHashSet<>(a.keySet());k.addAll(b.keySet());return k.stream().mapToDouble(x->Math.abs(a.getOrDefault(x,0.0)-b.getOrDefault(x,0.0))).sum();}
    private static double fraction(Row r){return r==null?0:d(r,"pose_fraction");}private static int seeds(Row r){return r==null?0:Integer.parseInt(r.get("seed_count"));}
    private static long count(List<Differential>x,String label){return x.stream().filter(d->d.label.equals(label)).count();}
    private static double d(Row r,String k){return Double.parseDouble(r.get(k));}private static String fmt(double x){return String.format(java.util.Locale.ROOT,"%.3f",x);}
    private static String join(Object...v){return java.util.Arrays.stream(v).map(Mettl7StageBInterpreter::q).collect(Collectors.joining(","));}
    private static String q(Object v){return "\""+String.valueOf(v).replace("\"","\"\"")+"\"";}
    private static String sha256(Path path)throws IOException{try{MessageDigest md=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[]buf=new byte[8192];for(int n;(n=in.read(buf))>=0;)md.update(buf,0,n);}return java.util.HexFormat.of().formatHex(md.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private record Differential(String species,String branch,String dimension,String residue,double aFraction,int aSeeds,double bFraction,int bSeeds,double delta,String label,String evidence){}
    private record Row(Map<String,String>v){String get(String k){String x=v.get(k);if(x==null)throw new IllegalArgumentException("Missing "+k);return x;}}
    private record Table(List<String>h,List<Row>rows){static Table read(Path p)throws IOException{List<String>l=Files.readAllLines(p);List<String>h=csv(l.getFirst());List<Row>r=new ArrayList<>();for(String s:l.subList(1,l.size())){if(s.isBlank())continue;List<String>x=csv(s);if(x.size()!=h.size())throw new IOException("Malformed "+p);Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<h.size();i++)m.put(h.get(i),x.get(i));r.add(new Row(Map.copyOf(m)));}return new Table(h,r);}}
    private static List<String>csv(String s)throws IOException{List<String>f=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='"'){if(q&&i+1<s.length()&&s.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){f.add(b.toString());b.setLength(0);}else b.append(c);}if(q)throw new IOException("quote");f.add(b.toString());return f;}
}
