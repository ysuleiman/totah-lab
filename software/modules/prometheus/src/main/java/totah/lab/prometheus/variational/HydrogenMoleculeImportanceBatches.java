package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Lazily emits deterministic H2 importance points in bounded batches. */
public final class HydrogenMoleculeImportanceBatches {
    public static final int MAXIMUM_BATCH_SIZE = 512;
    private static final int[] PRIMES = {2,3,5,7,11,13,17,19,23,29,31,37};
    private final int count, skip, batchSize;
    private final double bondLengthBohr, exponent;
    private final String provenanceHash;

    public HydrogenMoleculeImportanceBatches(int count, double bondLengthBohr, double exponent,
            int skip, int batchSize) {
        if (count < 100 || bondLengthBohr <= 0 || exponent <= 0 || skip < 1) {
            throw new IllegalArgumentException("invalid sampling");
        }
        if (batchSize < 1 || batchSize > MAXIMUM_BATCH_SIZE) {
            throw new IllegalArgumentException("batchSize must be between 1 and 512");
        }
        this.count = count; this.bondLengthBohr = bondLengthBohr; this.exponent = exponent;
        this.skip = skip; this.batchSize = batchSize;
        this.provenanceHash = CanonicalHashing.sha256Hex(
                "h2-two-center-halton-v1|"+count+"|R="+bondLengthBohr+"|zeta="+exponent+"|skip="+skip);
    }

    public int count() { return count; }
    public int batchSize() { return batchSize; }
    public String provenanceHash() { return provenanceHash; }

    public void forEachBatch(Consumer<List<CollocationPointSet.WeightedPoint>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        for (int start = 0; start < count; start += batchSize) {
            int end = Math.min(count, start + batchSize);
            List<CollocationPointSet.WeightedPoint> batch = new ArrayList<>(end - start);
            for (int sample = start; sample < end; sample++) batch.add(point(sample));
            consumer.accept(List.copyOf(batch));
        }
    }

    CollocationPointSet materialize() {
        List<CollocationPointSet.WeightedPoint> points = new ArrayList<>(count);
        forEachBatch(points::addAll);
        return new CollocationPointSet(points, provenanceHash);
    }

    private CollocationPointSet.WeightedPoint point(int sample) {
        double[] q = new double[12]; int index = sample + skip;
        for (int i = 0; i < q.length; i++) q[i] = halton(index, PRIMES[i]);
        double half = bondLengthBohr / 2;
        double[] first = electron(q, 0, exponent, half), second = electron(q, 6, exponent, half);
        double density = mixtureDensity(first, exponent, half) * mixtureDensity(second, exponent, half);
        var coordinates = new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0, first[0], first[1], first[2], SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1, second[0], second[1], second[2], SpinProjection.BETA)));
        return new CollocationPointSet.WeightedPoint(coordinates, 1 / (count * density));
    }

    private static double[] electron(double[] q,int offset,double exponent,double half) {
        double radius=-Math.log(q[offset]*q[offset+1]*q[offset+2])/(2*exponent);
        double cosine=2*q[offset+3]-1,sine=Math.sqrt(1-cosine*cosine),phi=2*Math.PI*q[offset+4];
        double center=q[offset+5]<0.5?-half:half;
        return new double[]{radius*sine*Math.cos(phi),radius*sine*Math.sin(phi),center+radius*cosine};
    }
    private static double mixtureDensity(double[] xyz,double exponent,double half) {
        double a=density(radius(xyz[0],xyz[1],xyz[2]+half),exponent);
        double b=density(radius(xyz[0],xyz[1],xyz[2]-half),exponent); return 0.5*(a+b);
    }
    private static double density(double radius,double exponent) {
        return exponent*exponent*exponent/Math.PI*Math.exp(-2*exponent*radius);
    }
    private static double radius(double x,double y,double z) { return Math.sqrt(x*x+y*y+z*z); }
    private static double halton(int index,int base) {
        double result=0,fraction=1.0/base; int remaining=index;
        while(remaining>0) { result+=fraction*(remaining%base);remaining/=base;fraction/=base; }
        return result;
    }
}
