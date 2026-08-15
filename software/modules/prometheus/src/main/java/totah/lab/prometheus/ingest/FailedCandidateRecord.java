package totah.lab.prometheus.ingest;

import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.candidate.EvidenceClass;

/**
 * The parsed outcome of a closed development branch or a validated diagnostic:
 * which branch, the classification string recovered from its decision report
 * (never hard-coded), its evidence class, the report it came from, related
 * evidence hashes, and a short summary.
 *
 * <p>Classification-to-class mapping is deliberately explicit: only
 * {@code ANGLE_LJ_COUPLED_DEFECT_SUPPORTED} is a {@link EvidenceClass#VALIDATED_DIAGNOSTIC};
 * every other parsed classification — including
 * {@code SHORT_RANGE_CORRECTION_VALIDATED} and
 * {@code MINIMAL_FIXED_CHARGE_NONBONDED_CORRECTION_PASSES}, which passed narrow
 * gates but were explicitly rejected as production models — is a
 * {@link EvidenceClass#FAILED_CANDIDATE}.
 */
public record FailedCandidateRecord(
        String branch,
        String classification,
        EvidenceClass evidenceClass,
        String reportPath,
        List<String> relatedEvidenceHashes,
        String summary) {

    public FailedCandidateRecord {
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(evidenceClass, "evidenceClass");
        Objects.requireNonNull(reportPath, "reportPath");
        relatedEvidenceHashes = List.copyOf(
                Objects.requireNonNull(relatedEvidenceHashes, "relatedEvidenceHashes"));
        Objects.requireNonNull(summary, "summary");
    }

    /** The one classification the archive establishes as a validated diagnostic. */
    public static final String VALIDATED_DIAGNOSTIC_CLASSIFICATION =
            "ANGLE_LJ_COUPLED_DEFECT_SUPPORTED";

    /** Maps a parsed classification string to its evidence class (see class javadoc). */
    public static EvidenceClass evidenceClassFor(String classification) {
        Objects.requireNonNull(classification, "classification");
        return VALIDATED_DIAGNOSTIC_CLASSIFICATION.equals(classification)
                ? EvidenceClass.VALIDATED_DIAGNOSTIC
                : EvidenceClass.FAILED_CANDIDATE;
    }
}
