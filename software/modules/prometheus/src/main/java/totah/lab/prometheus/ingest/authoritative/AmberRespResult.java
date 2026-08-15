package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.RecoveredField;

import java.util.List;
import java.util.Map;

/** Values reconstructed from native RESP inputs and outputs, never from a summary report. */
public record AmberRespResult(
        RecoveredField<String> software,
        RecoveredField<Integer> conformerCount,
        RecoveredField<List<String>> conformerIds,
        RecoveredField<List<Integer>> conformerFormalCharges,
        RecoveredField<Integer> atomCount,
        RecoveredField<Double> stage1RestraintWeight,
        RecoveredField<Double> stage2RestraintWeight,
        RecoveredField<Map<Integer, Integer>> stage2EquivalenceConstraints,
        RecoveredField<List<Double>> serializedCharges,
        RecoveredField<Double> serializedTotalCharge,
        RecoveredField<Boolean> converged,
        RecoveredField<String> espQuantumMethod) {
}
