package totah.lab.prometheus.potential.delta.basis;

import java.util.List;
import java.util.Objects;
import totah.lab.prometheus.potential.QuantumCoordinates;
import totah.lab.prometheus.potential.delta.environment.LocalEnvironment;
import totah.lab.prometheus.potential.delta.environment.SpeciesChannel;

/** Centered angular Legendre basis with graph-distance-one/two neighbors. */
public final class ThreeBodyBasis implements ManyBodyBasis {
    public record Neighbor(SpeciesChannel type,int graphDistance) implements Comparable<Neighbor>{public Neighbor{Objects.requireNonNull(type);if(graphDistance<1||graphDistance>2)throw new IllegalArgumentException("neighbor distance must be one or two");}@Override public int compareTo(Neighbor o){int c=type.compareTo(o.type);return c!=0?c:Integer.compare(graphDistance,o.graphDistance);}}
    public record Channel(SpeciesChannel center,Neighbor first,Neighbor second){public Channel{Objects.requireNonNull(center);Objects.requireNonNull(first);Objects.requireNonNull(second);if(first.compareTo(second)>0)throw new IllegalArgumentException("neighbors must be sorted");}}
    private final LocalEnvironment environment;private final List<Channel> channels;
    public ThreeBodyBasis(LocalEnvironment environment,List<Channel> channels){this.environment=Objects.requireNonNull(environment);this.channels=List.copyOf(channels);}
    @Override public int dimension(){return channels.size()*2;}
    @Override public BasisEvaluation evaluate(QuantumCoordinates coordinates){
        if(coordinates.atomCount()!=environment.atomCount())throw new IllegalArgumentException("atom count differs from topology"); int count=environment.atomCount();double[] values=new double[dimension()];double[][][] gradient=new double[dimension()][count][3];
        for(int center=0;center<count;center++)for(int j=0;j<count;j++){int dj=environment.graphDistance(center,j);if(j==center||dj>2)continue;for(int k=j+1;k<count;k++){int dk=environment.graphDistance(center,k);if(k==center||dk>2)continue;
            Neighbor a=new Neighbor(environment.type(j),dj),b=new Neighbor(environment.type(k),dk);Channel key=new Channel(environment.type(center),a.compareTo(b)<=0?a:b,a.compareTo(b)<=0?b:a);int channel=channels.indexOf(key);if(channel<0)continue;
            double[] u=vector(coordinates,center,j),v=vector(coordinates,center,k);double ru=norm(u),rv=norm(v);if(ru==0||rv==0||ru>=SmoothCutoff.CUTOFF||rv>=SmoothCutoff.CUTOFF)continue;double cos=dot(u,v)/(ru*rv);cos=Math.max(-1,Math.min(1,cos));double su=SmoothCutoff.value(ru),sv=SmoothCutoff.value(rv),dup=SmoothCutoff.derivative(ru),dvp=SmoothCutoff.derivative(rv);
            double[] dcdu=new double[3],dcdv=new double[3];for(int q=0;q<3;q++){dcdu[q]=v[q]/(ru*rv)-cos*u[q]/(ru*ru);dcdv[q]=u[q]/(ru*rv)-cos*v[q]/(rv*rv);}
            for(int l=1;l<=2;l++){double p=l==1?cos:(3*cos*cos-1)/2,pp=l==1?1:3*cos;int f=channel*2+l-1;values[f]+=su*sv*p;for(int q=0;q<3;q++){double gu=dup*u[q]/ru*sv*p+su*sv*pp*dcdu[q],gv=dvp*v[q]/rv*su*p+su*sv*pp*dcdv[q];gradient[f][j][q]+=gu;gradient[f][k][q]+=gv;gradient[f][center][q]-=gu+gv;}}
        }}return new BasisEvaluation(values,gradient);
    }
    private static double[] vector(QuantumCoordinates c,int from,int to){return new double[]{c.coordinate(to,0)-c.coordinate(from,0),c.coordinate(to,1)-c.coordinate(from,1),c.coordinate(to,2)-c.coordinate(from,2)};}private static double norm(double[]v){return Math.sqrt(dot(v,v));}private static double dot(double[]a,double[]b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}
