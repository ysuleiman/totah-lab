package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.numerics.SecondOrderJet;
import totah.lab.prometheus.variational.DifferentiableQuantumState;
import totah.lab.prometheus.variational.DifferentiableStateEvaluation;
import totah.lab.prometheus.variational.ParameterGradient;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumAmplitude;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.StateGradient;
import totah.lab.prometheus.variational.StateLaplacian;

/** Cusp-safe, covalent, explicitly r12-correlated H2 singlet state at one fixed R. */
public final class HydrogenMoleculeCorrelatedState implements DifferentiableQuantumState {
    private static final double[] RADIAL_SCALES={0.35,0.70,1.10};
    private static final double[] R12_SCALES={0.75,-0.45,0.25};
    private static final double[] BIASES={-0.35,0.10,0.50};
    private final double bondLengthBohr;
    private final ParameterVector parameters;
    private final boolean explicitCorrelation;

    /**
     * Parameters: localization response, neural bias, and three neural output weights.
     * Diagnostic capacity variants may append either one backflow-informed weight (six
     * values total) or three fixed cusp-safe feature weights (eight values total).
     */
    public HydrogenMoleculeCorrelatedState(double bondLengthBohr,ParameterVector parameters) {
        this(bondLengthBohr,parameters,true);
    }
    HydrogenMoleculeCorrelatedState(double bondLengthBohr,ParameterVector parameters,boolean explicitCorrelation) {
        if(!Double.isFinite(bondLengthBohr)||bondLengthBohr<=0) throw new IllegalArgumentException("positive R required");
        int size=parameters.values().size();
        if(size!=5&&size!=6&&size!=8) throw new IllegalArgumentException("five, six, or eight parameters required");
        this.bondLengthBohr=bondLengthBohr; this.parameters=parameters; this.explicitCorrelation=explicitCorrelation;
    }
    public double bondLengthBohr() { return bondLengthBohr; }
    @Override public String representationId() { return switch(parameters.values().size()) {
        case 6 -> "h2-covalent-r12-backflow-feature-diagnostic-v1";
        case 8 -> "h2-covalent-r12-expanded-features-diagnostic-v1";
        default -> "h2-covalent-r12-neural-v1";
    }; }
    @Override public ParameterVector parameters() { return parameters; }
    @Override public HydrogenMoleculeCorrelatedState withParameters(ParameterVector replacement) {
        return new HydrogenMoleculeCorrelatedState(bondLengthBohr,replacement,explicitCorrelation);
    }

    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        if(coordinates.particles().size()!=2) throw new IllegalArgumentException("two electrons required");
        int dimensions=6; SecondOrderJet[] xyz=new SecondOrderJet[dimensions];
        var one=coordinates.particles().get(0); var two=coordinates.particles().get(1);
        double[] values={one.xBohr(),one.yBohr(),one.zBohr(),two.xBohr(),two.yBohr(),two.zBohr()};
        for(int i=0;i<dimensions;i++) xyz[i]=SecondOrderJet.variable(values[i],dimensions,i);
        double half=0.5*bondLengthBohr;
        SecondOrderJet r1a=radius(xyz[0],xyz[1],xyz[2].add(half));
        SecondOrderJet r1b=radius(xyz[0],xyz[1],xyz[2].add(-half));
        SecondOrderJet r2a=radius(xyz[3],xyz[4],xyz[5].add(half));
        SecondOrderJet r2b=radius(xyz[3],xyz[4],xyz[5].add(-half));
        SecondOrderJet u=radius(xyz[0].subtract(xyz[3]),xyz[1].subtract(xyz[4]),xyz[2].subtract(xyz[5]));
        double localization=parameters.values().get(0);
        SecondOrderJet l1a=localized(r1a,r1b,localization),l1b=localized(r1b,r1a,localization);
        SecondOrderJet l2a=localized(r2a,r2b,localization),l2b=localized(r2b,r2a,localization);
        SecondOrderJet covalent=explicitCorrelation?l1a.multiply(l2b).add(l1b.multiply(l2a))
                :l1a.add(l1b).multiply(l2a.add(l2b));
        SecondOrderJet radialInvariant=tail(r1a).add(tail(r1b)).add(tail(r2a)).add(tail(r2b));
        SecondOrderJet neural=SecondOrderJet.constant(parameters.values().get(1),dimensions);
        List<SecondOrderJet> features=new ArrayList<>();
        for(int i=0;i<3;i++) {
            SecondOrderJet feature=radialInvariant.multiply(RADIAL_SCALES[i])
                    .add(u.multiply(R12_SCALES[i])).add(BIASES[i]).tanh();
            features.add(feature); neural=neural.add(feature.multiply(parameters.values().get(i+2)));
        }
        SecondOrderJet electronNuclearContrast=tail(r1a).subtract(tail(r1b))
                .multiply(tail(r2a).subtract(tail(r2b)));
        if(parameters.values().size()==6) {
            SecondOrderJet backflowFeature=u.divide(u.add(1)).multiply(electronNuclearContrast).tanh();
            features.add(backflowFeature);
            neural=neural.add(backflowFeature.multiply(parameters.values().get(5)));
        } else if(parameters.values().size()==8) {
            SecondOrderJet radialSquared=radialInvariant.multiply(radialInvariant).divide(radialInvariant.add(1));
            List<SecondOrderJet> expanded=List.of(
                    radialSquared.multiply(0.20).add(u.multiply(0.55)).add(-0.20).tanh(),
                    electronNuclearContrast.multiply(0.45).add(u.multiply(-0.30)).add(0.15).tanh(),
                    radialInvariant.multiply(0.25).add(electronNuclearContrast.multiply(0.30))
                            .add(u.multiply(0.35)).add(-0.40).tanh());
            for(int i=0;i<expanded.size();i++) {
                SecondOrderJet feature=expanded.get(i);
                features.add(feature);
                neural=neural.add(feature.multiply(parameters.values().get(i+5)));
            }
        }
        SecondOrderJet correlation=explicitCorrelation
                ? SecondOrderJet.constant(1,dimensions).add(u.multiply(0.5)).add(u.multiply(u).multiply(neural))
                : SecondOrderJet.constant(1,dimensions);
        SecondOrderJet psi=covalent.multiply(correlation);
        List<QuantumAmplitude> parameterDerivatives=new ArrayList<>();
        double localizationDerivative;
        if(explicitCorrelation) {
            localizationDerivative=(l1a.value()*tail(r1b).value()*l2b.value()
                    +l1a.value()*l2b.value()*tail(r2a).value()
                    +l1b.value()*tail(r1a).value()*l2a.value()
                    +l1b.value()*l2a.value()*tail(r2b).value())*correlation.value();
        } else {
            double phi1=l1a.value()+l1b.value(),phi2=l2a.value()+l2b.value();
            double dphi1=l1a.value()*tail(r1b).value()+l1b.value()*tail(r1a).value();
            double dphi2=l2a.value()*tail(r2b).value()+l2b.value()*tail(r2a).value();
            localizationDerivative=dphi1*phi2+phi1*dphi2;
        }
        parameterDerivatives.add(QuantumAmplitude.real(localizationDerivative));
        double u2=explicitCorrelation?u.value()*u.value():0,base=covalent.value();
        parameterDerivatives.add(QuantumAmplitude.real(base*u2));
        for(var feature:features) parameterDerivatives.add(QuantumAmplitude.real(base*u2*feature.value()));
        return new DifferentiableStateEvaluation(QuantumAmplitude.real(psi.value()),
                new StateGradient(List.of(vector(psi,0),vector(psi,3))),
                new StateLaplacian(QuantumAmplitude.real(psi.laplacian())),
                new ParameterGradient(parameterDerivatives),CanonicalHashing.sha256Hex(
                        representationId()+"|R="+bondLengthBohr+"|"+coordinates+"|"+parameters.values()));
    }

    private static SecondOrderJet localized(SecondOrderJet own,SecondOrderJet other,double response) {
        return own.add(other).multiply(-1).add(tail(other).multiply(response)).exp();
    }
    private static SecondOrderJet tail(SecondOrderJet r) { return r.multiply(r).divide(r.add(1)); }
    private static SecondOrderJet radius(SecondOrderJet x,SecondOrderJet y,SecondOrderJet z) {
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt();
    }
    private static StateGradient.Vector3 vector(SecondOrderJet psi,int offset) {
        return new StateGradient.Vector3(QuantumAmplitude.real(psi.gradient(offset)),
                QuantumAmplitude.real(psi.gradient(offset+1)),QuantumAmplitude.real(psi.gradient(offset+2)));
    }
}
