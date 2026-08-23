package totah.lab.prometheus.potential.delta.basis;

import java.util.ArrayList;
import java.util.List;
import totah.lab.prometheus.potential.QuantumCoordinates;

/** Preregistered local torsion and coupled-angle features; never a general 4-body expansion. */
public final class FourBodyBasis implements ManyBodyBasis {
    public enum Kind { TORSION_FOURIER, ANGLE_PAIR }
    public record Motif(Kind kind,int first,int second,int third,int fourth) {}
    private final List<Motif> motifs;
    public FourBodyBasis(List<Motif> motifs){this.motifs=List.copyOf(motifs);}
    @Override public int dimension(){return motifs.stream().mapToInt(m->m.kind()==Kind.TORSION_FOURIER?6:4).sum();}
    @Override public BasisEvaluation evaluate(QuantumCoordinates coordinates){int variables=coordinates.atomCount()*3;double[] values=new double[dimension()];double[][][] gradients=new double[dimension()][coordinates.atomCount()][3];int offset=0;
        for(Motif motif:motifs){D[][] p=new D[4][3];int[] atoms={motif.first(),motif.second(),motif.third(),motif.fourth()};for(int a=0;a<4;a++)for(int q=0;q<3;q++)p[a][q]=D.variable(coordinates.coordinate(atoms[a],q),atoms[a]*3+q,variables);
            List<D> generated=motif.kind()==Kind.TORSION_FOURIER?torsion(p):anglePair(p);for(D feature:generated){values[offset]=feature.value;for(int atom=0;atom<coordinates.atomCount();atom++)for(int q=0;q<3;q++)gradients[offset][atom][q]=feature.gradient[atom*3+q];offset++;}}
        return new BasisEvaluation(values,gradients);
    }
    private static List<D> torsion(D[][]p){D[] b0=sub(p[1],p[0]),b1=sub(p[2],p[1]),b2=sub(p[3],p[2]);D[] n1=cross(b0,b1),n2=cross(b1,b2);D[] b1u=scale(b1,inv(norm(b1)));D[] m1=cross(n1,b1u);D phi=atan2(dot(m1,n2),dot(n1,n2));List<D>out=new ArrayList<>();for(int k=1;k<=3;k++){out.add(sin(scale(phi,k)));out.add(cos(scale(phi,k)));}return out;}
    private static List<D> anglePair(D[][]p){D left=angleCos(p[0],p[1],p[2]),right=angleCos(p[0],p[1],p[3]);List<D>out=new ArrayList<>();for(int l=1;l<=2;l++)for(int m=1;m<=2;m++)out.add(mul(legendre(left,l),legendre(right,m)));return out;}
    private static D angleCos(D[]a,D[]center,D[]b){D[]u=sub(a,center),v=sub(b,center);return mul(dot(u,v),inv(mul(norm(u),norm(v))));}private static D legendre(D x,int l){return l==1?x:scale(sub(scale(mul(x,x),3),D.constant(1,x.gradient.length)),.5);}
    private static D[] sub(D[]a,D[]b){return new D[]{sub(a[0],b[0]),sub(a[1],b[1]),sub(a[2],b[2])};}private static D[] cross(D[]a,D[]b){return new D[]{sub(mul(a[1],b[2]),mul(a[2],b[1])),sub(mul(a[2],b[0]),mul(a[0],b[2])),sub(mul(a[0],b[1]),mul(a[1],b[0]))};}private static D dot(D[]a,D[]b){return add(add(mul(a[0],b[0]),mul(a[1],b[1])),mul(a[2],b[2]));}private static D norm(D[]a){return sqrt(dot(a,a));}private static D[] scale(D[]a,D b){return new D[]{mul(a[0],b),mul(a[1],b),mul(a[2],b)};}
    private static D add(D a,D b){double[]g=a.gradient.clone();for(int i=0;i<g.length;i++)g[i]+=b.gradient[i];return new D(a.value+b.value,g);}private static D sub(D a,D b){double[]g=a.gradient.clone();for(int i=0;i<g.length;i++)g[i]-=b.gradient[i];return new D(a.value-b.value,g);}private static D mul(D a,D b){double[]g=new double[a.gradient.length];for(int i=0;i<g.length;i++)g[i]=a.gradient[i]*b.value+b.gradient[i]*a.value;return new D(a.value*b.value,g);}private static D scale(D a,double b){double[]g=a.gradient.clone();for(int i=0;i<g.length;i++)g[i]*=b;return new D(a.value*b,g);}private static D inv(D a){double[]g=a.gradient.clone();for(int i=0;i<g.length;i++)g[i]/=-a.value*a.value;return new D(1/a.value,g);}private static D sqrt(D a){double v=Math.sqrt(a.value);double[]g=a.gradient.clone();for(int i=0;i<g.length;i++)g[i]/=2*v;return new D(v,g);}private static D sin(D a){double[]g=a.gradient.clone();double c=Math.cos(a.value);for(int i=0;i<g.length;i++)g[i]*=c;return new D(Math.sin(a.value),g);}private static D cos(D a){double[]g=a.gradient.clone();double s=-Math.sin(a.value);for(int i=0;i<g.length;i++)g[i]*=s;return new D(Math.cos(a.value),g);}private static D atan2(D y,D x){double d=x.value*x.value+y.value*y.value;double[]g=new double[x.gradient.length];for(int i=0;i<g.length;i++)g[i]=(x.value*y.gradient[i]-y.value*x.gradient[i])/d;return new D(Math.atan2(y.value,x.value),g);}
    private record D(double value,double[]gradient){static D variable(double value,int at,int n){double[]g=new double[n];g[at]=1;return new D(value,g);}static D constant(double value,int n){return new D(value,new double[n]);}}
}
