package totah.lab.athena.recognition;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Set;

/** Recurrent or non-recurrent empirical recognition basin; never thermodynamic. */
public record RecognitionBasin(String id, List<RecognitionStateObservation> members,
        List<String> poseIds, Set<String> seeds, Set<String> runs, Set<String> families,
        Set<String> ligandFeatureMapProvenance, String geometricMedoidPoseId,
        String topologyMedoidPoseId, boolean seedRecurrent, boolean familyRecurrent,
        Dispersion geometryDispersion, Dispersion topologyDispersion,
        Set<RecognitionEdge.Key> persistentEdges, Set<RecognitionEdge.Key> variableEdges,
        List<RecognitionConstraint> persistentConstraints,
        Set<ResidueId> differentialEnvironmentsEngaged, EvidenceQuality evidenceQuality,
        Set<String> ambiguityFlags) {
    public RecognitionBasin {
        members = List.copyOf(members); poseIds = List.copyOf(poseIds); seeds = Set.copyOf(seeds);
        runs = Set.copyOf(runs); families = Set.copyOf(families);
        ligandFeatureMapProvenance = Set.copyOf(ligandFeatureMapProvenance);
        persistentEdges = Set.copyOf(persistentEdges); variableEdges = Set.copyOf(variableEdges);
        persistentConstraints = List.copyOf(persistentConstraints);
        differentialEnvironmentsEngaged = Set.copyOf(differentialEnvironmentsEngaged);
        ambiguityFlags = Set.copyOf(ambiguityFlags);
        if (members.isEmpty()) throw new IllegalArgumentException("basin requires members");
    }
    public boolean recurrent() { return seedRecurrent && familyRecurrent; }
    public record Dispersion(double mean, double maximum, String units) { }
}
