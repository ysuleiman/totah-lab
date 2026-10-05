package totah.lab.athena.design.backend;

import java.util.*;
import totah.lab.mnemosyne.EvidenceInterpretation.Status;

/** Independent source-state checks. Neutrality is a requested proposition, not chemical validity. */
public interface MolecularValidationService {
    enum Dimension { TOPOLOGY_VALENCE, SUPPLIED_H_STATE, STEREOCHEMISTRY, NET_NEUTRALITY, OCL_COORDINATE_COMPATIBILITY }
    enum NeutralityPolicy { OBSERVE_ONLY, REQUIRE_COMPONENT_NEUTRAL }
    record Assessment(Status status, List<String> reasons) {
        public Assessment { Objects.requireNonNull(status); reasons=List.copyOf(reasons); if(reasons.isEmpty())throw new IllegalArgumentException("reasons required"); }
    }
    record Result(Map<Dimension,Assessment> dimensions, int netFormalCharge, BackendEvidence evidence) {
        public Result {
            var copy=new EnumMap<Dimension,Assessment>(Dimension.class); copy.putAll(dimensions);
            if(!copy.keySet().equals(EnumSet.allOf(Dimension.class))||copy.containsValue(null))throw new IllegalArgumentException("all dimensions required");
            dimensions=Collections.unmodifiableMap(copy); Objects.requireNonNull(evidence);
        }
    }
    Result validateDimensions(MolecularGraph graph, NeutralityPolicy neutrality) throws MolecularBackendException;
}
