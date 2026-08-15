package totah.lab.prometheus.numerics;

import java.util.Arrays;

/** Spatial second-order jet with a small fixed set of directional tangents. */
public final class DirectionalSecondOrderJet {
    private final SecondOrderJet primal;
    private final SecondOrderJet[] tangents;

    private DirectionalSecondOrderJet(SecondOrderJet primal,SecondOrderJet[] tangents){this.primal=primal;this.tangents=tangents;}
    public static DirectionalSecondOrderJet constant(double value,int spatialDimensions,int directions){return new DirectionalSecondOrderJet(SecondOrderJet.constant(value,spatialDimensions),zeros(spatialDimensions,directions));}
    public static DirectionalSecondOrderJet variable(double value,int spatialDimensions,double... directionalDerivatives){SecondOrderJet[] tangents=zeros(spatialDimensions,directionalDerivatives.length);for(int i=0;i<tangents.length;i++)tangents[i]=SecondOrderJet.constant(directionalDerivatives[i],spatialDimensions);return new DirectionalSecondOrderJet(SecondOrderJet.constant(value,spatialDimensions),tangents);}
    public static DirectionalSecondOrderJet spatialVariable(double value,int spatialDimensions,int axis,double... directionalDerivatives){SecondOrderJet[] tangents=zeros(spatialDimensions,directionalDerivatives.length);for(int i=0;i<tangents.length;i++)tangents[i]=SecondOrderJet.constant(directionalDerivatives[i],spatialDimensions);return new DirectionalSecondOrderJet(SecondOrderJet.variable(value,spatialDimensions,axis),tangents);}
    public double value(){return primal.value();}
    public double gradient(int axis){return primal.gradient(axis);}
    public double laplacian(int axes){return primal.laplacian(axes);}
    public double directionalDerivative(int direction){return tangents[direction].value();}
    public double laplacianDirectionalDerivative(int direction,int axes){return tangents[direction].laplacian(axes);}
    public DirectionalSecondOrderJet add(DirectionalSecondOrderJet other){require(other);SecondOrderJet[] t=new SecondOrderJet[tangents.length];for(int i=0;i<t.length;i++)t[i]=tangents[i].add(other.tangents[i]);return new DirectionalSecondOrderJet(primal.add(other.primal),t);}
    public DirectionalSecondOrderJet add(double scalar){return new DirectionalSecondOrderJet(primal.add(scalar),tangents.clone());}
    public DirectionalSecondOrderJet subtract(DirectionalSecondOrderJet other){require(other);SecondOrderJet[] t=new SecondOrderJet[tangents.length];for(int i=0;i<t.length;i++)t[i]=tangents[i].subtract(other.tangents[i]);return new DirectionalSecondOrderJet(primal.subtract(other.primal),t);}
    public DirectionalSecondOrderJet multiply(DirectionalSecondOrderJet other){require(other);SecondOrderJet[] t=new SecondOrderJet[tangents.length];for(int i=0;i<t.length;i++)t[i]=tangents[i].multiply(other.primal).add(primal.multiply(other.tangents[i]));return new DirectionalSecondOrderJet(primal.multiply(other.primal),t);}
    public DirectionalSecondOrderJet multiply(double scalar){return new DirectionalSecondOrderJet(primal.multiply(scalar),Arrays.stream(tangents).map(x->x.multiply(scalar)).toArray(SecondOrderJet[]::new));}
    public DirectionalSecondOrderJet reciprocal(){SecondOrderJet reciprocal=primal.reciprocal();return unary(reciprocal,reciprocal.multiply(reciprocal).multiply(-1));}
    public DirectionalSecondOrderJet divide(DirectionalSecondOrderJet other){return multiply(other.reciprocal());}
    public DirectionalSecondOrderJet exp(){SecondOrderJet result=primal.exp();return unary(result,result);}
    public DirectionalSecondOrderJet sqrt(){SecondOrderJet result=primal.sqrt();return unary(result,result.reciprocal().multiply(.5));}
    public DirectionalSecondOrderJet tanh(){SecondOrderJet result=primal.tanh();return unary(result,SecondOrderJet.constant(1,dimension()).subtract(result.multiply(result)));}
    private DirectionalSecondOrderJet unary(SecondOrderJet result,SecondOrderJet derivative){return new DirectionalSecondOrderJet(result,Arrays.stream(tangents).map(x->derivative.multiply(x)).toArray(SecondOrderJet[]::new));}
    private int dimension(){int d=0;while(true){try{primal.gradient(d++);}catch(ArrayIndexOutOfBoundsException exception){return d-1;}}}
    private void require(DirectionalSecondOrderJet other){if(tangents.length!=other.tangents.length)throw new IllegalArgumentException("direction counts disagree");}
    private static SecondOrderJet[] zeros(int dimensions,int directions){SecondOrderJet[] result=new SecondOrderJet[directions];Arrays.setAll(result,i->SecondOrderJet.constant(0,dimensions));return result;}
}
