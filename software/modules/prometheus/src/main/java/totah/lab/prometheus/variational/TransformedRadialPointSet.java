package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Midpoint quadrature of r in [0,infinity), using r=s*t/(1-t) and 4*pi*r^2 dr weights. */
public final class TransformedRadialPointSet {
    private TransformedRadialPointSet() { }

    public static CollocationPointSet create(int count,double scaleBohr) {
        if(count<8||!Double.isFinite(scaleBohr)||scaleBohr<=0) throw new IllegalArgumentException("invalid grid");
        List<CollocationPointSet.WeightedPoint> points=new ArrayList<>(count);
        double dt=1.0/count;
        for(int i=0;i<count;i++) {
            double t=(i+0.5)*dt,oneMinus=1.0-t;
            double r=scaleBohr*t/oneMinus,drdt=scaleBohr/(oneMinus*oneMinus);
            double weight=4.0*Math.PI*r*r*drdt*dt;
            var coordinate=new QuantumCoordinates.ParticleCoordinate(0,r,0,0,SpinProjection.UNSPECIFIED);
            points.add(new CollocationPointSet.WeightedPoint(new QuantumCoordinates(List.of(coordinate)),weight));
        }
        String provenance=CanonicalHashing.sha256Hex("transformed-radial-midpoint-v1|"+count+"|"+scaleBohr);
        return new CollocationPointSet(points,provenance);
    }
}
