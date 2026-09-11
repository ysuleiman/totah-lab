package totah.lab.mettl7.recognition;

import totah.lab.athena.recognition.*;
import totah.lab.athena.surface.differential.DifferentialResidueScore;
import totah.lab.mettl7.surface.Mettl7FrozenDifferentialSurfaceLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Executes only the frozen empirical METTL7 recognition-basin policy. */
public final class Mettl7RecognitionBasinExecutor {
    public static final String VERSION="METTL7_RECOGNITION_BASIN_EXECUTION_V1";
    private static final double[] GEOMETRY={1.5,2.0,2.5};
    private static final double[] TOPOLOGY={.25,.40,.55};
    private Mettl7RecognitionBasinExecutor(){}

    public static void main(String[] args)throws IOException{
        if(args.length!=2)throw new IllegalArgumentException("usage: <repository-root> <output-directory>");
        execute(Path.of(args[0]),Path.of(args[1]));
    }

    public static Result execute(Path root,Path output)throws IOException{
        var materialized=Mettl7RecognitionBatchMaterializer.runWithEvidence(root,output.resolve("materialization"));
        return execute(root,output,materialized);
    }

    public static Result execute(Path root,Path output,
            Mettl7RecognitionBatchMaterializer.EvidenceSummary materialized)throws IOException{
        Files.createDirectories(output);
        Map<String,List<Mettl7RecognitionBatchMaterializer.MaterializedEvidence>> arms=new TreeMap<>();
        materialized.evidence().forEach(e->arms.computeIfAbsent(e.arm(),ignored->new ArrayList<>()).add(e));
        Path basins=output.resolve("BASINS.csv"),pairs=output.resolve("PAIR_ADMISSIONS.csv");
        Map<String,RecognitionBasinExecutionResult> primary=new TreeMap<>();
        Map<String,List<String>> sensitivity=new TreeMap<>();
        try(BufferedWriter bw=Files.newBufferedWriter(basins,StandardCharsets.UTF_8);
            BufferedWriter pw=Files.newBufferedWriter(pairs,StandardCharsets.UTF_8)){
            bw.write(csv("arm","geometry_threshold_A","topology_threshold","basin_id","recurrent","members","seeds","runs","geometry_medoid","topology_medoid","geometry_mean","geometry_max","topology_mean","topology_max","persistent_edges","variable_edges","quality","flags","surfdiff_environments"));
            pw.write(csv("arm","geometry_threshold_A","topology_threshold","pose_1","pose_2","geometry_pass","topology_pass","reroute","matched_geometry_pass","ambiguity","quality","admitted","reasons","provenance"));
            for(var entry:arms.entrySet()){
                String arm=entry.getKey();
                var evidence=entry.getValue();Map<String,FixedFrameHeavyAtomGeometryDistance.PoseGeometry> geometryMap=new LinkedHashMap<>();
                evidence.forEach(e->geometryMap.put(e.observation().poseId(),e.geometry()));
                var geometry=new FixedFrameHeavyAtomGeometryDistance(geometryMap);
                List<RecognitionStateObservation> observations=evidence.stream().map(Mettl7RecognitionBatchMaterializer.MaterializedEvidence::observation).toList();
                for(double g:GEOMETRY)for(double t:TOPOLOGY){
                    RecognitionBasinPolicy policy=policy(g,t);RecognitionBasinExecutionResult result=new RecognitionBasinBuilder().buildWithAdmissions(
                            observations,geometry,Mettl7RecognitionBasinExecutor::topology,policy,
                            Mettl7RecognitionBasinExecutor::admission,new RecognitionRecurrencePolicy(2,2,2,"FROZEN_RECOGNITION_RECURRENCE_V1"));
                    if(g==2.0&&t==.40)primary.put(arm,result);
                    long recurrent=result.basins().stream().filter(RecognitionBasinExecutionResult.BasinResult::recurrent).count();
                    sensitivity.computeIfAbsent(arm,ignored->new ArrayList<>()).add(g+"/"+t+":basins="+result.basins().size()+",recurrent="+recurrent);
                    for(var b:result.basins()){var x=b.basin();bw.write(csv(arm,g,t,x.id(),b.recurrent(),x.members().size(),sorted(x.seeds()),sorted(x.runs()),x.geometricMedoidPoseId(),x.topologyMedoidPoseId(),decimal(x.geometryDispersion().mean()),decimal(x.geometryDispersion().maximum()),decimal(x.topologyDispersion().mean()),decimal(x.topologyDispersion().maximum()),sortedKeys(x.persistentEdges()),sortedKeys(x.variableEdges()),x.evidenceQuality(),sorted(x.ambiguityFlags()),sortedResidues(x.differentialEnvironmentsEngaged())));}
                    for(var p:result.pairAdmissions()){var a=p.admission();pw.write(csv(arm,g,t,p.firstPoseId(),p.secondPoseId(),a.geometryThresholdPassed(),a.topologyThresholdPassed(),a.stableFeatureReroutePresent(),a.matchedEdgeGeometryPassed(),a.topologyRelevantAmbiguityPresent(),a.evidenceQuality(),a.sameBasinAdmissible(),a.reasons(),a.provenance()));}
                }
            }
        }
        Path edgeEvidence=output.resolve("RECURRENT_EDGE_EVIDENCE.csv");
        writeRecurrentEdgeEvidence(edgeEvidence,primary);
        Path receipt=output.resolve("BASIN_EXECUTION_RECEIPT.txt");List<String> lines=new ArrayList<>();
        lines.add("version="+VERSION);lines.add("primary_geometry_A=2.0");lines.add("primary_topology_distance=0.40");
        lines.add("semantic_gates=no_reroute,matched_edge_geometry,no_topology_relevant_ambiguity");lines.add("pair_direction=MAXIMUM");
        lines.add("recurrence=minimum_members_2,minimum_seeds_2,minimum_runs_2");lines.add("sensitivity_geometry="+Arrays.toString(GEOMETRY));lines.add("sensitivity_topology="+Arrays.toString(TOPOLOGY));
        for(var e:primary.entrySet())lines.add(e.getKey()+" primary_basins="+e.getValue().basins().size()+" recurrent="+e.getValue().basins().stream().filter(RecognitionBasinExecutionResult.BasinResult::recurrent).count()+" sizes="+e.getValue().basins().stream().map(b->b.basin().members().size()).toList());
        sensitivity.forEach((arm,value)->lines.add(arm+" sensitivity="+value));
        lines.add("materialization_arm_outcome_counts=" + materialized.summary().outcomes().stream().collect(
                java.util.stream.Collectors.groupingBy(Mettl7RecognitionBatchMaterializer.Outcome::arm,
                        TreeMap::new, java.util.stream.Collectors.groupingBy(Mettl7RecognitionBatchMaterializer.Outcome::status,
                                TreeMap::new, java.util.stream.Collectors.counting()))));
        lines.add("evaluated_arm_quality_counts=" + materialized.evidence().stream().collect(
                java.util.stream.Collectors.groupingBy(Mettl7RecognitionBatchMaterializer.MaterializedEvidence::arm,
                        TreeMap::new, java.util.stream.Collectors.groupingBy(e -> e.observation().quality(),
                                TreeMap::new, java.util.stream.Collectors.counting()))));
        lines.add("RECURRENT_BASIN_PRESENCE=contact occurs in at least one member of a recurrent basin");
        lines.add("PERSISTENT_WITHIN_BASIN=the same frozen edge key is present in every member of a recurrent basin");
        lines.add("SURFDIFF=ATTACHED_FROZEN_DIRECTIONAL_MAPS");
        lines.add("SURFDIFF_A_VS_B_SHA256="+Mettl7FrozenDifferentialSurfaceLoader.A_VS_B_SHA256);
        lines.add("SURFDIFF_B_VS_A_SHA256="+Mettl7FrozenDifferentialSurfaceLoader.B_VS_A_SHA256);
        appendRoleEvidence(lines,primary);
        lines.add("SELECTIVITY_INTERPRETATION=REQUIRES_EVIDENCE_SYNTHESIS_FROM_REGENERATED_BASINS");
        lines.add("basins_sha256="+sha256(basins));lines.add("pair_admissions_sha256="+sha256(pairs));
        lines.add("recurrent_edge_evidence_sha256="+sha256(edgeEvidence));
        Files.write(receipt,lines,StandardCharsets.UTF_8);
        return new Result(primary,basins,pairs,receipt,materialized.summary().outcomes().size(),materialized.evidence().size());
    }

    private static void writeRecurrentEdgeEvidence(Path path,
            Map<String,RecognitionBasinExecutionResult> primary)throws IOException{
        try(BufferedWriter writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){
            writer.write(csv("arm","residue","interaction_type","ligand_features",
                    "recurrent_basins_with_edge","persistent_recurrent_basins","total_recurrent_basins",
                    "member_poses_with_edge","independent_seeds","interaction_distance_min_A",
                    "interaction_distance_mean_A","interaction_distance_max_A","RUP","RUS","RSS",
                    "local_environment_status","formal_roles","evidence_quality"));
            for(var armEntry:primary.entrySet()){
                String arm=armEntry.getKey();
                var recurrentResults=armEntry.getValue().basins().stream()
                        .filter(RecognitionBasinExecutionResult.BasinResult::recurrent).toList();
                Set<EdgeKind> kinds=new TreeSet<>(Comparator.comparingInt((EdgeKind k)->k.residue)
                        .thenComparing(k->k.type.name()));
                recurrentResults.stream().flatMap(r->r.basin().members().stream())
                        .flatMap(o->o.graph().edges().stream())
                        .forEach(e->kinds.add(new EdgeKind(e.environmentResidue().residueNumber(),e.interaction().type())));
                for(EdgeKind kind:kinds){
                    Set<String> basinIds=new TreeSet<>(),persistentBasinIds=new TreeSet<>(),poses=new TreeSet<>(),seeds=new TreeSet<>(),features=new TreeSet<>();
                    Set<EvidenceQuality> qualities=new LinkedHashSet<>();Set<InteractionRole> roles=new LinkedHashSet<>();
                    DoubleSummaryStatistics distances=new DoubleSummaryStatistics();Optional<DifferentialResidueScore> surface=Optional.empty();
                    for(var result:recurrentResults){
                        boolean basinHas=false;
                        for(var observation:result.basin().members())for(var edge:observation.graph().edges()){
                            if(edge.environmentResidue().residueNumber()!=kind.residue||edge.interaction().type()!=kind.type)continue;
                            basinHas=true;poses.add(observation.poseId());seeds.add(observation.seedId());features.add(edge.ligandFeature().id());
                            qualities.add(edge.quality());distances.accept(edge.interaction().distanceAngstroms());
                            if(surface.isEmpty())surface=edge.differentialSurface();
                        }
                        if(basinHas)basinIds.add(result.basin().id());
                        if(result.basin().persistentEdges().stream().anyMatch(k->k.residue().residueNumber()==kind.residue&&k.type()==kind.type))
                            persistentBasinIds.add(result.basin().id());
                        var classified=new InteractionRoleClassifier().classify(result.basin(),result.recurrent(),
                                new InteractionRoleClassifier.MaterialContributionEvidence(Set.of(),false,
                                        "FROZEN_ALTERNATIVE_EVIDENCE_INADEQUATE"));
                        classified.roles().forEach((key,role)->{if(key.residue().residueNumber()==kind.residue&&key.type()==kind.type)roles.add(role);});
                    }
                    String localStatus=surface.map(s->s.rup()>0||s.rus()>0?"DIFFERENTIAL":"PRESERVED").orElse("SURFACE_UNAVAILABLE");
                    writer.write(csv(arm,kind.residue,kind.type,features,basinIds.size(),persistentBasinIds.size(),
                            recurrentResults.size(),poses.size(),seeds.size(),decimal(distances.getMin()),
                            decimal(distances.getAverage()),decimal(distances.getMax()),surface.map(DifferentialResidueScore::rup).orElse(null),
                            surface.map(DifferentialResidueScore::rus).orElse(null),surface.map(DifferentialResidueScore::rss).orElse(null),
                            localStatus,roles.isEmpty()?Set.of(InteractionRole.UNRESOLVED):roles,qualities));
                }
            }
        }
    }

    private record EdgeKind(int residue,totah.lab.athena.interaction.InteractionType type){}

    private static void appendRoleEvidence(List<String> lines,Map<String,RecognitionBasinExecutionResult> primary){
        for(var entry:primary.entrySet()){
            String arm=entry.getKey();List<RecognitionBasinExecutionResult.BasinResult> recurrentResults=entry.getValue().basins().stream()
                    .filter(RecognitionBasinExecutionResult.BasinResult::recurrent).toList();
            List<RecognitionBasin> recurrent=recurrentResults.stream().map(RecognitionBasinExecutionResult.BasinResult::basin).toList();
            InteractionRoleClassifier classifier=new InteractionRoleClassifier();
            int[] residues={206,196,151,39,43,199,195,145,231,234,202};
            for(int residue:residues){long persistent=recurrent.stream().filter(b->b.persistentEdges().stream()
                    .anyMatch(k->k.residue().residueNumber()==residue)).count();
                Set<InteractionRole> formalRoles=new LinkedHashSet<>();
                for(var result:recurrentResults){var classified=classifier.classify(result.basin(),result.recurrent(),
                        new InteractionRoleClassifier.MaterialContributionEvidence(Set.of(),false,"FROZEN_ALTERNATIVE_EVIDENCE_INADEQUATE"));
                    classified.roles().forEach((key,role)->{if(key.residue().residueNumber()==residue)formalRoles.add(role);});}
                List<RecognitionStateObservation> supportingPoses=recurrent.stream().flatMap(b->b.members().stream())
                        .filter(o->o.graph().edges().stream().anyMatch(e->e.environmentResidue().residueNumber()==residue)).toList();
                Set<String> supportingSeeds=new TreeSet<>();supportingPoses.forEach(o->supportingSeeds.add(o.seedId()));
                Set<EvidenceQuality> qualities=new LinkedHashSet<>();
                Optional<DifferentialResidueScore> surface=supportingPoses.stream().flatMap(o->o.graph().edges().stream())
                        .filter(e->e.environmentResidue().residueNumber()==residue).peek(e->qualities.add(e.quality()))
                        .map(RecognitionEdge::differentialSurface).flatMap(Optional::stream).findFirst();
                String differential=surface.map(s->s.rup()>0.0||s.rus()>0.0?"DIFFERENTIAL":"PRESERVED").orElse("UNAVAILABLE");
                String scores=surface.map(s->"corresponding="+s.subjectResidue().map(Object::toString).orElse("UNMATCHED")
                        +",RUP="+s.rup()+",RUS="+s.rus()+",RSS="+s.rss()).orElse("corresponding=UNAVAILABLE,RUP=NA,RUS=NA,RSS=NA");
                lines.add(arm+" residue_"+residue+" persistent_recurrent_basins="+persistent+"/"+recurrent.size()
                        +" pose_support="+supportingPoses.size()+" seed_support="+supportingSeeds.size()
                        +" local_status="+differential+" "+scores+" evidence_quality="+qualities
                        +" formal_roles="+(formalRoles.isEmpty()?Set.of(InteractionRole.UNRESOLVED):formalRoles));}
        }
        lines.add("role_reason=defining requires adequate matched alternative-basin evidence; classification remains evidence-bound");
    }

    private static RecognitionBasinPolicy policy(double geometry,double topology){return new RecognitionBasinPolicy(geometry,topology,1,1,
            new RecognitionTopologyDistance.ScalarPolicy(1,0,0,0,0,"FROZEN_TYPED_JACCARD_ONLY_V1"),RecognitionBasinPolicy.PairSymmetryPolicy.MAXIMUM,"FROZEN_METTL7_BASIN_POLICY_V1");}
    private static Optional<RecognitionTopologyDistance> topology(RecognitionStateObservation a,RecognitionStateObservation b){
        RecognitionTopologyDistance d=Mettl7RecognitionBatchMaterializer.topology(a,b);double j=Mettl7RecognitionBatchMaterializer.typedJaccard(a,b);
        return Optional.of(new RecognitionTopologyDistance(d.preservedEdges(),d.substitutedEdges(),d.reroutedEdges(),d.lostEdges(),d.unobservedEdges(),d.unavailableEdges(),d.ligandFeatureRouteChanges(),d.residueRouteChanges(),j,d.matchedGeometryDeviation(),d.differentialSurfaceIntersectionChanges(),d.ambiguousAssignments()));
    }
    private static RecognitionPairAdmission admission(RecognitionStateObservation a,RecognitionStateObservation b,double geometry,double topology,RecognitionBasinPolicy policy){
        RecognitionTopologyDistance forward=topology(a,b).orElseThrow(),reverse=topology(b,a).orElseThrow();
        boolean reroute=forward.reroutedEdges()>0||reverse.reroutedEdges()>0;
        boolean ambiguity=forward.ambiguousAssignments()>0||reverse.ambiguousAssignments()>0;
        boolean matched=forward.preservedEdges()==Mettl7RecognitionBatchMaterializer.typedMultisetIntersection(a,b)
                &&reverse.preservedEdges()==Mettl7RecognitionBatchMaterializer.typedMultisetIntersection(b,a);
        boolean gp=geometry<=policy.maximumGeometryDistanceAngstroms(),tp=topology<=policy.maximumTopologyDistance();
        EvidenceQuality quality=a.quality().ordinal()>=b.quality().ordinal()?a.quality():b.quality();boolean admitted=gp&&tp&&!reroute&&matched&&!ambiguity;
        List<String> reasons=new ArrayList<>();if(!gp)reasons.add("GEOMETRY_THRESHOLD_FAILED");if(!tp)reasons.add("TOPOLOGY_THRESHOLD_FAILED");if(reroute)reasons.add("STABLE_FEATURE_REROUTE");if(!matched)reasons.add("MATCHED_EDGE_GEOMETRY_FAILED");if(ambiguity)reasons.add("TOPOLOGY_RELEVANT_AMBIGUITY");if(admitted)reasons.add("ALL_FROZEN_GATES_PASS");
        return new RecognitionPairAdmission(gp,tp,reroute,matched,ambiguity,quality,admitted,reasons,"FROZEN_METTL7_PAIR_ADMISSION_V1; bidirectional maximum");
    }
    private static String csv(Object...v){StringBuilder b=new StringBuilder();for(int i=0;i<v.length;i++){if(i>0)b.append(',');b.append('"').append(Objects.toString(v[i],"").replace("\"","\"\"")).append('"');}return b.append('\n').toString();}
    static String decimal(double value){return String.format(Locale.ROOT,"%.12f",value);}
    private static List<String> sorted(Collection<String> values){return values.stream().sorted().toList();}
    private static List<RecognitionEdge.Key> sortedKeys(Collection<RecognitionEdge.Key> values){return values.stream()
            .sorted(Comparator.comparing(RecognitionEdge.Key::ligandFeatureId)
                    .thenComparing(k->k.residue().chainId()).thenComparingInt(k->k.residue().residueNumber())
                    .thenComparing(k->Objects.toString(k.residue().insertionCode(),""))
                    .thenComparing(k->k.type().name())).toList();}
    private static List<totah.lab.gaia.structure.ResidueId> sortedResidues(Collection<totah.lab.gaia.structure.ResidueId> values){return values.stream()
            .sorted(Comparator.comparing(totah.lab.gaia.structure.ResidueId::chainId)
                    .thenComparingInt(totah.lab.gaia.structure.ResidueId::residueNumber)
                    .thenComparing(r->Objects.toString(r.insertionCode(),""))).toList();}
    private static String sha256(Path path)throws IOException{try{var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[]buf=new byte[8192];for(int n;(n=in.read(buf))>=0;)d.update(buf,0,n);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public record Result(Map<String,RecognitionBasinExecutionResult> primary,Path basins,Path pairs,Path receipt,int rawPoseCount,int observationCount){public Result{primary=Map.copyOf(primary);}}
}
