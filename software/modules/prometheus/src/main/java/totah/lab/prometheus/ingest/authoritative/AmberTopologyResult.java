package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.RecoveredField;
import java.util.List;

public record AmberTopologyResult(RecoveredField<Integer> atomCount,
                                  RecoveredField<List<String>> atomNames,
                                  RecoveredField<List<String>> atomTypes,
                                  RecoveredField<List<Double>> charges,
                                  RecoveredField<Double> totalCharge) {
}
