package totah.lab.prometheus.variational;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.identity.CanonicalHashing;

class NonstationaryParameterResponseAuditTest {
    @Test
    void reportsTheLockedUnregularizedResponseWithoutMovingParameters() {
        var original=new ParameterVector(List.of(0.72));
        var state=new AuditState(1.4,original,false);
        var batches=new HydrogenMoleculeImportanceBatches(100,1.4,1.0,43,31);

        var result=new NonstationaryParameterResponseAudit().evaluate(state,batches);

        assertThat(result.classification()).isEqualTo(
                NonstationaryParameterResponseAudit.Classification.PARAMETER_RESPONSE_DETERMINED);
        assertThat(result.variationalGradient()).hasSize(1).allMatch(Double::isFinite);
        assertThat(result.parameterResponsePerBohr()).hasSize(1).allMatch(Double::isFinite);
        assertThat(result.implicitResponseForceHartreePerBohr()).isPresent();
        assertThat(result.responseRank()).isEqualTo(1);
        assertThat(result.responseDimension()).isEqualTo(1);
        assertThat(result.pivotRatio()).isEqualTo(1.0);
        assertThat(result.parameterStep()).isEqualTo(1e-4);
        assertThat(result.geometryStepBohr()).isEqualTo(1e-3);
        assertThat(result.stateEvaluations()).isEqualTo(500);
        assertThat(result.redundantStateEvaluations()).isZero();
        assertThat(state.parameters()).isEqualTo(original);
    }

    @Test
    void classifiesRankDeficientResponseAsUnderdeterminedWithoutRegularization() {
        var state=new AuditState(1.4,new ParameterVector(List.of(0.2)),true);
        var result=new NonstationaryParameterResponseAudit().evaluate(state,
                new HydrogenMoleculeImportanceBatches(100,1.4,1.0,47,32));

        assertThat(result.classification()).isEqualTo(
                NonstationaryParameterResponseAudit.Classification.PARAMETER_RESPONSE_UNDERDETERMINED);
        assertThat(result.responseRank()).isZero();
        assertThat(result.parameterResponsePerBohr()).isEmpty();
        assertThat(result.implicitResponseForceHartreePerBohr()).isEmpty();
        assertThat(result.evidence()).anyMatch(value->value.contains("unregularized"));
    }

    private record AuditState(double geometryCoordinateBohr,ParameterVector parameters,boolean normalizationOnly)
            implements GeometryDifferentiableQuantumState {
        @Override public String representationId(){return "parameter-response-audit-fixture";}
        @Override public AuditState withParameters(ParameterVector replacement){return new AuditState(geometryCoordinateBohr,replacement,normalizationOnly);}
        @Override public AuditState atGeometry(double geometry){return new AuditState(geometry,parameters,normalizationOnly);}
        @Override public GeometryStateEvaluation evaluateWithGeometryDerivatives(QuantumCoordinates coordinates){
            double squared=0;for(var p:coordinates.particles())squared+=p.xBohr()*p.xBohr()+p.yBohr()*p.yBohr()+p.zBohr()*p.zBohr();
            double parameter=parameters.values().get(0),exponent=normalizationOnly?.7:parameter;
            double psi=Math.exp((normalizationOnly?parameter:0)-exponent*squared);
            double derivative=normalizationOnly?psi:-squared*psi;
            double laplacian=(4*exponent*exponent*squared-12*exponent)*psi;
            var evaluation=new DifferentiableStateEvaluation(QuantumAmplitude.real(psi),
                    new StateGradient(List.of(zero(),zero())),new StateLaplacian(QuantumAmplitude.real(laplacian)),
                    new ParameterGradient(List.of(QuantumAmplitude.real(derivative))),
                    CanonicalHashing.sha256Hex(representationId()+"|"+geometryCoordinateBohr+"|"+parameters+"|"+coordinates));
            return new GeometryStateEvaluation(evaluation,0);
        }
        private static StateGradient.Vector3 zero(){return new StateGradient.Vector3(
                QuantumAmplitude.real(0),QuantumAmplitude.real(0),QuantumAmplitude.real(0));}
    }
}
