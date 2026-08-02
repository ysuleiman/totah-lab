package totah.lab.report.validation;

import totah.lab.report.model.PocketReport;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class PocketReportValidator {

    public void validate(PocketReport report) {
        Objects.requireNonNull(report, "report");
        Set<String> identifiers = new HashSet<>();
        report.evidence().forEach(evidence -> {
            if (evidence.id().isBlank()) {
                throw new IllegalArgumentException(
                        "Report evidence identifier must not be blank");
            }
            if (!identifiers.add(evidence.id())) {
                throw new IllegalArgumentException(
                        "Duplicate report evidence identifier: " + evidence.id());
            }
            evidence.metrics().forEach((name, value) -> {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "Evidence metric must be finite: "
                                    + evidence.id() + "." + name);
                }
            });
        });
    }
}
