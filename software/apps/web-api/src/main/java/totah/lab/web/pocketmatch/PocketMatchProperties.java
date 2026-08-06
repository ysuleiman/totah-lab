package totah.lab.web.pocketmatch;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the experimental PocketMatch retrieval channel and
 * its offline benchmark.
 *
 * <p>The retrieval channel is disabled by default and must stay
 * disabled until benchmark results are reviewed; when disabled the
 * production {@code /similar} behavior is byte-identical to the
 * behavior without this component.</p>
 *
 * <pre>
 * pocket.search.pocket-match.enabled=false
 * pocket.search.pocket-match.limit=1000
 * pocket.search.pocket-match.ranking=QUERY_COVERAGE
 * pocket.search.pocket-match.distance-tolerance=0.50
 * pocket.search.pocket-match.signature-store=...
 * pocket.search.pocket-match.benchmark-enabled=false
 * </pre>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the {@code totah.lab.athena.pocket.pocketmatch} package
 * documentation for the full citation and provenance.</p>
 */
@Component
@ConfigurationProperties(prefix = "pocket.search.pocket-match")
public class PocketMatchProperties {

    /**
     * Enables the experimental candidate-union retrieval channel.
     */
    private boolean enabled = false;

    /**
     * Maximum number of PocketMatch candidates unioned into Stage 1.
     */
    private int limit = 1000;

    /**
     * Ranking metric for the candidate channel.
     */
    private PocketMatchRanking ranking = PocketMatchRanking.QUERY_COVERAGE;

    /**
     * Distance-list matching tolerance in angstroms.
     */
    private double distanceTolerance = 0.50;

    /**
     * Binary signature store produced by the benchmark runner.
     */
    private String signatureStore = "workspace/output/pocketmatch/"
            + "pocket-match-signatures.bin";

    /**
     * Runs the offline PocketMatch benchmark instead of serving HTTP.
     */
    private boolean benchmarkEnabled = false;

    /**
     * Benchmark query pocket (METTL7A pocket 32 by default).
     */
    private long benchmarkQueryPocketId = 32;

    /**
     * Markdown report destination for the benchmark runner.
     */
    private String benchmarkReport = "analysis/pocketmatch/"
            + "POCKETMATCH_EVALUATION.md";

    /**
     * Maximum pockets loaded into the benchmark; non-positive means
     * the full corpus.
     */
    private int benchmarkMaximumPockets = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public PocketMatchRanking getRanking() {
        return ranking;
    }

    public void setRanking(PocketMatchRanking ranking) {
        this.ranking = ranking;
    }

    public double getDistanceTolerance() {
        return distanceTolerance;
    }

    public void setDistanceTolerance(double distanceTolerance) {
        this.distanceTolerance = distanceTolerance;
    }

    public String getSignatureStore() {
        return signatureStore;
    }

    public void setSignatureStore(String signatureStore) {
        this.signatureStore = signatureStore;
    }

    public boolean isBenchmarkEnabled() {
        return benchmarkEnabled;
    }

    public void setBenchmarkEnabled(boolean benchmarkEnabled) {
        this.benchmarkEnabled = benchmarkEnabled;
    }

    public long getBenchmarkQueryPocketId() {
        return benchmarkQueryPocketId;
    }

    public void setBenchmarkQueryPocketId(long benchmarkQueryPocketId) {
        this.benchmarkQueryPocketId = benchmarkQueryPocketId;
    }

    public String getBenchmarkReport() {
        return benchmarkReport;
    }

    public void setBenchmarkReport(String benchmarkReport) {
        this.benchmarkReport = benchmarkReport;
    }

    public int getBenchmarkMaximumPockets() {
        return benchmarkMaximumPockets;
    }

    public void setBenchmarkMaximumPockets(int benchmarkMaximumPockets) {
        this.benchmarkMaximumPockets = benchmarkMaximumPockets;
    }
}
