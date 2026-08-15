package totah.lab.prometheus.neural;

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

/** Hartree-like product state exp[-zeta(r1+r2)] used as the helium uncorrelated baseline. */
public final class HeliumUncorrelatedState implements DifferentiableQuantumState {
    private final double exponent;
    public HeliumUncorrelatedState(double exponent) {
        if(!Double.isFinite(exponent)||exponent<=0) throw new IllegalArgumentException("positive exponent required");
        this.exponent=exponent;
    }
    @Override public String representationId() { return "helium-uncorrelated-product-v1"; }
    @Override public ParameterVector parameters() { return new ParameterVector(List.of(exponent)); }
    @Override public HeliumUncorrelatedState withParameters(ParameterVector parameters) {
        return new HeliumUncorrelatedState(parameters.values().getFirst());
    }
    @Override public DifferentiableStateEvaluation evaluateWithDerivatives(QuantumCoordinates coordinates) {
        if(coordinates.particles().size()!=2) throw new IllegalArgumentException("two electrons required");
        int dimensions=6; SecondOrderJet[] xyz=new SecondOrderJet[dimensions];
        var a=coordinates.particles().get(0); var b=coordinates.particles().get(1);
        double[] values={a.xBohr(),a.yBohr(),a.zBohr(),b.xBohr(),b.yBohr(),b.zBohr()};
        for(int i=0;i<dimensions;i++) xyz[i]=SecondOrderJet.variable(values[i],dimensions,i);
        SecondOrderJet r1=radius(xyz[0],xyz[1],xyz[2]),r2=radius(xyz[3],xyz[4],xyz[5]);
        SecondOrderJet psi=r1.add(r2).multiply(-exponent).exp();
        var gradients=List.of(vector(psi,0),vector(psi,3));
        double derivative=-(r1.value()+r2.value())*psi.value();
        return new DifferentiableStateEvaluation(QuantumAmplitude.real(psi.value()),new StateGradient(gradients),
                new StateLaplacian(QuantumAmplitude.real(psi.laplacian())),
                new ParameterGradient(List.of(QuantumAmplitude.real(derivative))),
                CanonicalHashing.sha256Hex(representationId()+"|"+coordinates+"|"+exponent));
    }
    private static SecondOrderJet radius(SecondOrderJet x,SecondOrderJet y,SecondOrderJet z) {
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt();
    }
    private static StateGradient.Vector3 vector(SecondOrderJet psi,int offset) {
        return new StateGradient.Vector3(QuantumAmplitude.real(psi.gradient(offset)),
                QuantumAmplitude.real(psi.gradient(offset+1)),QuantumAmplitude.real(psi.gradient(offset+2)));
    }
}
