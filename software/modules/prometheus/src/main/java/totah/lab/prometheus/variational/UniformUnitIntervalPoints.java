package totah.lab.prometheus.variational;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.identity.CanonicalHashing;

/** Deterministic trapezoidal quadrature/collocation points on [0,1]. */
public final class UniformUnitIntervalPoints {
    private UniformUnitIntervalPoints() { }

    public static CollocationPointSet create(int count) {
        if(count<3) throw new IllegalArgumentException("at least three points are required");
        double spacing=1.0/(count-1); List<CollocationPointSet.WeightedPoint> points=new ArrayList<>();
        for(int i=0;i<count;i++) {
            double x=i*spacing, weight=(i==0||i==count-1)?spacing/2.0:spacing;
            QuantumCoordinates coordinates=new QuantumCoordinates(List.of(
                    new QuantumCoordinates.ParticleCoordinate(0,x,0,0,SpinProjection.UNSPECIFIED)));
            points.add(new CollocationPointSet.WeightedPoint(coordinates,weight));
        }
        return new CollocationPointSet(points,CanonicalHashing.sha256Hex("uniform-unit-interval|"+count));
    }
}
