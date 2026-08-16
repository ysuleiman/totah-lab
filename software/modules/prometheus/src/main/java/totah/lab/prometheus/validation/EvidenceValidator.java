package totah.lab.prometheus.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.identity.EvidenceAtomMap;
import totah.lab.prometheus.validation.GeometryClashChecker.ClashAtom;

/**
 * Stateless validator turning raw evidence properties into
 * {@link EvidenceAcceptanceState} verdicts. Failed QM can never become accepted
 * evidence, and a checksum mismatch invalidates reuse — both surface here as
 * non-ACCEPTED states with an explicit reason.
 */
public final class EvidenceValidator {

    /** Default Bondi scale factor for probe-geometry audits (as in the TSL audit). */
    public static final double DEFAULT_PROBE_SCALE_FACTOR = 0.75;

    private static final double HESSIAN_SYMMETRY_TOLERANCE = 1e-8;

    /** Outcome of a checksum verification, always carrying the reason. */
    public record ChecksumOutcome(EvidenceAcceptanceState state, String reason) {

        public ChecksumOutcome {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Outcome of a probe-geometry audit: the state plus the clashes found. */
    public record GeometryOutcome(EvidenceAcceptanceState state, List<String> clashes) {

        public GeometryOutcome {
            Objects.requireNonNull(state, "state");
            clashes = List.copyOf(Objects.requireNonNull(clashes, "clashes"));
        }
    }

    /**
     * Outcome of a Hessian sanity check.
     *
     * <p>{@code massWeighted} is always {@code false}: whether a Hessian is
     * mass-weighted cannot be inferred from its numbers and must be declared by
     * the producer, never guessed.
     */
    public record HessianOutcome(EvidenceAcceptanceState state, String reason, boolean massWeighted) {

        public HessianOutcome {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * Verifies an evidence artifact against its expected SHA-256, streaming the
     * file with an 8 KB buffer (the artifact is never loaded fully into memory).
     * A mismatch — or an unreadable/missing file — yields
     * {@link EvidenceAcceptanceState#CHECKSUM_INVALID}: a checksum mismatch
     * invalidates reuse of the artifact.
     */
    public ChecksumOutcome verifyChecksum(Path actualFile, String expectedSha256) {
        Objects.requireNonNull(actualFile, "actualFile");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        MessageDigest digest = newSha256();
        try (InputStream in = Files.newInputStream(actualFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            return new ChecksumOutcome(EvidenceAcceptanceState.CHECKSUM_INVALID,
                    "cannot read evidence artifact " + actualFile + ": " + e.getMessage());
        }
        String actual = toHex(digest.digest());
        if (actual.equalsIgnoreCase(expectedSha256)) {
            return new ChecksumOutcome(EvidenceAcceptanceState.ACCEPTED,
                    "checksum matches: " + actual);
        }
        return new ChecksumOutcome(EvidenceAcceptanceState.CHECKSUM_INVALID,
                "checksum mismatch invalidates reuse: expected " + expectedSha256
                        + " but computed " + actual + " for " + actualFile);
    }

    /** Maps the convergence status of a QM calculation to an acceptance state. */
    public EvidenceAcceptanceState verifyConvergence(QuantumEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        ConvergenceStatus convergence = evidence.convergence();
        return switch (convergence) {
            case CONVERGED -> EvidenceAcceptanceState.ACCEPTED;
            case FAILED, NOT_CONVERGED -> EvidenceAcceptanceState.FAILED_NUMERICALLY;
            case EMPTY_OUTPUT -> EvidenceAcceptanceState.PROTOCOL_INCOMPLETE;
            case UNKNOWN -> EvidenceAcceptanceState.PENDING;
        };
    }

    /** Two atom maps agree iff their crosswalk hashes are equal. */
    public EvidenceAcceptanceState verifyAtomMap(EvidenceAtomMap expected, EvidenceAtomMap actual) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        return expected.crosswalkHash().equals(actual.crosswalkHash())
                ? EvidenceAcceptanceState.ACCEPTED
                : EvidenceAcceptanceState.ATOM_MAP_INVALID;
    }

    /**
     * Audits a probe-complex geometry for clashes. Any clash yields
     * {@link EvidenceAcceptanceState#GEOMETRY_INVALID} with the clash
     * descriptions in the outcome; a clean geometry is ACCEPTED.
     *
     * <p>{@code bondedPairs} declares the covalently bound label pairs that
     * must be excluded from the clash test. A {@code null} {@code checker}
     * selects a {@link BondiClashChecker} with
     * {@link #DEFAULT_PROBE_SCALE_FACTOR} and these bonded pairs; a non-null
     * checker must already be configured with the same exclusions (a
     * {@code BondiClashChecker} takes them at construction).
     */
    public GeometryOutcome verifyProbeGeometry(
            List<ClashAtom> probeComplex,
            Set<Set<String>> bondedPairs,
            GeometryClashChecker checker) {

        Objects.requireNonNull(probeComplex, "probeComplex");
        Objects.requireNonNull(bondedPairs, "bondedPairs");
        GeometryClashChecker effective = checker != null
                ? checker
                : new BondiClashChecker(DEFAULT_PROBE_SCALE_FACTOR, bondedPairs);
        List<String> clashes = effective.clashes(probeComplex);
        if (!clashes.isEmpty()) {
            return new GeometryOutcome(EvidenceAcceptanceState.GEOMETRY_INVALID, clashes);
        }
        return new GeometryOutcome(EvidenceAcceptanceState.ACCEPTED, List.of());
    }

    /**
     * Sanity-checks a row-major Hessian: it must be 3N×3N for {@code atomCount}
     * atoms (else PROTOCOL_INCOMPLETE) and symmetric within 1e-8 (else
     * FAILED_NUMERICALLY).
     */
    public HessianOutcome verifyHessian(List<Double> hessianRowMajor, int atomCount) {
        Objects.requireNonNull(hessianRowMajor, "hessianRowMajor");
        if (atomCount < 1) {
            throw new IllegalArgumentException("atomCount must be >= 1, got " + atomCount);
        }
        int dimension = 3 * atomCount;
        int expected = dimension * dimension;
        if (hessianRowMajor.size() != expected) {
            return new HessianOutcome(EvidenceAcceptanceState.PROTOCOL_INCOMPLETE,
                    "hessian has " + hessianRowMajor.size() + " values, expected 3N×3N = "
                            + expected + " for N=" + atomCount + " atoms",
                    false);
        }
        for (int index = 0; index < hessianRowMajor.size(); index++) {
            Double value = hessianRowMajor.get(index);
            if (value == null || !Double.isFinite(value)) {
                return new HessianOutcome(EvidenceAcceptanceState.FAILED_NUMERICALLY,
                        "hessian contains a non-finite value at row-major index " + index, false);
            }
        }
        for (int row = 0; row < dimension; row++) {
            for (int col = row + 1; col < dimension; col++) {
                double upper = hessianRowMajor.get(row * dimension + col);
                double lower = hessianRowMajor.get(col * dimension + row);
                if (Math.abs(upper - lower) > HESSIAN_SYMMETRY_TOLERANCE) {
                    return new HessianOutcome(EvidenceAcceptanceState.FAILED_NUMERICALLY,
                            "hessian not symmetric within 1e-8: ["
                                    + row + "][" + col + "]=" + upper + " vs ["
                                    + col + "][" + row + "]=" + lower,
                            false);
                }
            }
        }
        return new HessianOutcome(EvidenceAcceptanceState.ACCEPTED,
                "hessian is 3N×3N and symmetric within 1e-8", false);
    }

    /**
     * Combines individual checks into a final acceptance state: ACCEPTED iff
     * every check is ACCEPTED; otherwise the highest-precedence failing state.
     *
     * <p>Precedence: CHECKSUM_INVALID &gt; ATOM_MAP_INVALID &gt; GEOMETRY_INVALID
     * &gt; FAILED_NUMERICALLY &gt; PROTOCOL_INCOMPLETE &gt; EXCLUDED_BY_PROTOCOL
     * &gt; PENDING.
     */
    public EvidenceAcceptanceState finalAcceptance(List<EvidenceAcceptanceState> checks) {
        Objects.requireNonNull(checks, "checks");
        if (checks.isEmpty()) {
            throw new IllegalArgumentException("at least one acceptance check is required");
        }
        EvidenceAcceptanceState worst = EvidenceAcceptanceState.ACCEPTED;
        int worstRank = -1;
        for (EvidenceAcceptanceState check : checks) {
            int rank = precedenceRank(Objects.requireNonNull(check, "check"));
            if (rank > worstRank) {
                worstRank = rank;
                worst = check;
            }
        }
        return worst;
    }

    private static int precedenceRank(EvidenceAcceptanceState state) {
        return switch (state) {
            case ACCEPTED -> -1;
            case PENDING -> 0;
            case EXCLUDED_BY_PROTOCOL -> 1;
            case PROTOCOL_INCOMPLETE -> 2;
            case FAILED_NUMERICALLY -> 3;
            case GEOMETRY_INVALID -> 4;
            case ATOM_MAP_INVALID -> 5;
            case CHECKSUM_INVALID -> 6;
        };
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
