package totah.lab.prometheus.ingest.authoritative;

import java.util.List;
import java.util.Map;

import totah.lab.prometheus.recovery.RecoveredField;

/** Raw-artifact reconstruction of one counterpoise intermolecular probe point. */
public record AuthoritativeProbeRecord(
        RecoveredField<String> pointId,
        RecoveredField<String> minimumId,
        RecoveredField<String> interactionClass,
        RecoveredField<Double> targetDistanceAngstrom,
        RecoveredField<Integer> charge,
        RecoveredField<Integer> multiplicity,
        RecoveredField<String> method,
        RecoveredField<Map<String, String>> softwareVersions,
        RecoveredField<Double> dimerElectronicEnergyHartree,
        RecoveredField<Double> tslGhostProbeEnergyHartree,
        RecoveredField<Double> probeGhostTslEnergyHartree,
        RecoveredField<Double> d3InteractionEnergyHartree,
        RecoveredField<Double> interactionEnergyKcalMol,
        RecoveredField<Boolean> scfConverged,
        RecoveredField<String> geometrySha256,
        RecoveredField<String> geometryClassification,
        RecoveredField<String> validationEligibility,
        RecoveredField<Double> closestNonTargetDistanceAngstrom,
        RecoveredField<Double> closestNonTargetVdwOverlapAngstrom,
        List<RawValueDiscrepancy> rawArtifactDiscrepancies,
        List<String> verificationNotes) {

    public AuthoritativeProbeRecord {
        rawArtifactDiscrepancies = List.copyOf(rawArtifactDiscrepancies);
        verificationNotes = List.copyOf(verificationNotes);
    }

    public boolean geometryValidForValidation() {
        return !validationEligibility.value().orElse("").startsWith("EXCLUDE_");
    }
}
