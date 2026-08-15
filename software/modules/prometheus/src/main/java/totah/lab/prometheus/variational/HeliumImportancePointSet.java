package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Deterministic Halton importance samples from two normalized exponential one-electron densities. */
public final class HeliumImportancePointSet {
    private static final int[] PRIMES={2,3,5,7,11,13,17,19,23,29};
    private HeliumImportancePointSet() { }

    public static CollocationPointSet create(int count,double samplingExponent,int skip) {
        if(count<100||samplingExponent<=0||skip<1) throw new IllegalArgumentException("invalid sampling request");
        List<CollocationPointSet.WeightedPoint> points=new ArrayList<>(count);
        for(int sample=0;sample<count;sample++) {
            int index=sample+skip; double[] q=new double[10];
            for(int dimension=0;dimension<q.length;dimension++) q[dimension]=halton(index,PRIMES[dimension]);
            double[] first=electron(q,0,samplingExponent),second=electron(q,5,samplingExponent);
            double density=orbitalDensity(first,samplingExponent)*orbitalDensity(second,samplingExponent);
            var coordinates=new QuantumCoordinates(List.of(
                    new QuantumCoordinates.ParticleCoordinate(0,first[0],first[1],first[2],SpinProjection.ALPHA),
                    new QuantumCoordinates.ParticleCoordinate(1,second[0],second[1],second[2],SpinProjection.BETA)));
            points.add(new CollocationPointSet.WeightedPoint(coordinates,1.0/(count*density)));
        }
        String provenance=CanonicalHashing.sha256Hex("helium-halton-importance-v1|"+count+"|"
                +samplingExponent+"|"+skip);
        return new CollocationPointSet(points,provenance);
    }

    private static double[] electron(double[] q,int offset,double exponent) {
        double radius=-Math.log(q[offset]*q[offset+1]*q[offset+2])/(2.0*exponent);
        double cosine=2.0*q[offset+3]-1.0,sine=Math.sqrt(1.0-cosine*cosine);
        double phi=2.0*Math.PI*q[offset+4];
        return new double[]{radius*sine*Math.cos(phi),radius*sine*Math.sin(phi),radius*cosine};
    }
    private static double orbitalDensity(double[] coordinate,double exponent) {
        double radius=Math.sqrt(coordinate[0]*coordinate[0]+coordinate[1]*coordinate[1]+coordinate[2]*coordinate[2]);
        return exponent*exponent*exponent/Math.PI*Math.exp(-2.0*exponent*radius);
    }
    private static double halton(int index,int base) {
        double result=0.0,fraction=1.0/base; int remaining=index;
        while(remaining>0) { result+=fraction*(remaining%base); remaining/=base; fraction/=base; }
        return result;
    }
}
