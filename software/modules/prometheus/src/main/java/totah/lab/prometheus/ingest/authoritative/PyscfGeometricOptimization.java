package totah.lab.prometheus.ingest.authoritative;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.recovery.RecoveredField;

/** Authoritative reconstruction of a PySCF/geomeTRIC optimization directory. */
public record PyscfGeometricOptimization(
        RecoveredField<String> calculationId,
        RecoveredField<String> method,
        ElectronicStructureProtocol protocol,
        RecoveredField<Integer> charge,
        RecoveredField<Integer> multiplicity,
        RecoveredField<String> constraints,
        Map<String, RecoveredField<String>> softwareVersions,
        RecoveredField<CartesianGeometry> finalGeometry,
        RecoveredField<Double> finalEnergyHartree,
        RecoveredField<List<Double>> finalGradientHartreePerBohr,
        RecoveredField<Boolean> scfConverged,
        RecoveredField<Boolean> geometryConverged,
        RecoveredField<Integer> optimizationCycles,
        List<RawValueDiscrepancy> comparisons) {
    public PyscfGeometricOptimization {
        Objects.requireNonNull(calculationId, "calculationId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(multiplicity, "multiplicity");
        Objects.requireNonNull(constraints, "constraints");
        softwareVersions = Map.copyOf(Objects.requireNonNull(softwareVersions, "softwareVersions"));
        Objects.requireNonNull(finalGeometry, "finalGeometry");
        Objects.requireNonNull(finalEnergyHartree, "finalEnergyHartree");
        Objects.requireNonNull(finalGradientHartreePerBohr, "finalGradientHartreePerBohr");
        Objects.requireNonNull(scfConverged, "scfConverged");
        Objects.requireNonNull(geometryConverged, "geometryConverged");
        Objects.requireNonNull(optimizationCycles, "optimizationCycles");
        comparisons = List.copyOf(Objects.requireNonNull(comparisons, "comparisons"));
    }
}
