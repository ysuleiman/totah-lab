package totah.lab.aether.matrix;

import java.util.List;
import java.util.Objects;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.integral.ElectronRepulsionIntegral;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ElectronRepulsionReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/**
 * Immutable real Cartesian s/p ERIs in chemists' order (ij|kl), hartree. Stores only the lower triangle
 * of unordered AO pairs: pair(i,j)=max(i,j)(max(i,j)+1)/2+min(i,j).
 * All eight equivalent permutations retrieve the same stored binary64 value.
 */
public final class ElectronRepulsionTensor {
    public static final String IMPLEMENTATION = "aether-electron-repulsion-1";
    public static final String PROTOCOL = "s-only;bohr;hartree;chemist-order=(ij|kl);operator=1/r12;"
            + "normalized-primitives;normalized-contractions;min-norm2=1e-14;StrictMath;binary64;"
            + "ordered-primitive-quartets;Neumaier-sum;log-scale-fallback;no-screening;no-cache;"
            + "pair-triangle-then-pair-pair-triangle;"
            + "F0=taylor-to-0.5,positive-series-to-36,asymptotic-from-36;series-rel=1e-16;max-terms=256";
    private final QuantumSystem system;
    private final List<ContractedGaussian> functions;
    private final double[] values;
    private final ElectronRepulsionReceipt receipt;
    private final PerformanceCounters performanceCounters;

    ElectronRepulsionTensor(QuantumSystem system, List<ContractedGaussian> functions) {
        long started = System.nanoTime();
        this.system = system;
        this.functions = List.copyOf(functions);
        int n = functions.size();
        long pairs = triangular(n);
        // Guard before the second triangular product, which could overflow for enormous n.
        if (pairs > 65535) throw new IllegalArgumentException("ERI packed storage exceeds Java array capacity");
        long unique = triangular(pairs);
        if (unique > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("ERI packed storage exceeds Java array capacity");
        values = new double[(int) unique];
        long primitiveQuartets = 0;
        long contractedQuartets = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                int ij = pair(i, j);
                for (int k = 0; k <= i; k++) {
                    for (int l = 0; l <= k; l++) {
                        int kl = pair(k, l);
                        if (kl > ij) break;
                        var a = this.functions.get(i);
                        var b = this.functions.get(j);
                        var c = this.functions.get(k);
                        var d = this.functions.get(l);
                        values[pair(ij, kl)] = ElectronRepulsionIntegral.between(a, b, c, d);
                        long terms = Math.multiplyExact((long) a.terms().size(), b.terms().size());
                        terms = Math.multiplyExact(terms, c.terms().size());
                        terms = Math.multiplyExact(terms, d.terms().size());
                        primitiveQuartets = Math.addExact(primitiveQuartets, terms);
                        contractedQuartets++;
                    }
                }
            }
        }
        String basisHash = IntegralMatrixData.basisGeometryHash(this.functions);
        String systemHash = IntegralMatrixData.systemHash(system);
        String protocol = IntegralMatrixData.protocol(PROTOCOL, functions);
        String reason = "Electron-repulsion integrals only; arbitrary inputs are not reference-validated";
        var identity = IntegralMatrixData.identityFromResultHash(basisHash, IMPLEMENTATION, protocol, reason,
                "\naether-eri-system-v1\n" + systemHash, canonicalResultHash());
        receipt = new ElectronRepulsionReceipt(IMPLEMENTATION, protocol, basisHash, systemHash, n, unique,
                identity.calculationHash(), identity.resultHash(), ScientificStatus.SCREENING_ONLY,
                reason, identity.receiptHash());
        // Timing is observational only and never enters canonical identity or receipt bytes.
        performanceCounters = new PerformanceCounters(primitiveQuartets, contractedQuartets, unique, 0,
                System.nanoTime() - started);
    }

    private static long triangular(long n) { return n * (n + 1) / 2; }

    /** Shared production path, also exercised by the test-only receipt-memory probe. */
    String canonicalResultHash() {
        Iterable<String> lines=()->java.util.stream.Stream.concat(
                java.util.stream.Stream.of("aether-ERI-hartree-packed-v1",Integer.toString(size()),Long.toString(values.length)),
                java.util.Arrays.stream(values).mapToObj(ContentHash::number)).iterator();
        return ContentHash.sha256Lines(lines);
    }

    private static int pair(int i, int j) {
        return (int) (triangular(StrictMath.max(i, j)) + StrictMath.min(i, j));
    }

    public int size() { return functions.size(); }
    public int uniqueQuartetCount() { return values.length; }

    public double get(int i, int j, int k, int l) {
        Objects.checkIndex(i, size());
        Objects.checkIndex(j, size());
        Objects.checkIndex(k, size());
        Objects.checkIndex(l, size());
        return values[pair(pair(i, j), pair(k, l))];
    }

    public List<ContractedGaussian> functions() { return functions; }
    public QuantumSystem system() { return system; }
    public ElectronRepulsionReceipt receipt() { return receipt; }
    public PerformanceCounters performanceCounters() { return performanceCounters; }

    /** Evaluation counters, not lookup counters; no cache exists and cacheHits is zero. */
    public record PerformanceCounters(long primitiveQuartetsEvaluated, long contractedQuartetsEvaluated,
                                      long symmetryUniqueQuartets, long cacheHits, long elapsedNanos) {}
}
