package totah.lab.prometheus.potential.delta.basis;

import java.util.Arrays;

/** Basis values and exact coordinate gradients [feature][atom][axis]. */
public final class BasisEvaluation {
    private final double[] values;
    private final double[][][] gradients;
    public BasisEvaluation(double[] values,double[][][] gradients){this.values=values.clone();this.gradients=Arrays.stream(gradients).map(atomRows->Arrays.stream(atomRows).map(double[]::clone).toArray(double[][]::new)).toArray(double[][][]::new);}
    public int size(){return values.length;} public double value(int feature){return values[feature];} public double gradient(int feature,int atom,int axis){return gradients[feature][atom][axis];}
    public double[] values(){return values.clone();}
}
