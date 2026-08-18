package totah.lab.prometheus.neural.ferminet.runtime;

/** Value, Cartesian gradient, and summed Cartesian Laplacian for one scalar. */
final class FermiNetSpatialJet {
    private final double value;
    private final double[] gradient;
    private final double laplacian;

    private FermiNetSpatialJet(double value,double[] gradient,double laplacian){
        this.value=value;this.gradient=gradient;this.laplacian=laplacian;
    }
    static FermiNetSpatialJet constant(double value,int dimensions){return new FermiNetSpatialJet(value,new double[dimensions],0);}
    static FermiNetSpatialJet variable(double value,int dimensions,int axis){double[] g=new double[dimensions];g[axis]=1;return new FermiNetSpatialJet(value,g,0);}
    double value(){return value;}double gradient(int axis){return gradient[axis];}double[] gradient(){return gradient.clone();}double laplacian(){return laplacian;}int dimensions(){return gradient.length;}
    FermiNetSpatialJet add(FermiNetSpatialJet other){require(other);double[] g=new double[gradient.length];for(int i=0;i<g.length;i++)g[i]=gradient[i]+other.gradient[i];return new FermiNetSpatialJet(value+other.value,g,laplacian+other.laplacian);}
    FermiNetSpatialJet add(double scalar){return new FermiNetSpatialJet(value+scalar,gradient.clone(),laplacian);}
    FermiNetSpatialJet subtract(FermiNetSpatialJet other){return add(other.multiply(-1));}
    FermiNetSpatialJet multiply(double scalar){double[] g=gradient.clone();for(int i=0;i<g.length;i++)g[i]*=scalar;return new FermiNetSpatialJet(value*scalar,g,laplacian*scalar);}
    FermiNetSpatialJet multiply(FermiNetSpatialJet other){require(other);double[] g=new double[gradient.length];double dot=0;for(int i=0;i<g.length;i++){g[i]=gradient[i]*other.value+value*other.gradient[i];dot+=gradient[i]*other.gradient[i];}return new FermiNetSpatialJet(value*other.value,g,laplacian*other.value+2*dot+value*other.laplacian);}
    FermiNetSpatialJet reciprocal(){return unary(1/value,-1/(value*value),2/(value*value*value));}
    FermiNetSpatialJet divide(FermiNetSpatialJet other){return multiply(other.reciprocal());}
    FermiNetSpatialJet exp(){double result=Math.exp(value);return unary(result,result,result);}
    FermiNetSpatialJet sqrt(){double result=Math.sqrt(value);return unary(result,.5/result,-.25/(value*result));}
    FermiNetSpatialJet tanh(){double result=Math.tanh(value),first=1-result*result;return unary(result,first,-2*result*first);}
    static FermiNetSpatialJet affine(FermiNetSpatialJet[] input,double[] weights,
            int offset,double bias){int dimensions=input[0].dimensions();double value=bias,laplacian=0;double[] gradient=new double[dimensions];for(int j=0;j<input.length;j++){double weight=weights[offset+j];value+=weight*input[j].value;laplacian+=weight*input[j].laplacian;for(int axis=0;axis<dimensions;axis++)gradient[axis]+=weight*input[j].gradient[axis];}return new FermiNetSpatialJet(value,gradient,laplacian);}
    private FermiNetSpatialJet unary(double result,double first,double second){double[] g=new double[gradient.length];double norm=0;for(int i=0;i<g.length;i++){g[i]=first*gradient[i];norm+=gradient[i]*gradient[i];}return new FermiNetSpatialJet(result,g,second*norm+first*laplacian);}
    private void require(FermiNetSpatialJet other){if(gradient.length!=other.gradient.length)throw new IllegalArgumentException("spatial dimensions disagree");}
}
