package totah.lab.prometheus.neural.ferminet.runtime;

/** Value, Cartesian gradient, and summed Cartesian Laplacian for one scalar. */
final class FermiNetSpatialJet {
    private final double value;
    private final double[] gradient;
    private final double laplacian;
    private final FermiNetSpatialJet directional;

    private FermiNetSpatialJet(double value,double[] gradient,double laplacian){
        this(value,gradient,laplacian,null);
    }
    private FermiNetSpatialJet(double value,double[] gradient,double laplacian,
            FermiNetSpatialJet directional){
        this.value=value;this.gradient=gradient;this.laplacian=laplacian;
        this.directional=directional;
    }
    static FermiNetSpatialJet constant(double value,int dimensions){return new FermiNetSpatialJet(value,new double[dimensions],0);}
    static FermiNetSpatialJet variable(double value,int dimensions,int axis){double[] g=new double[dimensions];g[axis]=1;return new FermiNetSpatialJet(value,g,0);}
    static FermiNetSpatialJet directionalConstant(double value,int dimensions,
            double directionalValue){
        return new FermiNetSpatialJet(value,new double[dimensions],0,
                constant(directionalValue,dimensions));
    }
    static FermiNetSpatialJet directionalVariable(double value,int dimensions,int axis,
            double directionalValue){
        double[] g=new double[dimensions];g[axis]=1;
        return new FermiNetSpatialJet(value,g,0,constant(directionalValue,dimensions));
    }
    double value(){return value;}double gradient(int axis){return gradient[axis];}double[] gradient(){return gradient.clone();}double laplacian(){return laplacian;}int dimensions(){return gradient.length;}
    double directionalValue(){return requireDirectional().value;}
    double directionalLaplacian(){return requireDirectional().laplacian;}
    boolean hasDirectional(){return directional!=null;}
    FermiNetSpatialJet add(FermiNetSpatialJet other){require(other);double[] g=new double[gradient.length];for(int i=0;i<g.length;i++)g[i]=gradient[i]+other.gradient[i];FermiNetSpatialJet d=null;if(directional!=null||other.directional!=null)d=directionalOrZero().add(other.directionalOrZero());return new FermiNetSpatialJet(value+other.value,g,laplacian+other.laplacian,d);}
    FermiNetSpatialJet add(double scalar){return new FermiNetSpatialJet(value+scalar,gradient.clone(),laplacian,directional);}
    FermiNetSpatialJet subtract(FermiNetSpatialJet other){return add(other.multiply(-1));}
    FermiNetSpatialJet multiply(double scalar){double[] g=gradient.clone();for(int i=0;i<g.length;i++)g[i]*=scalar;return new FermiNetSpatialJet(value*scalar,g,laplacian*scalar,directional==null?null:directional.multiply(scalar));}
    FermiNetSpatialJet multiply(FermiNetSpatialJet other){require(other);double[] g=new double[gradient.length];double dot=0;for(int i=0;i<g.length;i++){g[i]=gradient[i]*other.value+value*other.gradient[i];dot+=gradient[i]*other.gradient[i];}FermiNetSpatialJet d=null;if(directional!=null||other.directional!=null)d=directionalOrZero().multiply(other.primal()).add(primal().multiply(other.directionalOrZero()));return new FermiNetSpatialJet(value*other.value,g,laplacian*other.value+2*dot+value*other.laplacian,d);}
    FermiNetSpatialJet reciprocal(){FermiNetSpatialJet derivative=null;if(directional!=null){FermiNetSpatialJet r=primalReciprocal();derivative=r.multiply(r).multiply(-1);}return unary(1/value,-1/(value*value),2/(value*value*value),derivative);}
    FermiNetSpatialJet divide(FermiNetSpatialJet other){return multiply(other.reciprocal());}
    FermiNetSpatialJet exp(){double result=Math.exp(value);FermiNetSpatialJet derivative=directional==null?null:primal().unary(result,result,result);return unary(result,result,result,derivative);}
    FermiNetSpatialJet sqrt(){double result=Math.sqrt(value);FermiNetSpatialJet derivative=directional==null?null:primal().unary(result,.5/result,-.25/(value*result)).reciprocal().multiply(.5);return unary(result,.5/result,-.25/(value*result),derivative);}
    FermiNetSpatialJet tanh(){double result=Math.tanh(value),first=1-result*result;FermiNetSpatialJet derivative=null;if(directional!=null){FermiNetSpatialJet p=primal().unary(result,first,-2*result*first);derivative=constant(1,dimensions()).subtract(p.multiply(p));}return unary(result,first,-2*result*first,derivative);}
    static FermiNetSpatialJet affine(FermiNetSpatialJet[] input,double[] weights,
            int offset,double bias){int dimensions=input[0].dimensions();double value=bias,laplacian=0;double[] gradient=new double[dimensions];boolean directional=false;for(int j=0;j<input.length;j++){double weight=weights[offset+j];value+=weight*input[j].value;laplacian+=weight*input[j].laplacian;directional|=input[j].directional!=null;for(int axis=0;axis<dimensions;axis++)gradient[axis]+=weight*input[j].gradient[axis];}FermiNetSpatialJet d=null;if(directional){d=constant(0,dimensions);for(int j=0;j<input.length;j++)if(input[j].directional!=null)d=d.add(input[j].directional.multiply(weights[offset+j]));}return new FermiNetSpatialJet(value,gradient,laplacian,d);}
    private FermiNetSpatialJet unary(double result,double first,double second){return unary(result,first,second,null);}
    private FermiNetSpatialJet unary(double result,double first,double second,FermiNetSpatialJet derivative){double[] g=new double[gradient.length];double norm=0;for(int i=0;i<g.length;i++){g[i]=first*gradient[i];norm+=gradient[i]*gradient[i];}FermiNetSpatialJet d=directional==null?null:derivative.multiply(directional);return new FermiNetSpatialJet(result,g,second*norm+first*laplacian,d);}
    private FermiNetSpatialJet primal(){return directional==null?this:new FermiNetSpatialJet(value,gradient,laplacian);}
    private FermiNetSpatialJet primalReciprocal(){return primal().unary(1/value,-1/(value*value),2/(value*value*value));}
    private FermiNetSpatialJet directionalOrZero(){return directional==null?constant(0,dimensions()):directional;}
    private FermiNetSpatialJet requireDirectional(){if(directional==null)throw new IllegalStateException("directional tangent is absent");return directional;}
    private void require(FermiNetSpatialJet other){if(gradient.length!=other.gradient.length)throw new IllegalArgumentException("spatial dimensions disagree");}
}
