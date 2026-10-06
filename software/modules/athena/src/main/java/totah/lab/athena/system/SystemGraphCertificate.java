package totah.lab.athena.system;

import totah.lab.mnemosyne.*;
import java.time.Instant;
import java.util.*;

/** Immutable capability-specific judgment bound to exact state, inputs, configuration and rules. */
public record SystemGraphCertificate(ScientificReference reference, SystemStateView.Binding binding,
                                     String configurationSha256, EvidenceAdmission.Pin evidenceSnapshot,
                                     List<ScientificReference> analyzers, Map<Capability,Qualification> capabilities,
                                     List<Check> checks, List<ScientificReference> interpretations,
                                     List<String> limitations, Instant recordedAt) {
    public enum Capability { DISTANCE_QUERIES, NEIGHBORHOOD_TRAVERSAL, GRAPH_TRANSFORMATIONS, CLASH_ANALYSIS, INTERACTION_TYPING, HBOND_ANALYSIS, ENERGETICS }
    public enum Status { QUALIFIED, CONDITIONAL, NOT_EVALUATED, UNSUPPORTED, FAILED }
    public record Qualification(Status status,List<String> reasons) {
        public Qualification {Objects.requireNonNull(status);reasons=List.copyOf(reasons);if(reasons.isEmpty())throw new IllegalArgumentException("reasons required");}
    }
    public record Check(String dimension,String name,Status status,String detail) { }
    public SystemGraphCertificate {
        Objects.requireNonNull(reference);Objects.requireNonNull(binding);Objects.requireNonNull(configurationSha256);Objects.requireNonNull(evidenceSnapshot);
        analyzers=List.copyOf(analyzers);capabilities=Collections.unmodifiableMap(new TreeMap<>(capabilities));
        checks=List.copyOf(checks);interpretations=List.copyOf(interpretations);limitations=List.copyOf(limitations);Objects.requireNonNull(recordedAt);
        if(!capabilities.keySet().equals(EnumSet.allOf(Capability.class)))throw new IllegalArgumentException("every capability needs explicit status");
    }
    public Qualification require(Capability capability,SystemStateView actual,Map<String,String> configuration,
                                 EvidenceAdmission.Pin evidence,List<ScientificReference> analyzerVersions,boolean acceptConditional) {
        if(!binding.equals(actual.binding())||!configurationSha256.equals(SystemStateView.digest(new TreeMap<>(configuration)))
                ||!evidenceSnapshot.equals(evidence)||!analyzers.equals(analyzerVersions))throw new IllegalArgumentException("stale certificate binding");
        var value=capabilities.get(capability);
        if(value.status()!=Status.QUALIFIED && !(acceptConditional&&value.status()==Status.CONDITIONAL))
            throw new IllegalStateException("capability not qualified: "+capability+": "+value);
        return value;
    }
}
