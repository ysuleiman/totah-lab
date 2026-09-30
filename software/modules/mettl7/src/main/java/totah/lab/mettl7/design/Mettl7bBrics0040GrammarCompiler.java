package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammarValidator;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.hephaestus.ligand.topology.KekuleAromaticity;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.BufferedWriter;
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

/** Materializes the frozen BRICS-0040 scientific artifacts; scientific choices stay in those artifacts. */
public final class Mettl7bBrics0040GrammarCompiler {
 private static final ObjectMapper JSON=new ObjectMapper();
 private Mettl7bBrics0040GrammarCompiler(){}
 public static void main(String[] args)throws IOException{if(args.length!=11)throw new IllegalArgumentException("sdf scaffold.json vectors.csv target.json counter.json rules.csv executable.json core.json executableVectors.csv neighborhoods.csv permissions.csv");compile(java.util.Arrays.stream(args).map(Path::of).toList());}
 static void compile(List<Path> p)throws IOException{
  var sdf=new SdfLigandReader().readModel(p.get(0));var atoms=sdf.ligand().structure().getChains().getFirst().residues().getFirst().getAtoms();boolean[] aromatic=KekuleAromaticity.perceive(atoms.size(),sdf.bonds(),atoms);
  JsonNode scaffold=JSON.readTree(p.get(1).toFile());List<Map<String,String>> sourceVectors=readCsv(p.get(2));Set<String> editable=new LinkedHashSet<>();sourceVectors.forEach(r->editable.add(r.get("ligand_atom")));
  Map<String,Integer> index=new LinkedHashMap<>();for(int i=0;i<atoms.size();i++)index.put(atoms.get(i).getName(),i);
  List<ExecutableScaffoldGrammar.IndexedAtom> graphAtoms=new ArrayList<>();for(int i=0;i<atoms.size();i++)graphAtoms.add(new ExecutableScaffoldGrammar.IndexedAtom(atoms.get(i).getName(),i+1,atoms.get(i).getElement().name(),aromatic[i],sdf.formalCharges().get(i),"UNSPECIFIED"));
  List<ExecutableScaffoldGrammar.IndexedBond> graphBonds=sdf.bonds().stream().map(b->bond(b,atoms,aromatic)).sorted(Comparator.comparing(ExecutableScaffoldGrammar.IndexedBond::firstAtomId).thenComparing(ExecutableScaffoldGrammar.IndexedBond::secondAtomId)).toList();
  Map<String,Set<String>> adjacency=adjacency(graphBonds);Set<String> editRegionAll=new LinkedHashSet<>(editable);for(String a:editable)for(String n:adjacency.getOrDefault(a,Set.of()))if(element(n,index,atoms).equals("H"))editRegionAll.add(n);
  Set<String> protectedAtoms=new LinkedHashSet<>();for(var a:graphAtoms)if(!editRegionAll.contains(a.id()))protectedAtoms.add(a.id());
  List<ExecutableScaffoldGrammar.IndexedBond> protectedBonds=graphBonds.stream().filter(b->protectedAtoms.contains(b.firstAtomId())&&protectedAtoms.contains(b.secondAtomId())).toList();
  List<String> soft=strings(scaffold.path("soft_objectives"));List<ExecutableScaffoldGrammar.ExecutableEditVector> vectors=new ArrayList<>();
  for(Map<String,String> row:sourceVectors){String anchor=row.get("ligand_atom");int i=index.get(anchor);Set<String> region=new LinkedHashSet<>();region.add(anchor);for(String n:adjacency.get(anchor))if(element(n,index,atoms).equals("H"))region.add(n);List<ExecutableScaffoldGrammar.IndexedBond> attachments=graphBonds.stream().filter(b->(region.contains(b.firstAtomId())&&!region.contains(b.secondAtomId()))||(region.contains(b.secondAtomId())&&!region.contains(b.firstAtomId()))).toList();Set<String> neighborhood=new LinkedHashSet<>();for(String r:region)for(String n:adjacency.get(r))if(!region.contains(n))neighborhood.add(n);Set<String> second=new LinkedHashSet<>();for(String n:neighborhood)for(String q:adjacency.get(n))if(!region.contains(q))second.add(q);neighborhood.addAll(second);boolean ring=aromatic[i];boolean terminal=adjacency.get(anchor).stream().filter(n->!element(n,index,atoms).equals("H")).count()==1;List<String> allowed=new ArrayList<>();if(ring){allowed.add("HETEROATOM_SUBSTITUTION");allowed.add("RING_SUBSTITUTION");}if(terminal){allowed.add("SUBSTITUENT_REPLACEMENT");allowed.add("SUBSTITUENT_GROWTH");allowed.add("SUBSTITUENT_PRUNING");allowed.add("HETEROATOM_SUBSTITUTION");}List<String> replacements=ring?List.of("AROMATIC_HYDROPHOBE","AROMATIC_POLAR_HETEROATOM","RING_POSITION_SUBSTITUENT"):List.of("ALIPHATIC_HYDROPHOBE","POLAR_NEUTRAL_ISOSTERE");vectors.add(new ExecutableScaffoldGrammar.ExecutableEditVector("editable_"+anchor,anchor,attachments,region,neighborhood,region,allowed,replacements,soft));}
  List<ExecutableScaffoldGrammar.FeatureProtection> features=features(scaffold,editable);
  var grammar=new ExecutableScaffoldGrammar(scaffold.path("grammar_id").asText()+"_EXECUTABLE","1",sdf.title(),sha(p.get(0)),new ExecutableScaffoldGrammar.IndexedGraph(graphAtoms,graphBonds),protectedAtoms,protectedBonds,vectors,features,Map.of("parent_sdf",p.get(0).toString(),"scientific_scaffold_grammar",p.get(1).toString(),"editable_vectors",p.get(2).toString(),"target_grammar",p.get(3).toString(),"counter_grammar",p.get(4).toString(),"rules",p.get(5).toString()));
  var validation=new ExecutableScaffoldGrammarValidator().validate(grammar);if(!validation.valid())throw new IOException("compiled grammar invalid: "+validation.errors());JSON.writerWithDefaultPrettyPrinter().writeValue(p.get(6).toFile(),grammar);
  JSON.writerWithDefaultPrettyPrinter().writeValue(p.get(7).toFile(),Map.of("parent_id",sdf.title(),"parent_sha256",sha(p.get(0)),"explicit_indexed_subgraph",grammar.parentGraph(),"protected_atom_ids",protectedAtoms,"protected_bonds",protectedBonds,"stereochemical_constraints",List.of(),"validation",validation));
  writeVectors(p.get(8),vectors);writeNeighborhoods(p.get(9),vectors);writePermissions(p.get(10),vectors);
 }
 private static ExecutableScaffoldGrammar.IndexedBond bond(ChemicalBond b,List<totah.lab.gaia.structure.Atom>a,boolean[]ar){String x=a.get(b.atomIndexA()).getName(),y=a.get(b.atomIndexB()).getName();return new ExecutableScaffoldGrammar.IndexedBond(x,y,b.order().name(),b.aromatic(),"UNSPECIFIED");}
 private static Map<String,Set<String>> adjacency(List<ExecutableScaffoldGrammar.IndexedBond>bonds){Map<String,Set<String>>m=new LinkedHashMap<>();for(var b:bonds){m.computeIfAbsent(b.firstAtomId(),x->new LinkedHashSet<>()).add(b.secondAtomId());m.computeIfAbsent(b.secondAtomId(),x->new LinkedHashSet<>()).add(b.firstAtomId());}return m;}
 private static String element(String id,Map<String,Integer>idx,List<totah.lab.gaia.structure.Atom>a){return a.get(idx.get(id)).getElement().name();}
 private static List<ExecutableScaffoldGrammar.FeatureProtection> features(JsonNode s,Set<String>editable){Map<String,ExecutableScaffoldGrammar.FeatureProtection>m=new LinkedHashMap<>();for(String field:List.of("b207_hbond_observations","b_hydrophobic_observations"))for(JsonNode x:s.path(field)){String atom=x.path("ligand_atom").asText(),id=x.path("feature_role").asText()+"_"+atom+"_"+x.path("receptor_residue").asText();String disposition=editable.contains(atom)?"SOFT_REPLACEABLE_AT_DECLARED_EDIT_VECTOR":"PROTECTED";String alt=editable.contains(atom)?"replacement must preserve a configured B-recognition soft objective":"";m.putIfAbsent(id,new ExecutableScaffoldGrammar.FeatureProtection(id,atom,disposition,alt));}return List.copyOf(m.values());}
 private static List<Map<String,String>> readCsv(Path p)throws IOException{List<String>lines=Files.readAllLines(p);String[]h=lines.getFirst().split(",");List<Map<String,String>>out=new ArrayList<>();for(String line:lines.subList(1,lines.size())){String[]v=line.split(",",-1);Map<String,String>r=new LinkedHashMap<>();for(int i=0;i<h.length;i++)r.put(h[i],v[i]);out.add(r);}return out;}
 private static List<String> strings(JsonNode n){List<String>o=new ArrayList<>();n.forEach(x->o.add(x.asText()));return o;}
 private static String sha(Path p)throws IOException{try{byte[]d=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));return java.util.HexFormat.of().formatHex(d);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static void writeVectors(Path p,List<ExecutableScaffoldGrammar.ExecutableEditVector>v)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write("vector_id,anchor_atom_id,attachment_bonds,current_subgraph,permitted_edit_region\n");for(var x:v)w.write(csv(x.id())+","+csv(x.anchorAtomId())+","+csv(bonds(x.attachmentBonds()))+","+csv(String.join(";",x.currentSubgraphAtomIds()))+","+csv(String.join(";",x.permittedEditRegion()))+"\n");}}
 private static void writeNeighborhoods(Path p,List<ExecutableScaffoldGrammar.ExecutableEditVector>v)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write("vector_id,protected_neighbor_atom_ids,derivation\n");for(var x:v)w.write(csv(x.id())+","+csv(String.join(";",x.protectedNeighborhood()))+",GRAPH_ONE_AND_TWO_HOP_OUTSIDE_EDIT_REGION\n");}}
 private static void writePermissions(Path p,List<ExecutableScaffoldGrammar.ExecutableEditVector>v)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write("vector_id,allowed_transformation_class,permitted_replacement_features,permission_basis\n");for(var x:v)for(String t:x.allowedTransformationClasses())w.write(csv(x.id())+","+csv(t)+","+csv(String.join(";",x.permittedReplacementFeatures()))+",FROZEN_GLOBAL_CLASS_FILTERED_BY_PARENT_GRAPH_TOPOLOGY\n");}}
 private static String bonds(List<ExecutableScaffoldGrammar.IndexedBond>b){return b.stream().map(x->x.firstAtomId()+"-"+x.secondAtomId()+":"+x.order()).reduce((a,c)->a+";"+c).orElse("");}
 private static String csv(String s){return '"'+s.replace("\"","\"\"")+'"';}
}
