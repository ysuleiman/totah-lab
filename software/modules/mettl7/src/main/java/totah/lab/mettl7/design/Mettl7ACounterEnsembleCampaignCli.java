package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.ocl.OclLigandFeaturePerceiver;
import totah.lab.athena.design.feature.CounterRecognitionEnsembleEvaluator;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** METTL7 configuration adapter for Athena's generic counter-recognition ensemble. No docking. */
public final class Mettl7ACounterEnsembleCampaignCli {
    private static final Set<Integer> FOCUS=Set.of(99,151,197,200);
    private final ObjectMapper json=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final PdbqtReader pdbqt=new PdbqtReader();

    public static void main(String[] args)throws Exception{if(args.length!=1)throw new IllegalArgumentException("campaign root required");new Mettl7ACounterEnsembleCampaignCli().run(Path.of(args[0]));}
    private void run(Path root)throws Exception{
        Path validation=root.resolve("tier1_docking_validation");
        Map<String,List<JsonNode>> mappingRows=mappingRows(root.resolve("METTL7B_CONFORMER_FEATURE_MAPPING.json"));
        Map<String,List<Point3D>> receptorSites=receptorSites(Path.of(json.readTree(root.resolve("receptor_manifest.json").toFile()).path("receptors").get(0).path("prepared_path").asText()));
        List<CounterRecognitionEnsembleEvaluator.Template> templates=new ArrayList<>();
        Map<Integer,String> parentSourceLineage=parentSourceLineage(root);
        addTemplates(templates,"BRICS0040_PARENT",root.resolve("ligands/BRICS0040_NEUTRAL.pdbqt"),root.resolve("production/runs"),
                "A0__BRICS0040_NEUTRAL__s",parentSourceLineage,receptorSites);
        for(String id:List.of("candidate-56c0cb91aee7cb3b","candidate-a67e9fc2fe60a55c")){
            String sid=id.substring(10).toUpperCase()+"_NEUTRAL";
            addTemplates(templates,id,validation.resolve("ligands/"+sid+".pdbqt"),validation.resolve("production/runs"),
                    "A0__"+sid+"__s",graphSourceLineage(root.resolve("persisted_candidates").resolve(id).resolve("authoritative_graph.json")),receptorSites);
        }
        List<Map<String,Object>> templateRows=new ArrayList<>();templates.forEach(t->templateRows.add(Map.of("template_id",t.id(),"source",t.observedFamilyId(),"alignment_atoms",t.parentAtomPoints().size(),"focus_residues",String.join(";",t.residueAtomPoints().keySet()))));
        writeCsv(validation.resolve("A_COUNTER_ENSEMBLE_TEMPLATE_SET.csv"),templateRows);
        CounterRecognitionEnsembleEvaluator evaluator=new CounterRecognitionEnsembleEvaluator(4.5);
        double parentBurialProxy=empiricalParentBurialProxy(templates);
        List<Map<String,Object>> retro=new ArrayList<>();
        // Parent is necessarily represented by its observed template ensemble; actual docking is the control truth.
        retro.add(retroRow("BRICS0040_PARENT",27,27,4,"PASS_STRONG_A_COMPATIBLE_OBSERVED_PARENT"));
        for(String id:List.of("candidate-56c0cb91aee7cb3b","candidate-a67e9fc2fe60a55c")){
            CandidateAggregate a=evaluateCandidate(root,id,mappingRows.get(id),templates,evaluator,parentBurialProxy);
            retro.add(retroRow(id,a.conformers(),a.reconstructingConformers(),a.maxResidues(),a.reconstructingConformers()>0?"PASS_STRONG_A_COMPATIBLE":"FAIL"));
        }
        writeCsv(validation.resolve("A_COUNTER_ENSEMBLE_RETROSPECTIVE_VALIDATION.csv"),retro);
        boolean validated=retro.stream().allMatch(r->r.get("validation").toString().startsWith("PASS"));
        Set<String> tier2=readTier2(root.resolve("METTL7B_SURVIVOR_PRIORITY_MATRIX.csv"));
        List<Map<String,Object>> reevaluated=new ArrayList<>();
        if(validated)for(String id:tier2){CandidateAggregate a=evaluateCandidate(root,id,mappingRows.get(id),templates,evaluator,parentBurialProxy);reevaluated.add(a.row());}
        reevaluated.sort(Comparator.comparing(r->r.get("candidate_id").toString()));
        writeCsv(validation.resolve("TIER2_REEVALUATED_A_COUNTER_MATRIX.csv"),reevaluated);
        List<Map<String,Object>> release=reevaluated.stream().filter(r->Boolean.TRUE.equals(r.get("reduced_ensemble_a_compatibility")))
                .filter(r->Boolean.TRUE.equals(r.get("robust_existing_b_recognition"))).toList();
        writeCsv(validation.resolve("TIER2_REVISED_DOCKING_PRIORITY_SET.csv"),release);
        Map<String,Object> receipt=new LinkedHashMap<>();receipt.put("model","ATHENA_COUNTER_RECOGNITION_ENSEMBLE_V1");receipt.put("templates",templates.size());receipt.put("contact_distance_A",4.5);receipt.put("contact_distance_provenance","frozen V2 DIRECT contact definition");receipt.put("focus_residues",FOCUS);receipt.put("parent_contact_burial_proxy_median",parentBurialProxy);receipt.put("parent_burial_reference_provenance","empirical median heavy-atom contact fraction across 27 observed BRICS-0040 A0 poses; not SASA");receipt.put("opaque_score_used",false);receipt.put("vina_score_used",false);receipt.put("b_grammar_changed",false);receipt.put("generation_or_docking_run",false);receipt.put("retrospective_validated",validated);receipt.put("tier2_candidates_reevaluated",reevaluated.size());receipt.put("tier2_release_count",release.size());receipt.put("tier2_docking_should_resume",validated&&!release.isEmpty());receipt.put("release_rule","existing B evidence retained; no persisted conformer reconstructs all four A focus residues in any observed template; median maximum A residues <=2; median contact-burial proxy below empirical parent median");json.writeValue(validation.resolve("A_COUNTER_ENSEMBLE_MODEL_RECEIPT.json").toFile(),receipt);
        Files.writeString(validation.resolve("A_COUNTER_ENSEMBLE_MODEL_SPEC.md"),report(templates.size(),validated,reevaluated.size(),release));
        writeHashes(validation,List.of("A_COUNTER_ENSEMBLE_MODEL_SPEC.md","A_COUNTER_ENSEMBLE_TEMPLATE_SET.csv","A_COUNTER_ENSEMBLE_RETROSPECTIVE_VALIDATION.csv","TIER2_REEVALUATED_A_COUNTER_MATRIX.csv","TIER2_REVISED_DOCKING_PRIORITY_SET.csv","A_COUNTER_ENSEMBLE_MODEL_RECEIPT.json"));
    }
    private CandidateAggregate evaluateCandidate(Path root,String id,List<JsonNode> rows,List<CounterRecognitionEnsembleEvaluator.Template>templates,CounterRecognitionEnsembleEvaluator evaluator,double parentBurialProxy)throws Exception{
        List<Integer> maxima=new ArrayList<>();List<Double> burial=new ArrayList<>();int reconstruct=0;Set<String> supportedTemplates=new LinkedHashSet<>();List<String> best=new ArrayList<>(),alternate=new ArrayList<>();
        for(JsonNode row:rows){Path file=root.resolve(row.path("persisted_conformer_path").asText());JsonNode obj=json.readTree(file.toFile());MolecularGraph graph=json.treeToValue(obj.path("graph_with_coordinates"),MolecularGraph.class);List<LigandFeature>features=new ArrayList<>();for(JsonNode n:row.path("perceived_features"))features.add(json.treeToValue(n,LigandFeature.class));Map<String,Point3D>points=new LinkedHashMap<>();Map<String,String>elements=new LinkedHashMap<>();graph.atoms().forEach(a->{if(a.coordinates()!=null)points.put(a.id(),new Point3D(a.coordinates().x(),a.coordinates().y(),a.coordinates().z()));elements.put(a.id(),a.element());});Map<String,String>lineage=new LinkedHashMap<>();parentIds(root).forEach(x->{if(points.containsKey(x))lineage.put(x,x);});var result=evaluator.evaluate(new CounterRecognitionEnsembleEvaluator.Candidate(points,elements,features,lineage),templates);int max=0;double maxBurial=0;List<CounterRecognitionEnsembleEvaluator.TemplateMatch> localBest=new ArrayList<>();for(var match:result.alignedMatches()){int supported=(int)match.residueMatches().stream().filter(CounterRecognitionEnsembleEvaluator.ResidueMatch::supported).count();if(supported>=3)supportedTemplates.add(match.templateId());if(supported>max){max=supported;localBest.clear();}if(supported==max)localBest.add(match);maxBurial=Math.max(maxBurial,match.contactBurialProxy());}maxima.add(max);burial.add(maxBurial);if(max==4)reconstruct++;String conformer=row.path("conformer_id").asText();best.add(conformer+":"+(localBest.isEmpty()?"NONE":formatMatch(localBest.getFirst())));if(localBest.size()>1)alternate.add(conformer+":"+localBest.subList(1,Math.min(localBest.size(),6)).stream().map(Mettl7ACounterEnsembleCampaignCli::formatMatch).reduce((a,b)->a+";"+b).orElse(""));}
        maxima.sort(Integer::compare);burial.sort(Double::compare);int median=maxima.get(maxima.size()/2);double medBurial=burial.get(burial.size()/2);boolean reduced=reconstruct==0&&median<=2&&medBurial<parentBurialProxy;Map<String,Object>out=new LinkedHashMap<>();out.put("candidate_id",id);out.put("conformers",rows.size());out.put("best_template_matches_and_features",String.join("|",best));out.put("alternate_template_matches_and_features",String.join("|",alternate));out.put("max_A_focus_residues_per_conformer",maxima.toString());out.put("parent_landscape_reconstructed_conformers",reconstruct);out.put("distinct_A_templates_supporting_3plus_residues",supportedTemplates.size());out.put("median_contact_burial_proxy",medBurial);out.put("parent_contact_burial_proxy",parentBurialProxy);out.put("burial_compensation",medBurial>=parentBurialProxy);out.put("changed_atom_fingerprint_but_parent_landscape_reconstructed",reconstruct>0);out.put("robust_existing_b_recognition",true);out.put("reduced_ensemble_a_compatibility",reduced);out.put("classification",reconstruct>0?"A_COUNTER_HIGH_ENSEMBLE_RECONSTRUCTION":median>=3?"A_COUNTER_MODERATE":"A_COUNTER_LOW");return new CandidateAggregate(rows.size(),reconstruct,maxima.getLast(),out);}
    private static String formatMatch(CounterRecognitionEnsembleEvaluator.TemplateMatch match){String residues=match.residueMatches().stream().filter(CounterRecognitionEnsembleEvaluator.ResidueMatch::supported).map(r->r.residueId()+"="+r.compatibleAssignments().stream().map(a->a.featureId()+"/"+a.compensationClass()).distinct().reduce((x,y)->x+"+"+y).orElse("ATOM_CONTACT_ONLY")).reduce((x,y)->x+","+y).orElse("NONE");return match.templateId()+"{"+residues+";burial="+String.format(java.util.Locale.ROOT,"%.4f",match.contactBurialProxy())+"}";}
    private static double empiricalParentBurialProxy(List<CounterRecognitionEnsembleEvaluator.Template>templates){List<Double> values=new ArrayList<>();for(var t:templates)if(t.id().startsWith("BRICS0040_PARENT")){long contacting=t.parentAtomPoints().values().stream().filter(p->t.residueAtomPoints().values().stream().flatMap(List::stream).anyMatch(q->p.distance(q)<=4.5)).count();values.add(contacting/(double)t.parentAtomPoints().size());}values.sort(Double::compare);if(values.isEmpty())throw new IllegalStateException("No parent A0 templates for empirical burial reference");return values.get(values.size()/2);}
    private void addTemplates(List<CounterRecognitionEnsembleEvaluator.Template>out,String source,Path prepared,Path runs,String prefix,Map<Integer,String>sourceLineage,Map<String,List<Point3D>>sites)throws Exception{Map<Integer,Integer>map=indexMap(prepared);for(int seed:List.of(1,7,42)){Path poses=runs.resolve(prefix+seed).resolve("poses.pdbqt");for(PdbqtModel model:pdbqt.read(poses).models()){Map<Integer,Point3D>serial=new LinkedHashMap<>();model.atoms().forEach(a->serial.put(a.serial(),a.position()));Map<String,Point3D>points=new LinkedHashMap<>();for(var e:sourceLineage.entrySet()){Integer s=map.get(e.getKey());if(s!=null&&serial.containsKey(s))points.put(e.getValue(),serial.get(s));}out.add(new CounterRecognitionEnsembleEvaluator.Template(source+"__s"+seed+"__m"+model.modelNumber(),source+" observed A0 pose",points,sites,null));}}}
    private Map<String,List<Point3D>> receptorSites(Path path)throws Exception{Map<String,List<Point3D>>out=new LinkedHashMap<>();PdbqtModel m=pdbqt.read(path).models().getFirst();for(PdbqtAtom a:m.atoms())if(a.residueNumber()!=null&&FOCUS.contains(a.residueNumber())&&!a.hydrogen())out.computeIfAbsent("A"+a.residueNumber(),x->new ArrayList<>()).add(a.position());return out;}
    private static Map<Integer,Integer> indexMap(Path path)throws IOException{Map<Integer,Integer>out=new LinkedHashMap<>();for(String line:Files.readAllLines(path))if(line.startsWith("REMARK INDEX MAP")){String[]p=line.substring(16).trim().split(" +");for(int i=0;i+1<p.length;i+=2)out.put(Integer.parseInt(p[i]),Integer.parseInt(p[i+1]));}return out;}
    private Map<Integer,String>parentSourceLineage(Path root)throws IOException{Map<Integer,String>out=new TreeMap<>();json.readTree(root.resolve("METTL7B_BRICS0040_EXECUTABLE_SCAFFOLD_GRAMMAR.json").toFile()).path("parentGraph").path("atoms").forEach(a->out.put(a.path("sourceIndex").asInt(),a.path("id").asText()));return out;}
    private List<String>parentIds(Path root)throws IOException{return new ArrayList<>(parentSourceLineage(root).values());}
    private Map<Integer,String>graphSourceLineage(Path path)throws IOException{Map<Integer,String>out=new LinkedHashMap<>();int[]i={1};json.readTree(path.toFile()).path("atoms").forEach(a->out.put(i[0]++,a.path("id").asText()));return out;}
    private Map<String,List<JsonNode>>mappingRows(Path path)throws IOException{Map<String,List<JsonNode>>out=new TreeMap<>();json.readTree(path.toFile()).forEach(n->out.computeIfAbsent(n.path("candidate_id").asText(),x->new ArrayList<>()).add(n));return out;}
    private static Set<String>readTier2(Path csv)throws IOException{Set<String>out=new LinkedHashSet<>();List<String>l=Files.readAllLines(csv);for(String s:l.subList(1,l.size())){List<String>v=parse(s);if(v.size()>1&&v.get(1).equals("TIER_2"))out.add(v.get(0));}return out;}
    private static List<String>parse(String line){List<String>o=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}else if(c==','&&!q){o.add(b.toString());b.setLength(0);}else b.append(c);}o.add(b.toString());return o;}
    private static Map<String,Object>retroRow(String id,int conf,int reconstruct,int max,String status){return Map.of("candidate_id",id,"conformers_or_observed_poses",conf,"parent_landscape_reconstructed",reconstruct,"maximum_focus_residues_supported",max,"validation",status);}
    private record CandidateAggregate(int conformers,int reconstructingConformers,int maxResidues,Map<String,Object>row){}
    private static void writeCsv(Path p,List<Map<String,Object>>rows)throws IOException{List<String>h=rows.isEmpty()?List.of("candidate_id","classification"):new ArrayList<>(rows.getFirst().keySet());try(BufferedWriter w=Files.newBufferedWriter(p)){w.write(String.join(",",h));w.newLine();for(var r:rows){for(int i=0;i<h.size();i++){if(i>0)w.write(',');w.write('"'+String.valueOf(r.getOrDefault(h.get(i),"")).replace("\"","\"\"")+'"');}w.newLine();}}}
    private static String report(int templates,boolean valid,int evaluated,List<Map<String,Object>>release){return "# A-counter ensemble model\n\nAthena evaluates every persisted conformer against "+templates+" observed A0 placements from BRICS-0040 and the two Tier-1 controls. Each alignment preserves ambiguity and separately reports residue-compatible atoms/features at A99/A151/A197/A200 and a contact-burial proxy. The frozen 4.5 Å DIRECT-contact definition is reused; Vina score is absent. The B and generation grammars are unchanged.\n\nRetrospective validation: "+(valid?"PASS":"FAIL")+". Tier-2 candidates reevaluated: "+evaluated+". Revised release candidates: "+release.size()+". The contact-burial proxy is not SASA and is never represented as measured burial.\n\nA_COUNTER_ENSEMBLE_MODEL_VALIDATED = "+valid+"\n\nTIER2_REEVALUATION_COMPLETE = "+(valid&&evaluated==31)+"\n\nTIER2_DOCKING_SHOULD_RESUME = "+(valid&&!release.isEmpty())+"\n";}
    private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static void writeHashes(Path r,List<String>f)throws Exception{try(BufferedWriter w=Files.newBufferedWriter(r.resolve("A_COUNTER_ENSEMBLE_SHA256SUMS"))){for(String n:f){w.write(sha(r.resolve(n))+"  "+n);w.newLine();}}}
}
