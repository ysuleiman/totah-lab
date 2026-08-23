package totah.lab.prometheus.potential.delta.basis;

import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.delta.environment.LocalEnvironment;
import totah.lab.prometheus.potential.delta.environment.SpeciesChannel;

/** Frozen topology-aware radial Chebyshev invariant basis. */
public final class TwoBodyBasis implements ManyBodyBasis {
    public enum TopologyClass { ONE_TWO, ONE_THREE, ONE_FOUR, NONBONDED }
    public record Channel(SpeciesChannel first, SpeciesChannel second, TopologyClass topology) {
        public Channel { Objects.requireNonNull(first); Objects.requireNonNull(second); Objects.requireNonNull(topology); if(first.compareTo(second)>0) throw new IllegalArgumentException("unordered channel must be sorted"); }
    }
    private final LocalEnvironment environment; private final List<Channel> channels;
    public TwoBodyBasis(LocalEnvironment environment,List<Channel> channels){this.environment=Objects.requireNonNull(environment);this.channels=List.copyOf(channels);}
    @Override public int dimension(){return channels.size()*4;}
    @Override public BasisEvaluation evaluate(QuantumCoordinates coordinates){
        if(coordinates.atomCount()!=environment.atomCount())throw new IllegalArgumentException("atom count differs from topology");
        double[] values=new double[dimension()]; double[][][] gradient=new double[dimension()][environment.atomCount()][3];
        for(int i=0;i<environment.atomCount();i++)for(int j=i+1;j<environment.atomCount();j++){
            int ci=environment.type(i).compareTo(environment.type(j))<=0?i:j, cj=ci==i?j:i;
            Channel key=new Channel(environment.type(ci),environment.type(cj),topology(environment.graphDistance(i,j))); int channel=channels.indexOf(key); if(channel<0)continue;
            double dx=coordinates.coordinate(i,0)-coordinates.coordinate(j,0),dy=coordinates.coordinate(i,1)-coordinates.coordinate(j,1),dz=coordinates.coordinate(i,2)-coordinates.coordinate(j,2); double r=Math.sqrt(dx*dx+dy*dy+dz*dz); if(r==0||r>=SmoothCutoff.CUTOFF)continue;
            double x=2*r/SmoothCutoff.CUTOFF-1,s=SmoothCutoff.value(r),sp=SmoothCutoff.derivative(r); double[] t={1,x,2*x*x-1,4*x*x*x-3*x}; double[] tp={0,1,4*x,12*x*x-3};
            for(int n=0;n<4;n++){int f=channel*4+n;values[f]+=s*t[n];double radial=sp*t[n]+s*tp[n]*2/SmoothCutoff.CUTOFF;double[] d={dx/r*radial,dy/r*radial,dz/r*radial};for(int a=0;a<3;a++){gradient[f][i][a]+=d[a];gradient[f][j][a]-=d[a];}}
        } return new BasisEvaluation(values,gradient);
    }
    private static TopologyClass topology(int distance){return switch(distance){case 1->TopologyClass.ONE_TWO;case 2->TopologyClass.ONE_THREE;case 3->TopologyClass.ONE_FOUR;default->TopologyClass.NONBONDED;};}
}
