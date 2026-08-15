package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.variational.ParameterVector;

/** Minimal continuous H2 geometry encoder with five outputs and cubic Chebyshev features. */
public final class CubicChebyshevGeometryEncoder {
    public static final int OUTPUTS=5,FEATURES=4,PARAMETERS=OUTPUTS*FEATURES;
    private final ParameterVector parameters;
    public CubicChebyshevGeometryEncoder(ParameterVector parameters){
        if(parameters.values().size()!=PARAMETERS)throw new IllegalArgumentException("twenty encoder parameters required");
        this.parameters=parameters;
    }
    public static ParameterVector coldStart(){List<Double> values=new ArrayList<>(java.util.Collections.nCopies(PARAMETERS,0.0));
        values.set(0,1.0);return new ParameterVector(values);}
    public ParameterVector parameters(){return parameters;}
    public ParameterVector encode(double radius){double[] feature=features(radius);List<Double> output=new ArrayList<>(OUTPUTS);
        for(int i=0;i<OUTPUTS;i++){double value=0;for(int j=0;j<FEATURES;j++)value+=parameters.values().get(i*FEATURES+j)*feature[j];output.add(value);}
        return new ParameterVector(output);}
    public static double[] features(double radius){if(!Double.isFinite(radius)||radius<=0)throw new IllegalArgumentException("positive finite radius required");
        double x=2*(radius-.8)/(6.0-.8)-1;return new double[]{1,x,2*x*x-1,4*x*x*x-3*x};}
}
