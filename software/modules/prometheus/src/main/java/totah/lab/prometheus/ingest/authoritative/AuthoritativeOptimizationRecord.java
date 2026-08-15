package totah.lab.prometheus.ingest.authoritative;

import java.util.List;
import java.util.Map;

import totah.lab.prometheus.recovery.RecoveredField;

/** Raw-artifact reconstruction of one constrained QM optimization. */
public record AuthoritativeOptimizationRecord(
        RecoveredField<String> pointId,
        RecoveredField<Integer> charge,
        RecoveredField<Integer> multiplicity,
        RecoveredField<String> method,
        RecoveredField<Map<String, String>> softwareVersions,
        RecoveredField<List<String>> constraints,
        RecoveredField<Double> finalEnergyHartree,
        RecoveredField<Boolean> scfConverged,
        RecoveredField<String> optimizationStatus,
        RecoveredField<Integer> cycles,
        RecoveredField<String> finalGeometrySha256,
        List<String> verificationNotes) {

    public AuthoritativeOptimizationRecord {
        verificationNotes = List.copyOf(verificationNotes);
    }
}
