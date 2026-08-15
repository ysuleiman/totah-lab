package totah.lab.prometheus.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * Immutable report of all functional-form diagnostics issued for one molecule.
 */
public record DiagnosisReport(
        MoleculeIdentity molecule,
        List<FunctionalFormDiagnostic> diagnostics,
        Instant createdAt) {

    public DiagnosisReport {
        Objects.requireNonNull(molecule, "molecule");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** All diagnostics carrying the given classification, in report order. */
    public List<FunctionalFormDiagnostic> byClassification(FunctionalFormClassification classification) {
        Objects.requireNonNull(classification, "classification");
        return diagnostics.stream()
                .filter(d -> d.classification() == classification)
                .toList();
    }
}
