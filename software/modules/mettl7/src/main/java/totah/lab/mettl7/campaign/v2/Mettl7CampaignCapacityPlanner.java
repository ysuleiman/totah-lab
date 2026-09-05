package totah.lab.mettl7.campaign.v2;

import java.util.Objects;

/** Pure planning math; it neither launches Vina nor reads partial results. */
public final class Mettl7CampaignCapacityPlanner {
    private Mettl7CampaignCapacityPlanner() {}

    public static CapacityEstimate estimate(
            Mettl7CartesianLedgerGenerator.LedgerPlan ledger,
            int logicalCpuCount,
            int cpuPerJob,
            double smokeWallSecondsPerRun) {
        Objects.requireNonNull(ledger, "ledger");
        if (!ledger.ready()) throw new IllegalStateException("Final ledger is not ready");
        if (logicalCpuCount < 1 || cpuPerJob < 1 || cpuPerJob > logicalCpuCount) {
            throw new IllegalArgumentException("Invalid CPU allocation");
        }
        if (!Double.isFinite(smokeWallSecondsPerRun) || smokeWallSecondsPerRun <= 0.0) {
            throw new IllegalArgumentException("A positive measured smoke runtime is required");
        }
        int concurrency = Math.max(1, logicalCpuCount / cpuPerJob);
        int runs = ledger.rows().size();
        double cpuHours = runs * smokeWallSecondsPerRun * cpuPerJob / 3600.0;
        double wallHours = Math.ceil((double) runs / concurrency)
                * smokeWallSecondsPerRun / 3600.0;
        return new CapacityEstimate(runs, logicalCpuCount, cpuPerJob, concurrency,
                smokeWallSecondsPerRun, cpuHours, wallHours);
    }

    public record CapacityEstimate(int expectedRuns, int logicalCpuCount, int cpuPerJob,
                                   int configuredConcurrency, double smokeWallSecondsPerRun,
                                   double estimatedCpuHours, double estimatedWallHours) {}
}
