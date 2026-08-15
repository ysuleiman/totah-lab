package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.numerics.SecondOrderJet;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.GeometryDifferentiableQuantumState;
import totah.lab.prometheus.variational.GeometryStateEvaluation;
import totah.lab.prometheus.variational.ParameterGradient;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumAmplitude;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.StateGradient;
import totah.lab.prometheus.variational.StateLaplacian;

/** Shared-parameter H2 state whose proven cusp-safe local parameters are encoded from R. */
public final class GeometryConditionedHydrogenMoleculeState implements GeometryDifferentiableQuantumState {
    private static final double[] RADIAL_SCALES={0.35,0.70,1.10};
    private static final double[] R12_SCALES={0.75,-0.45,0.25};
    private static final double[] BIASES={-0.35,0.10,0.50};
    private final double radius;
    private final CubicChebyshevGeometryEncoder encoder;
    public GeometryConditionedHydrogenMoleculeState(double radius,ParameterVector sharedParameters){
        if(!Double.isFinite(radius)||radius<=0)throw new IllegalArgumentException("positive R required");
        this.radius=radius;this.encoder=new CubicChebyshevGeometryEncoder(sharedParameters);}
    @Override public double geometryCoordinateBohr(){return radius;}
    @Override public String representationId(){return "h2-shared-cubic-chebyshev-cusp-safe-v1";}
    @Override public ParameterVector parameters(){return encoder.parameters();}
    @Override public GeometryConditionedHydrogenMoleculeState withParameters(ParameterVector replacement){return new GeometryConditionedHydrogenMoleculeState(radius,replacement);}
    @Override public GeometryConditionedHydrogenMoleculeState atGeometry(double geometryCoordinateBohr){return new GeometryConditionedHydrogenMoleculeState(geometryCoordinateBohr,parameters());}
    @Override public GeometryStateEvaluation evaluateWithGeometryDerivatives(QuantumCoordinates coordinates){
        if(coordinates.particles().size()!=2)throw new IllegalArgumentException("two electrons required");
        int dimensions=7;SecondOrderJet[] xyz=new SecondOrderJet[6];var one=coordinates.particles().get(0);
        var two=coordinates.particles().get(1);double[] values={one.xBohr(),one.yBohr(),one.zBohr(),two.xBohr(),two.yBohr(),two.zBohr()};
        for(int i=0;i<6;i++)xyz[i]=SecondOrderJet.variable(values[i],dimensions,i);
        SecondOrderJet r=SecondOrderJet.variable(radius,dimensions,6),half=r.multiply(.5);
        SecondOrderJet r1a=distance(xyz[0],xyz[1],xyz[2].add(half));
        SecondOrderJet r1b=distance(xyz[0],xyz[1],xyz[2].subtract(half));
        SecondOrderJet r2a=distance(xyz[3],xyz[4],xyz[5].add(half));
        SecondOrderJet r2b=distance(xyz[3],xyz[4],xyz[5].subtract(half));
        SecondOrderJet u=distance(xyz[0].subtract(xyz[3]),xyz[1].subtract(xyz[4]),xyz[2].subtract(xyz[5]));
        SecondOrderJet[] geometryFeatures=geometryFeatures(r);SecondOrderJet[] local=new SecondOrderJet[5];
        for(int output=0;output<5;output++){local[output]=SecondOrderJet.constant(0,dimensions);
            for(int feature=0;feature<4;feature++)local[output]=local[output].add(geometryFeatures[feature]
                    .multiply(parameters().values().get(output*4+feature)));}
        SecondOrderJet l1a=localized(r1a,r1b,local[0]),l1b=localized(r1b,r1a,local[0]);
        SecondOrderJet l2a=localized(r2a,r2b,local[0]),l2b=localized(r2b,r2a,local[0]);
        SecondOrderJet covalent=l1a.multiply(l2b).add(l1b.multiply(l2a));
        SecondOrderJet radial=tail(r1a).add(tail(r1b)).add(tail(r2a)).add(tail(r2b));
        SecondOrderJet neural=local[1];List<SecondOrderJet> hidden=new ArrayList<>();
        for(int i=0;i<3;i++){SecondOrderJet feature=radial.multiply(RADIAL_SCALES[i]).add(u.multiply(R12_SCALES[i]))
                .add(BIASES[i]).tanh();hidden.add(feature);neural=neural.add(feature.multiply(local[i+2]));}
        SecondOrderJet correlation=SecondOrderJet.constant(1,dimensions).add(u.multiply(.5)).add(u.multiply(u).multiply(neural));
        SecondOrderJet psi=covalent.multiply(correlation);
        double localizationDerivative=(l1a.value()*tail(r1b).value()*l2b.value()+l1a.value()*l2b.value()*tail(r2a).value()
                +l1b.value()*tail(r1a).value()*l2a.value()+l1b.value()*l2a.value()*tail(r2b).value())*correlation.value();
        double u2=u.value()*u.value(),base=covalent.value();double[] localDerivatives={localizationDerivative,base*u2,
                base*u2*hidden.get(0).value(),base*u2*hidden.get(1).value(),base*u2*hidden.get(2).value()};
        List<QuantumAmplitude> sharedDerivatives=new ArrayList<>(20);
        for(int output=0;output<5;output++)for(SecondOrderJet feature:geometryFeatures)
            sharedDerivatives.add(QuantumAmplitude.real(localDerivatives[output]*feature.value()));
        var electronic=new DifferentiableStateEvaluation(QuantumAmplitude.real(psi.value()),
                new StateGradient(List.of(vector(psi,0),vector(psi,3))),
                new StateLaplacian(QuantumAmplitude.real(psi.laplacian(6))),new ParameterGradient(sharedDerivatives),
                CanonicalHashing.sha256Hex(representationId()+"|R="+radius+"|"+coordinates+"|"+parameters().values()));
        if(Math.abs(psi.value())<1e-14)throw new IllegalArgumentException("zero amplitude geometry derivative");
        return new GeometryStateEvaluation(electronic,psi.gradient(6)/psi.value());}
    private static SecondOrderJet[] geometryFeatures(SecondOrderJet radius){SecondOrderJet x=radius.add(-.8).multiply(2/(6.0-.8)).add(-1);
        return new SecondOrderJet[]{SecondOrderJet.constant(1,7),x,x.multiply(x).multiply(2).add(-1),
                x.multiply(x).multiply(x).multiply(4).add(x.multiply(-3))};}
    private static SecondOrderJet localized(SecondOrderJet own,SecondOrderJet other,SecondOrderJet response){return own.add(other)
            .multiply(-1).add(tail(other).multiply(response)).exp();}
    private static SecondOrderJet tail(SecondOrderJet value){return value.multiply(value).divide(value.add(1));}
    private static SecondOrderJet distance(SecondOrderJet x,SecondOrderJet y,SecondOrderJet z){return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt();}
    private static StateGradient.Vector3 vector(SecondOrderJet psi,int offset){return new StateGradient.Vector3(
            QuantumAmplitude.real(psi.gradient(offset)),QuantumAmplitude.real(psi.gradient(offset+1)),QuantumAmplitude.real(psi.gradient(offset+2)));}
}
