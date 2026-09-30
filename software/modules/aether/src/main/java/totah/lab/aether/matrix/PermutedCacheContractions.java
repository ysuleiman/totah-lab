package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;

/**
 * Exact AO permutation view. Only O(N^2) density/output matrices are permuted;
 * the frozen contractions scan the original checksum-verified ERI pages.
 * Package-private until the independent M17.1 numerical gate is complete.
 */
final class PermutedCacheContractions implements AutoCloseable {
    static final String PROTOCOL = "aether-AO-permutation-cache-17.1-1;bijective-single-function-identities;"
            + "canonical-contiguous-scan;no-tensor-copy;SCREENING_ONLY";
    private final QuantumSystem system;
    private final EriDiskCache cache;
    private final List<ContractedGaussian> requested;
    private final int[] requestedToCanonical, canonicalToRequested;
    private final String provenance;
    private final SemiDirectJk jk;
    private final CachedCoulomb coulomb;

    /** Ambiguous duplicates and any non-bijection decline reuse, never guess. */
    static Optional<int[]> bijection(List<ContractedGaussian> canonical, List<ContractedGaussian> requested) {
        if (canonical.size() != requested.size() || canonical.isEmpty()) return Optional.empty();
        var positions = new HashMap<String, Integer>();
        for (int i = 0; i < canonical.size(); i++) {
            String identity = EriDiskCache.identity(List.of(canonical.get(i)));
            if (positions.put(identity, i) != null) return Optional.empty();
        }
        int[] permutation = new int[requested.size()];
        for (int i = 0; i < requested.size(); i++) {
            Integer position = positions.remove(EriDiskCache.identity(List.of(requested.get(i))));
            if (position == null) return Optional.empty();
            permutation[i] = position;
        }
        return positions.isEmpty() ? Optional.of(permutation) : Optional.empty();
    }

    PermutedCacheContractions(QuantumSystem system, EriDiskCache cache,
                             List<ContractedGaussian> requested, boolean jOnly) throws IOException {
        this.system = system;
        this.cache = cache;
        this.requested = List.copyOf(requested);
        requestedToCanonical = bijection(cache.basis(), this.requested)
                .orElseThrow(() -> new IllegalArgumentException("Unproven or ambiguous AO bijection"));
        canonicalToRequested = new int[requested.size()];
        var permutationHash = ContentHash.accumulator().line(PROTOCOL);
        for (int i = 0; i < requested.size(); i++) {
            canonicalToRequested[requestedToCanonical[i]] = i;
            permutationHash.line(Integer.toString(requestedToCanonical[i]));
        }
        provenance = PROTOCOL + "\ncanonical=" + cache.identity() + "\npayload=" + cache.payloadSha256()
                + "\nrequested=" + EriDiskCache.identity(this.requested)
                + "\npermutation=" + permutationHash.finish() + "\nbijection=VERIFIED";
        jk = jOnly ? null : new SemiDirectJk(system, cache);
        coulomb = jOnly ? new CachedCoulomb(system, cache) : null;
    }

    String provenance() { return provenance; }

    long canonicalOffset(int i, int j, int k, int l) {
        return EriDiskCache.offset(requestedToCanonical[i], requestedToCanonical[j],
                requestedToCanonical[k], requestedToCanonical[l]);
    }

    private DensityMatrix canonicalDensity(DensityMatrix input) {
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(system), input.systemHash(), "permuted system");
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.basisGeometryHash(requested),
                input.basisGeometryHash(), "permuted requested AO basis");
        var values = new ArrayList<Double>(Math.multiplyExact(input.size(), input.size()));
        for (int i : canonicalToRequested) for (int j : canonicalToRequested) values.add(input.get(i, j));
        return DensityMatrix.fromRowMajor(system, cache.basis(), values);
    }

    private ExecutionMatrix requestedMatrix(ExecutionMatrix original, DensityMatrix input, ExecutionMatrix.Kind kind) {
        return new ExecutionMatrix(kind, requested, input.systemHash(), input.densityHash(), PROTOCOL,
                provenance + "\n" + original.receiptHash(),
                (i, j) -> original.get(requestedToCanonical[i], requestedToCanonical[j]));
    }

    synchronized SemiDirectJk.Result calculateJk(DensityMatrix input) throws IOException {
        if (jk == null) throw new IllegalStateException("J-only view cannot compute exchange");
        long start = System.nanoTime();
        var result = jk.calculate(canonicalDensity(input));
        var j = requestedMatrix(result.coulomb(), input, ExecutionMatrix.Kind.COULOMB);
        var k = requestedMatrix(result.exchange(), input, ExecutionMatrix.Kind.EXCHANGE);
        String hash = ContentHash.sha256(provenance + "\n" + j.receiptHash() + "\n" + k.receiptHash());
        return new SemiDirectJk.Result(j, k, hash, result.scan(), result.jAccumulations(),
                result.kAccumulations(), System.nanoTime() - start);
    }

    synchronized CachedCoulomb.Result calculateCoulomb(DensityMatrix input) throws IOException {
        if (coulomb == null) throw new IllegalStateException("Not a J-only view");
        long start = System.nanoTime();
        var result = coulomb.calculate(canonicalDensity(input));
        return new CachedCoulomb.Result(requestedMatrix(result.coulomb(), input, ExecutionMatrix.Kind.COULOMB),
                result.scan(), result.accumulations(), System.nanoTime() - start);
    }

    @Override public void close() throws IOException {
        if (jk != null) jk.close();
        if (coulomb != null) coulomb.close();
    }
}
