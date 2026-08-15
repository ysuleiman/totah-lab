package totah.lab.prometheus.variational;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.identity.CanonicalHashing;

class HydrogenMoleculeNuclearForceEstimatorTest {
    @Test
    void explicitPotentialDerivativeMatchesCentralDifference() {
        var coordinates=coordinates(0.37,-0.21,0.82,-0.44,0.31,-0.63);
        double radius=1.4,step=1e-6;
        double finiteDifference=(new HydrogenMoleculeHamiltonian(radius+step).potential(coordinates)
                -new HydrogenMoleculeHamiltonian(radius-step).potential(coordinates))/(2*step);

        assertThat(new HydrogenMoleculeHamiltonian(radius).potentialDerivativeBondLength(coordinates))
                .isCloseTo(finiteDifference,org.assertj.core.data.Offset.offset(2e-9));
    }

    @Test
    void normalizedForceMatchesFiniteDifferenceOfPrometheusEnergy() {
        double radius=1.4,step=1e-5;
        var batches=new HydrogenMoleculeImportanceBatches(401,radius,1.0,37,71);
        var state=new GeometryGaussianState(radius,0.65,0.12);
        var result=new HydrogenMoleculeNuclearForceEstimator().evaluate(
                state,new HydrogenMoleculeHamiltonian(radius),batches);
        var rayleigh=new HydrogenMoleculeStreamingRayleighEvaluator();
        double plus=rayleigh.evaluate(state.atGeometry(radius+step),
                new HydrogenMoleculeHamiltonian(radius+step),batches).objective();
        double minus=rayleigh.evaluate(state.atGeometry(radius-step),
                new HydrogenMoleculeHamiltonian(radius-step),batches).objective();
        double finiteDifference=(plus-minus)/(2*step);

        assertThat(result.energyDerivativeHartreePerBohr()).isCloseTo(finiteDifference,
                org.assertj.core.data.Offset.offset(2e-8));
        assertThat(result.forceHartreePerBohr()).isEqualTo(-result.energyDerivativeHartreePerBohr());
        assertThat(result.forceHartreePerBohr()).isEqualTo(
                result.hellmannFeynmanForceHartreePerBohr()+result.pulayForceHartreePerBohr());
        assertThat(result.forceUnits()).isEqualTo("hartree/bohr");
        assertThat(result.stateEvaluations()).isEqualTo(401);
        assertThat(result.expectedStateEvaluations()).isEqualTo(401);
        assertThat(result.redundantStateEvaluations()).isZero();
    }

    @Test
    void forceEstimateIsReproducible() {
        var batches=new HydrogenMoleculeImportanceBatches(257,1.4,1.0,41,53);
        var state=new GeometryGaussianState(1.4,0.65,0.12);
        var estimator=new HydrogenMoleculeNuclearForceEstimator();
        var first=estimator.evaluate(state,new HydrogenMoleculeHamiltonian(1.4),batches);
        var second=estimator.evaluate(state,new HydrogenMoleculeHamiltonian(1.4),batches);
        assertThat(first).isEqualTo(second);
    }

    private static QuantumCoordinates coordinates(double... xyz) {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0,xyz[0],xyz[1],xyz[2],SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1,xyz[3],xyz[4],xyz[5],SpinProjection.BETA)));
    }

    private record GeometryGaussianState(double bondLengthBohr,double baseExponent,double response)
            implements GeometryDifferentiableQuantumState {
        @Override public String representationId() { return "test-geometry-gaussian"; }
        @Override public ParameterVector parameters() { return new ParameterVector(List.of()); }
        @Override public GeometryGaussianState withParameters(ParameterVector parameters) {
            if(!parameters.values().isEmpty()) throw new IllegalArgumentException("no parameters");
            return this;
        }
        @Override public double geometryCoordinateBohr() { return bondLengthBohr; }
        @Override public GeometryGaussianState atGeometry(double replacement) {
            return new GeometryGaussianState(replacement,baseExponent,response);
        }
        @Override public GeometryStateEvaluation evaluateWithGeometryDerivatives(QuantumCoordinates coordinates) {
            double exponent=baseExponent;
            double squared=squaredRadius(coordinates),psi=Math.exp(-response*bondLengthBohr-exponent*squared);
            var first=coordinates.particles().get(0);var second=coordinates.particles().get(1);
            var stateEvaluation=new DifferentiableStateEvaluation(QuantumAmplitude.real(psi),
                    new StateGradient(List.of(gradient(first,exponent,psi),gradient(second,exponent,psi))),
                    new StateLaplacian(QuantumAmplitude.real((4*exponent*exponent*squared-12*exponent)*psi)),
                    new ParameterGradient(List.of()),CanonicalHashing.sha256Hex(
                            representationId()+"|"+bondLengthBohr+"|"+coordinates));
            return new GeometryStateEvaluation(stateEvaluation,-response);
        }
        private static StateGradient.Vector3 gradient(QuantumCoordinates.ParticleCoordinate p,double a,double psi) {
            return new StateGradient.Vector3(QuantumAmplitude.real(-2*a*p.xBohr()*psi),
                    QuantumAmplitude.real(-2*a*p.yBohr()*psi),QuantumAmplitude.real(-2*a*p.zBohr()*psi));
        }
        private static double squaredRadius(QuantumCoordinates coordinates) {
            double sum=0;for(var p:coordinates.particles())
                sum+=p.xBohr()*p.xBohr()+p.yBohr()*p.yBohr()+p.zBohr()*p.zBohr();
            return sum;
        }
    }
}
