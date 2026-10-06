package totah.lab.athena.system;

import totah.lab.mnemosyne.*;
import java.util.*;

/** Registered versioned analyzer; observation preservation is the caller's prerequisite. */
public interface SystemGraphAnalyzer {
    ScientificReference method();
    Set<SystemGraphCertificate.Capability> requires();
    Set<SystemGraphCertificate.Capability> qualifies();
    Set<String> evidenceTypes();
    List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> evidence,Map<String,String> configuration) throws Exception;
    record Finding(String id,List<EvidenceSubject> subjects,EvidenceInterpretation.Status status,
                   Map<String,String> measurements,List<String> reasons,List<String> limitations) {
        public Finding { if(id==null||id.isBlank())throw new IllegalArgumentException("finding id required");
            subjects=List.copyOf(subjects);Objects.requireNonNull(status);measurements=Collections.unmodifiableMap(new TreeMap<>(measurements));
            reasons=List.copyOf(reasons);limitations=List.copyOf(limitations);if(subjects.isEmpty()||reasons.isEmpty())throw new IllegalArgumentException("subjects and reasons required"); }
    }
}
