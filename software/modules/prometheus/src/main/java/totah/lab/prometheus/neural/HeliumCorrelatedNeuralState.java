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

/** Symmetric helium singlet state with explicit electron-electron correlation. */
public final class HeliumCorrelatedNeuralState implements DifferentiableQuantumState {
    private static final double[] S_SCALES={0.45,0.85,1.25};
    private static final double[] U_SCALES={0.90,-0.55,0.30};
    private static final double[] BIASES={-0.4,0.15,0.55};
    private final ParameterVector parameters;

    /** Parameters are tail response followed by neural output bias and three neural output weights. */
    public HeliumCorrelatedNeuralState(ParameterVector parameters) {
        if(parameters.values().size()!=5) throw new IllegalArgumentException("five parameters are required");
        this.parameters=parameters;
    }
    @Override public String representationId() { return "helium-symmetric-r12-neural-v1"; }
    @Override public ParameterVector parameters() { return parameters; }
    @Override public HeliumCorrelatedNeuralState withParameters(ParameterVector replacement) {
        return new HeliumCorrelatedNeuralState(replacement);
    }

    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        if(coordinates.particles().size()!=2) throw new IllegalArgumentException("two electrons are required");
        int dimensions=6; SecondOrderJet[] xyz=new SecondOrderJet[dimensions];
        var first=coordinates.particles().get(0); var second=coordinates.particles().get(1);
        double[] values={first.xBohr(),first.yBohr(),first.zBohr(),second.xBohr(),second.yBohr(),second.zBohr()};
        for(int i=0;i<dimensions;i++) xyz[i]=SecondOrderJet.variable(values[i],dimensions,i);
        SecondOrderJet r1=radius(xyz[0],xyz[1],xyz[2]);
        SecondOrderJet r2=radius(xyz[3],xyz[4],xyz[5]);
        SecondOrderJet dx=xyz[0].subtract(xyz[3]),dy=xyz[1].subtract(xyz[4]),dz=xyz[2].subtract(xyz[5]);
        SecondOrderJet u=radius(dx,dy,dz),s=r1.add(r2);
        SecondOrderJet cuspSafeRadialInput=radialTail(r1).add(radialTail(r2));
        double tail=parameters.values().get(0);
        SecondOrderJet exponent=s.multiply(-2.0).add(radialTail(r1).add(radialTail(r2)).multiply(tail));
        List<SecondOrderJet> features=new ArrayList<>();
        SecondOrderJet neural=SecondOrderJet.constant(parameters.values().get(1),dimensions);
        for(int i=0;i<3;i++) {
            SecondOrderJet feature=cuspSafeRadialInput.multiply(S_SCALES[i])
                    .add(u.multiply(U_SCALES[i])).add(BIASES[i]).tanh();
            features.add(feature); neural=neural.add(feature.multiply(parameters.values().get(i+2)));
        }
        SecondOrderJet correlation=SecondOrderJet.constant(1.0,dimensions).add(u.multiply(0.5))
                .add(u.multiply(u).multiply(neural));
        SecondOrderJet psi=exponent.exp().multiply(correlation);
        List<StateGradient.Vector3> gradients=List.of(vector(psi,0),vector(psi,3));
        double envelope=exponent.exp().value(),uSquared=u.value()*u.value();
        List<QuantumAmplitude> parameterGradient=new ArrayList<>();
        parameterGradient.add(QuantumAmplitude.real(psi.value()*(radialTail(r1).value()+radialTail(r2).value())));
        parameterGradient.add(QuantumAmplitude.real(envelope*uSquared));
        for(var feature:features) parameterGradient.add(QuantumAmplitude.real(envelope*uSquared*feature.value()));
        String identity=CanonicalHashing.sha256Hex(representationId()+"|"+coordinates+"|"+parameters.values());
        return new DifferentiableStateEvaluation(QuantumAmplitude.real(psi.value()),new StateGradient(gradients),
                new StateLaplacian(QuantumAmplitude.real(psi.laplacian())),
                new ParameterGradient(parameterGradient),identity);
    }

    private static SecondOrderJet radius(SecondOrderJet x,SecondOrderJet y,SecondOrderJet z) {
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt();
    }
    private static SecondOrderJet radialTail(SecondOrderJet radius) {
        return radius.multiply(radius).divide(radius.add(1.0));
    }
    private static StateGradient.Vector3 vector(SecondOrderJet psi,int offset) {
        return new StateGradient.Vector3(QuantumAmplitude.real(psi.gradient(offset)),
                QuantumAmplitude.real(psi.gradient(offset+1)),QuantumAmplitude.real(psi.gradient(offset+2)));
    }
}
