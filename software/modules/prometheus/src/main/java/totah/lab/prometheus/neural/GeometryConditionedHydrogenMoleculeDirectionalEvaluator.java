package totah.lab.prometheus.neural;

import java.util.List;

import totah.lab.prometheus.numerics.DirectionalSecondOrderJet;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;

/** Fused primal and two-direction derivative graph for the frozen shared H2 representation. */
public final class GeometryConditionedHydrogenMoleculeDirectionalEvaluator {
    public static final int TOTAL_SWCT_DIRECTION=0,BARE_NUCLEAR_DIRECTION=1;
    private static final double[] RADIAL_SCALES={.35,.70,1.10},R12_SCALES={.75,-.45,.25},BIASES={-.35,.10,.50};
    public Evaluation evaluate(double radius,ParameterVector parameters,QuantumCoordinates coordinates,double[] zVelocities){
        if(parameters.values().size()!=20||coordinates.particles().size()!=2||zVelocities.length!=2)throw new IllegalArgumentException("frozen H2 dimensions required");
        int d=6,directions=2;var one=coordinates.particles().get(0);var two=coordinates.particles().get(1);
        double[] values={one.xBohr(),one.yBohr(),one.zBohr(),two.xBohr(),two.yBohr(),two.zBohr()};DirectionalSecondOrderJet[] xyz=new DirectionalSecondOrderJet[d];
        for(int i=0;i<d;i++)xyz[i]=DirectionalSecondOrderJet.spatialVariable(values[i],d,i,i%3==2?zVelocities[i/3]:0,0);
        DirectionalSecondOrderJet r=DirectionalSecondOrderJet.variable(radius,d,1,1),half=r.multiply(.5);
        var r1a=distance(xyz[0],xyz[1],xyz[2].add(half));var r1b=distance(xyz[0],xyz[1],xyz[2].subtract(half));
        var r2a=distance(xyz[3],xyz[4],xyz[5].add(half));var r2b=distance(xyz[3],xyz[4],xyz[5].subtract(half));
        var u=distance(xyz[0].subtract(xyz[3]),xyz[1].subtract(xyz[4]),xyz[2].subtract(xyz[5]));
        DirectionalSecondOrderJet[] features=features(r,d,directions),local=new DirectionalSecondOrderJet[5];
        for(int output=0;output<5;output++){local[output]=DirectionalSecondOrderJet.constant(0,d,directions);for(int feature=0;feature<4;feature++)local[output]=local[output].add(features[feature].multiply(parameters.values().get(output*4+feature)));}
        var l1a=localized(r1a,r1b,local[0]);var l1b=localized(r1b,r1a,local[0]);var l2a=localized(r2a,r2b,local[0]);var l2b=localized(r2b,r2a,local[0]);
        var covalent=l1a.multiply(l2b).add(l1b.multiply(l2a));var radial=tail(r1a).add(tail(r1b)).add(tail(r2a)).add(tail(r2b));var neural=local[1];
        for(int i=0;i<3;i++){var hidden=radial.multiply(RADIAL_SCALES[i]).add(u.multiply(R12_SCALES[i])).add(BIASES[i]).tanh();neural=neural.add(hidden.multiply(local[i+2]));}
        var psi=covalent.multiply(DirectionalSecondOrderJet.constant(1,d,directions).add(u.multiply(.5)).add(u.multiply(u).multiply(neural)));
        return new Evaluation(psi.value(),psi.laplacian(d),new double[]{psi.directionalDerivative(0),psi.directionalDerivative(1)},
                new double[]{psi.laplacianDirectionalDerivative(0,d),psi.laplacianDirectionalDerivative(1,d)});
    }
    private static DirectionalSecondOrderJet[] features(DirectionalSecondOrderJet radius,int d,int directions){var x=radius.add(-.8).multiply(2/(6.0-.8)).add(-1);return new DirectionalSecondOrderJet[]{DirectionalSecondOrderJet.constant(1,d,directions),x,x.multiply(x).multiply(2).add(-1),x.multiply(x).multiply(x).multiply(4).add(x.multiply(-3))};}
    private static DirectionalSecondOrderJet localized(DirectionalSecondOrderJet own,DirectionalSecondOrderJet other,DirectionalSecondOrderJet response){return own.add(other).multiply(-1).add(tail(other).multiply(response)).exp();}
    private static DirectionalSecondOrderJet tail(DirectionalSecondOrderJet value){return value.multiply(value).divide(value.add(1));}
    private static DirectionalSecondOrderJet distance(DirectionalSecondOrderJet x,DirectionalSecondOrderJet y,DirectionalSecondOrderJet z){return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt();}
    public record Evaluation(double value,double laplacian,double[] valueDirectionalDerivatives,double[] laplacianDirectionalDerivatives){public Evaluation{valueDirectionalDerivatives=valueDirectionalDerivatives.clone();laplacianDirectionalDerivatives=laplacianDirectionalDerivatives.clone();}}
}
