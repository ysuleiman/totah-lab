package totah.lab.athena.recognition;

import totah.lab.euclid.spatial.CompleteLinkClusterer;
import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds empirical complete-link basins under simultaneous geometry and topology gates. */
public final class RecognitionBasinBuilder {
    /** Additive evidence-retaining execution path with explicit semantic gates and recurrence. */
    public RecognitionBasinExecutionResult buildWithAdmissions(List<RecognitionStateObservation> observations,
            RecognitionGeometryDistance geometry, RecognitionTopologyDistanceProvider topology,
            RecognitionBasinPolicy policy, RecognitionPairAdmissionProvider admissionProvider,
            RecognitionRecurrencePolicy recurrencePolicy) {
        List<RecognitionStateObservation> states=observations.stream().sorted(Comparator.comparing(RecognitionStateObservation::poseId)).toList();
        int size=states.size();double[][] geom=new double[size][size],topo=new double[size][size];
        List<List<Double>> joint=new ArrayList<>();List<RecognitionBasinBuildResult.EvidenceInsufficiency> insufficient=new ArrayList<>();
        List<RecognitionBasinExecutionResult.PairResult> admissions=new ArrayList<>();
        for(int i=0;i<size;i++)for(int j=i+1;j<size;j++){
            var gf=geometry.distanceAngstroms(states.get(i),states.get(j));var gr=geometry.distanceAngstroms(states.get(j),states.get(i));
            var tf=topology.distance(states.get(i),states.get(j));var tr=topology.distance(states.get(j),states.get(i));
            if(gf.isEmpty()||gr.isEmpty()||tf.isEmpty()||tr.isEmpty()){
                geom[i][j]=geom[j][i]=topo[i][j]=topo[j][i]=Double.POSITIVE_INFINITY;
                insufficient.add(new RecognitionBasinBuildResult.EvidenceInsufficiency(states.get(i).poseId(),states.get(j).poseId(),"bidirectional pair evidence unavailable"));continue;
            }
            double gd=symmetric(gf.getAsDouble(),gr.getAsDouble(),policy.pairSymmetryPolicy());
            double td=symmetric(tf.get().scalarDistance(policy.topologyScalarPolicy()),tr.get().scalarDistance(policy.topologyScalarPolicy()),policy.pairSymmetryPolicy());
            RecognitionPairAdmission admission=admissionProvider.assess(states.get(i),states.get(j),gd,td,policy);
            admissions.add(new RecognitionBasinExecutionResult.PairResult(states.get(i).poseId(),states.get(j).poseId(),admission));
            geom[i][j]=geom[j][i]=gd;topo[i][j]=topo[j][i]=td;
            if(!admission.sameBasinAdmissible())geom[i][j]=geom[j][i]=topo[i][j]=topo[j][i]=Double.POSITIVE_INFINITY;
        }
        for(int i=0;i<size;i++){List<Double> row=new ArrayList<>();for(int j=0;j<size;j++)row.add(Double.isFinite(geom[i][j])
                ?Math.max(ratio(geom[i][j],policy.maximumGeometryDistanceAngstroms()),ratio(topo[i][j],policy.maximumTopologyDistance())):2.0);joint.add(row);}
        List<RecognitionBasinExecutionResult.BasinResult> basins=new ArrayList<>();int index=0;
        for(List<Integer> cluster:new CompleteLinkClusterer().cluster(joint,1.0)){
            RecognitionBasin b=basin("RB-"+(++index),cluster,states,geom,topo,policy);
            basins.add(new RecognitionBasinExecutionResult.BasinResult(b,recurrencePolicy.recurrent(b)));
        }
        return new RecognitionBasinExecutionResult(basins,admissions,insufficient,recurrencePolicy);
    }
    public RecognitionBasinBuildResult build(List<RecognitionStateObservation> observations,
            RecognitionGeometryDistance geometry, RecognitionTopologyDistanceProvider topology,
            RecognitionBasinPolicy policy) {
        List<RecognitionStateObservation> states = observations.stream()
                .sorted(Comparator.comparing(RecognitionStateObservation::poseId)).toList();
        List<RecognitionBasinBuildResult.EvidenceInsufficiency> insufficiencies = new ArrayList<>();
        int size = states.size();
        double[][] geom = new double[size][size], topo = new double[size][size];
        List<List<Double>> joint = new ArrayList<>();
        for (int i = 0; i < size; i++) for (int j = i + 1; j < size; j++) {
            var gdForward = geometry.distanceAngstroms(states.get(i), states.get(j));
            var gdReverse = geometry.distanceAngstroms(states.get(j), states.get(i));
            var tdForward = topology.distance(states.get(i), states.get(j));
            var tdReverse = topology.distance(states.get(j), states.get(i));
            if (gdForward.isEmpty() || gdReverse.isEmpty() || tdForward.isEmpty() || tdReverse.isEmpty()) {
                geom[i][j] = geom[j][i] = topo[i][j] = topo[j][i] = Double.POSITIVE_INFINITY;
                insufficiencies.add(new RecognitionBasinBuildResult.EvidenceInsufficiency(
                        states.get(i).poseId(), states.get(j).poseId(), "bidirectional pair evidence unavailable"));
            } else {
                geom[i][j] = geom[j][i] = symmetric(gdForward.getAsDouble(), gdReverse.getAsDouble(), policy.pairSymmetryPolicy());
                double first = tdForward.get().scalarDistance(policy.topologyScalarPolicy());
                double second = tdReverse.get().scalarDistance(policy.topologyScalarPolicy());
                topo[i][j] = topo[j][i] = symmetric(first, second, policy.pairSymmetryPolicy());
            }
        }
        for (int i = 0; i < size; i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < size; j++) row.add(Double.isFinite(geom[i][j])
                    ? Math.max(ratio(geom[i][j], policy.maximumGeometryDistanceAngstroms()),
                    ratio(topo[i][j], policy.maximumTopologyDistance())) : 2.0);
            joint.add(row);
        }
        List<List<Integer>> clusters = new CompleteLinkClusterer().cluster(joint, 1.0);
        List<RecognitionBasin> recurrent = new ArrayList<>(), nonRecurrent = new ArrayList<>();
        for (int index = 0; index < clusters.size(); index++) {
            RecognitionBasin basin = basin("RB-" + (index + 1), clusters.get(index), states, geom, topo, policy);
            (basin.recurrent() ? recurrent : nonRecurrent).add(basin);
        }
        return new RecognitionBasinBuildResult(recurrent, nonRecurrent,
                List.of(), insufficiencies);
    }

    private static RecognitionBasin basin(String id, List<Integer> indexes,
            List<RecognitionStateObservation> states, double[][] geom, double[][] topo,
            RecognitionBasinPolicy policy) {
        List<RecognitionStateObservation> members = indexes.stream().map(states::get).toList();
        Set<String> seeds = collect(members, RecognitionStateObservation::seedId);
        Set<String> runs = collect(members, RecognitionStateObservation::runId);
        Set<String> families = members.stream().flatMap(m -> m.familyId().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> maps = collect(members, RecognitionStateObservation::ligandFeatureMapProvenance);
        Set<RecognitionEdge.Key> union = new LinkedHashSet<>(), persistent = null;
        Set<ResidueId> differential = new LinkedHashSet<>();
        for (RecognitionStateObservation member : members) {
            Set<RecognitionEdge.Key> keys = member.graph().edges().stream()
                    .filter(e -> e.quality() == EvidenceQuality.ADEQUATE).map(RecognitionEdge::key)
                    .collect(java.util.stream.Collectors.toSet());
            union.addAll(keys); if (persistent == null) persistent = new LinkedHashSet<>(keys); else persistent.retainAll(keys);
            member.graph().edges().stream().filter(RecognitionEdge::engagesDifferentialEnvironment)
                    .map(RecognitionEdge::environmentResidue).forEach(differential::add);
        }
        Set<RecognitionEdge.Key> variable = new LinkedHashSet<>(union); variable.removeAll(persistent);
        Map<String, Integer> constraintCounts = new HashMap<>(); Map<String, RecognitionConstraint> constraints = new HashMap<>();
        members.forEach(m -> m.constraints().forEach(c -> { constraintCounts.merge(c.id(), 1, Integer::sum); constraints.put(c.id(), c); }));
        List<RecognitionConstraint> persistentConstraints = constraintCounts.entrySet().stream()
                .filter(e -> e.getValue() == members.size()).map(e -> constraints.get(e.getKey())).toList();
        EvidenceQuality quality = members.stream().map(RecognitionStateObservation::quality)
                .max(Comparator.comparingInt(Enum::ordinal)).orElse(EvidenceQuality.UNAVAILABLE);
        Set<String> flags = new LinkedHashSet<>();
        if (maps.size() > 1) flags.add("MULTIPLE_FEATURE_MAP_PROVENANCE");
        if (quality != EvidenceQuality.ADEQUATE) flags.add("NON_ADEQUATE_EVIDENCE");
        return new RecognitionBasin(id, members, members.stream().map(RecognitionStateObservation::poseId).toList(),
                seeds, runs, families, maps, medoid(indexes, states, geom), medoid(indexes, states, topo),
                seeds.size() >= policy.minimumDistinctSeeds(), families.size() >= policy.minimumDistinctFamilies(),
                dispersion(indexes, geom, "angstrom"), dispersion(indexes, topo, "explicit topology distance"),
                persistent == null ? Set.of() : persistent, variable, persistentConstraints, differential, quality, flags);
    }

    private static String medoid(List<Integer> indexes, List<RecognitionStateObservation> states, double[][] matrix) {
        return indexes.stream().min(Comparator.<Integer>comparingDouble(i -> indexes.stream().mapToDouble(j -> matrix[i][j]).sum())
                .thenComparing(i -> states.get(i).poseId())).map(i -> states.get(i).poseId()).orElseThrow();
    }
    private static RecognitionBasin.Dispersion dispersion(List<Integer> indexes, double[][] matrix, String units) {
        double sum = 0, max = 0; int count = 0;
        for (int a = 0; a < indexes.size(); a++) for (int b = a + 1; b < indexes.size(); b++) {
            double value = matrix[indexes.get(a)][indexes.get(b)]; sum += value; max = Math.max(max, value); count++;
        }
        return new RecognitionBasin.Dispersion(count == 0 ? 0 : sum / count, max, units);
    }
    private static double ratio(double value, double threshold) { return threshold == 0 ? (value == 0 ? 0 : 2) : value / threshold; }
    private static double symmetric(double first, double second, RecognitionBasinPolicy.PairSymmetryPolicy policy) {
        if (!Double.isFinite(first) || first < 0 || !Double.isFinite(second) || second < 0)
            throw new IllegalArgumentException("pair distances must be finite and non-negative");
        return switch (policy) {
            case MAXIMUM -> Math.max(first, second);
            case MEAN -> (first + second) / 2.0;
            case REQUIRE_EQUAL -> {
                if (Double.compare(first, second) != 0) throw new IllegalArgumentException("directional pair distances differ");
                yield first;
            }
        };
    }
    private static Set<String> collect(List<RecognitionStateObservation> members,
            java.util.function.Function<RecognitionStateObservation, String> function) {
        return members.stream().map(function).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
