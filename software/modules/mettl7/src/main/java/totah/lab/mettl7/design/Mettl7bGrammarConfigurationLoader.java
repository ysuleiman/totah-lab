package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.athena.design.grammar.DesignGrammar;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/** Adapts frozen METTL7 data artifacts to Athena's target-independent grammar model. */
public final class Mettl7bGrammarConfigurationLoader {
    private final ObjectMapper mapper;
    public Mettl7bGrammarConfigurationLoader(){this(new ObjectMapper());}
    public Mettl7bGrammarConfigurationLoader(ObjectMapper mapper){this.mapper=mapper;}

    public DesignGrammar load(Path targetPath,Path scaffoldPath,Path counterPath,Path ruleCsv) throws IOException {
        return load(targetPath,Optional.of(scaffoldPath),counterPath,ruleCsv);
    }

    public DesignGrammar load(Path targetPath,Optional<Path> scaffoldPath,Path counterPath,Path ruleCsv) throws IOException {
        JsonNode target=mapper.readTree(targetPath.toFile()), scaffold=scaffoldPath.isPresent()?mapper.readTree(scaffoldPath.orElseThrow().toFile()):mapper.createObjectNode(), counter=mapper.readTree(counterPath.toFile());
        List<DesignGrammar.FeatureGroup> positive=groups(target.path("promoted_soft_features"),DesignGrammar.FeatureRole.POSITIVE_RECOGNITION,"target");
        List<DesignGrammar.FeatureGroup> avoidance=groups(counter.path("promoted_soft_avoidance_features"),DesignGrammar.FeatureRole.COUNTER_RECOGNITION,"counter");
        Map<String,String> configuredFeatureIds=new LinkedHashMap<>();
        for(DesignGrammar.FeatureGroup group:positive){DesignGrammar.MolecularFeature feature=group.features().getFirst();configuredFeatureIds.put(feature.attributes().get("configured_feature_role")+"|"+feature.atomOrGroupId(),feature.id());}
        List<DesignGrammar.DistanceEnvelope> distances=new ArrayList<>();int n=0;
        for(JsonNode x:target.path("cross_scaffold_inter_feature_geometry").path("feature_pair_envelopes")){
            String first=configuredFeatureIds.get(x.path("feature_roles").get(0).asText()+"|"+x.path("receptor_residue_pair").get(0).asText());
            String second=configuredFeatureIds.get(x.path("feature_roles").get(1).asText()+"|"+x.path("receptor_residue_pair").get(1).asText());
            if(first!=null&&second!=null)distances.add(new DesignGrammar.DistanceEnvelope("distance_"+(++n),first,second,x.path("distance_A_min").asDouble(),x.path("distance_A_max").asDouble(),false));
        }
        List<DesignGrammar.AngleConstraint> angles=new ArrayList<>();
        for(JsonNode x:scaffold.path("b207_hbond_observations")){if(!x.path("angle_deg_min").isNull()&&x.hasNonNull("angle_deg_min"))angles.add(new DesignGrammar.AngleConstraint("configured_angle_"+angles.size(),x.path("ligand_atom").asText(),x.path("angle_deg_min").asDouble(),x.path("angle_deg_max").asDouble(),false));}
        var invariants=scaffoldPath.isPresent()?List.of(new DesignGrammar.ScaffoldInvariant("configured_scaffold_core",scaffold.path("invariant_core").asText(),Set.of(),true)):List.<DesignGrammar.ScaffoldInvariant>of();
        List<DesignGrammar.EditableVector> vectors=scaffoldPath.isPresent()?editableVectors(scaffold):List.of();
        List<DesignGrammar.HardConstraint> hard=new ArrayList<>();List<DesignGrammar.SoftObjective> soft=new ArrayList<>();
        try(BufferedReader reader=Files.newBufferedReader(ruleCsv)){String line;boolean first=true;while((line=reader.readLine())!=null){if(first){first=false;continue;}List<String>v=csv(line);if(v.size()<4)continue;if(v.get(0).equals("HARD"))hard.add(new DesignGrammar.HardConstraint(v.get(1),mapHard(v.get(1)),v.get(2)));else if(v.get(0).equals("SOFT"))soft.add(new DesignGrammar.SoftObjective(v.get(1),v.get(2),Set.of()));}}
        Set<String> templates=new LinkedHashSet<>();target.path("template_ids").forEach(x->templates.add(x.asText()));counter.path("template_ids").forEach(x->templates.add(x.asText()));
        Map<String,String> provenance=new LinkedHashMap<>();provenance.put("target",targetPath.toString());scaffoldPath.ifPresent(path->provenance.put("scaffold",path.toString()));provenance.put("counter",counterPath.toString());provenance.put("rules",ruleCsv.toString());
        JsonNode state=target.path("chemical_state_policy");
        JsonNode p=target.path("physicochemical_policy");
        var policy=new PhysicochemicalGate.Policy(p.path("minimum_molecular_weight").asDouble(),p.path("maximum_molecular_weight").asDouble(),p.path("minimum_formal_charge").asInt(),p.path("maximum_formal_charge").asInt(),p.path("maximum_hydrogen_bond_donors").asInt(),p.path("minimum_hydrogen_bond_acceptors").asInt(),p.path("maximum_hydrogen_bond_acceptors").asInt(),p.path("maximum_rotatable_bonds").asInt(),p.path("minimum_tpsa").asDouble(),p.path("maximum_tpsa").asDouble(),p.path("minimum_log_p").asDouble(),p.path("maximum_log_p").asDouble(),p.path("maximum_aromatic_rings").asInt(),p.path("preferred_maximum_aromatic_rings").asInt(),p.path("minimum_heavy_atoms").asInt(),p.path("maximum_heavy_atoms").asInt(),p.path("preferred_minimum_fraction_sp3").asDouble());
        String grammarId=target.path("grammar_id").asText()+(scaffoldPath.isPresent()?"+"+scaffold.path("grammar_id").asText():"")+"+"+counter.path("grammar_id").asText();
        return new DesignGrammar(grammarId,"1",positive,avoidance,invariants,vectors,distances,angles,hard,soft,new DesignGrammar.ChemicalStatePolicy(state.path("minimum_formal_charge").asInt(),state.path("maximum_formal_charge").asInt(),state.path("assay_plausibility_required").asBoolean(),state.path("states_separate").asBoolean()),policy,new DesignGrammar.ExplorationPolicy(target.path("exploration_fraction").path("minimum").asDouble(),target.path("exploration_fraction").path("maximum").asDouble(),1),templates,provenance);
    }

    private static List<DesignGrammar.FeatureGroup> groups(JsonNode nodes,DesignGrammar.FeatureRole role,String prefix){List<DesignGrammar.FeatureGroup> out=new ArrayList<>();int i=0;for(JsonNode x:nodes){String id=prefix+"_feature_"+(++i);var feature=new DesignGrammar.MolecularFeature(id,mapType(x.path("feature_role").asText()),role,x.has("receptor_residue")?x.path("receptor_residue").asText():x.path("residue").asText(),Map.of("configured_feature_role",x.path("feature_role").asText()));out.add(new DesignGrammar.FeatureGroup(id,DesignGrammar.Requirement.OPTIONAL,1,List.of(feature),List.of()));}return out;}
    private static List<DesignGrammar.EditableVector> editableVectors(JsonNode scaffold){Map<String,List<String>> byAtom=new LinkedHashMap<>();for(JsonNode x:scaffold.path("editable_a_facing_observations"))byAtom.computeIfAbsent(x.path("ligand_atom").asText(),ignored->new ArrayList<>()).add(x.path("receptor_residue").asText());List<DesignGrammar.EditableVector> out=new ArrayList<>();byAtom.forEach((atom,residues)->out.add(new DesignGrammar.EditableVector("editable_"+atom,atom,strings(scaffold.path("allowed_edits")),List.of("configuration-supplied"),residues,Set.of(),List.of("preserve hard chemistry constraints"),strings(scaffold.path("soft_objectives")))));return out;}
    private static DesignGrammar.FeatureType mapType(String role){return switch(role){case "HYDROPHOBIC"->DesignGrammar.FeatureType.HYDROPHOBE;case "HBA"->DesignGrammar.FeatureType.H_BOND_ACCEPTOR;case "HBD"->DesignGrammar.FeatureType.H_BOND_DONOR;case "CHARGED_CENTER"->DesignGrammar.FeatureType.POSITIVE_CENTER;case "AROMATIC_OR_CATION"->DesignGrammar.FeatureType.AROMATIC_RING;default->DesignGrammar.FeatureType.CUSTOM;};}
    private static DesignGrammar.HardConstraintType mapHard(String id){return switch(id){case "VALID_GRAPH"->DesignGrammar.HardConstraintType.VALID_STRUCTURE;case "ASSAY_STATE"->DesignGrammar.HardConstraintType.CHEMICAL_STATE;case "PHYSICOCHEMICAL"->DesignGrammar.HardConstraintType.PHYSICOCHEMICAL;case "REACTIVITY"->DesignGrammar.HardConstraintType.LIABILITY;case "SYNTHESIS"->DesignGrammar.HardConstraintType.SYNTHESIS;default->DesignGrammar.HardConstraintType.CUSTOM;};}
    private static List<String> strings(JsonNode n){List<String>x=new ArrayList<>();n.forEach(v->x.add(v.asText()));return x;}
    private static List<String> csv(String line){List<String>out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='\"')q=!q;else if(c==','&&!q){out.add(b.toString());b.setLength(0);}else b.append(c);}out.add(b.toString());return out;}
}
