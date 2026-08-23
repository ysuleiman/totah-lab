package totah.lab.prometheus.potential.delta.model;

/** Immutable linear conservative-model coefficients. */
public final class DeltaModelParameters {
    private final double[] coefficients;
    public DeltaModelParameters(double[] coefficients){if(coefficients==null)throw new IllegalArgumentException("coefficients required");this.coefficients=coefficients.clone();for(double value:this.coefficients)if(!Double.isFinite(value))throw new IllegalArgumentException("coefficients must be finite");}
    public int size(){return coefficients.length;} public double coefficient(int index){return coefficients[index];} public double[] coefficients(){return coefficients.clone();}
}
