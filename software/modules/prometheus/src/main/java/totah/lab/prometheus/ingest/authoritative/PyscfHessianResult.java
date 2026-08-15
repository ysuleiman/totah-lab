package totah.lab.prometheus.ingest.authoritative;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import totah.lab.prometheus.recovery.RecoveredField;

/** Authoritative reconstruction of a PySCF analytic-Hessian directory. */
public record PyscfHessianResult(
        RecoveredField<String> calculationId,
        RecoveredField<String> method,
        ElectronicStructureProtocol protocol,
        RecoveredField<Integer> charge,
        RecoveredField<Integer> multiplicity,
        Map<String, RecoveredField<String>> softwareVersions,
        RecoveredField<Double> energyHartree,
        RecoveredField<List<Double>> cartesianHessian,
        int cartesianDimension,
        String hessianUnit,
        RecoveredField<List<Double>> frequencies,
        String frequencyUnit,
        RecoveredField<String> frequencyProjection,
        RecoveredField<Boolean> scfConverged,
        RecoveredField<String> status,
        boolean artifactChecksumsVerified,
        String normalModeConvention,
        List<RawValueDiscrepancy> comparisons) {
    public PyscfHessianResult {
        Objects.requireNonNull(calculationId, "calculationId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(protocol, "protocol");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(multiplicity, "multiplicity");
        softwareVersions = Map.copyOf(Objects.requireNonNull(softwareVersions, "softwareVersions"));
        Objects.requireNonNull(energyHartree, "energyHartree");
        Objects.requireNonNull(cartesianHessian, "cartesianHessian");
        Objects.requireNonNull(hessianUnit, "hessianUnit");
        Objects.requireNonNull(frequencies, "frequencies");
        Objects.requireNonNull(frequencyUnit, "frequencyUnit");
        Objects.requireNonNull(frequencyProjection, "frequencyProjection");
        Objects.requireNonNull(scfConverged, "scfConverged");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(normalModeConvention, "normalModeConvention");
        comparisons = List.copyOf(Objects.requireNonNull(comparisons, "comparisons"));
    }
}
