package totah.lab.prometheus.planning;

import java.util.Collection;
import java.util.Objects;

/**
 * Coarse, pre-authorization cost estimate for a planned calculation or a whole
 * plan. All components are non-negative. These numbers exist so a human can
 * authorize (or reject) a plan before any expensive calculation launches; they
 * are not measurements.
 */
public record CostEstimate(
        int jobCount,
        double cpuHoursPerJob,
        double expectedWallHours,
        double expectedLocalRuntimeHours,
        double estimatedRemoteCostUsd) {

    public CostEstimate {
        if (jobCount < 0) {
            throw new IllegalArgumentException("jobCount must be >= 0");
        }
        if (!Double.isFinite(cpuHoursPerJob) || cpuHoursPerJob < 0) {
            throw new IllegalArgumentException("cpuHoursPerJob must be >= 0");
        }
        if (!Double.isFinite(expectedWallHours) || expectedWallHours < 0) {
            throw new IllegalArgumentException("expectedWallHours must be >= 0");
        }
        if (!Double.isFinite(expectedLocalRuntimeHours) || expectedLocalRuntimeHours < 0) {
            throw new IllegalArgumentException("expectedLocalRuntimeHours must be >= 0");
        }
        if (!Double.isFinite(estimatedRemoteCostUsd) || estimatedRemoteCostUsd < 0) {
            throw new IllegalArgumentException("estimatedRemoteCostUsd must be >= 0");
        }
    }

    /** The empty estimate: no jobs, no cost. */
    public static CostEstimate zero() {
        return new CostEstimate(0, 0.0, 0.0, 0.0, 0.0);
    }

    /** Component-wise sum of this estimate and {@code other}. */
    public CostEstimate plus(CostEstimate other) {
        Objects.requireNonNull(other, "other");
        return new CostEstimate(
                jobCount + other.jobCount,
                cpuHoursPerJob + other.cpuHoursPerJob,
                expectedWallHours + other.expectedWallHours,
                expectedLocalRuntimeHours + other.expectedLocalRuntimeHours,
                estimatedRemoteCostUsd + other.estimatedRemoteCostUsd);
    }

    /** Component-wise sum of all estimates in {@code estimates}; empty → {@link #zero()}. */
    public static CostEstimate aggregate(Collection<CostEstimate> estimates) {
        Objects.requireNonNull(estimates, "estimates");
        CostEstimate total = zero();
        for (CostEstimate estimate : estimates) {
            total = total.plus(estimate);
        }
        return total;
    }
}
