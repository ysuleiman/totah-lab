package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.CubicChebyshevGeometryEncoder;
import totah.lab.prometheus.variational.ParameterVector;

class DerivativeAwarePesDiagnosticOptimizerTest {
    private static final double[] RADII={.8,1,1.2};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281};

    @Test void objectiveIsDeterministicAndCountsEveryScientificStateOnce() {
        var optimizer=optimizer(100);ParameterVector parameters=CubicChebyshevGeometryEncoder.coldStart();
        var first=optimizer.objectiveEvaluation(parameters);var second=optimizer.objectiveEvaluation(parameters);

        assertThat(first.loss()).isEqualTo(second.loss());
        assertThat(first.force()).isEqualTo(second.force());
        assertThat(first.rawGradient()).containsExactly(second.rawGradient());
        assertThat(first.stateEvaluations()).isEqualTo(5L*100);
        assertThat(first.localEnergyEvaluations()).isEqualTo(first.stateEvaluations());
        assertThat(first.displacedStateEvaluations()).isEqualTo(2L*100);
        assertThat(first.sampleCount()).isEqualTo(first.stateEvaluations());
    }

    @Test void lockedGradientGateRejectsCovarianceRhsAsExactFiniteSampleGradient() {
        var optimizer=optimizer(100);ParameterVector parameters=CubicChebyshevGeometryEncoder.coldStart();
        var analytic=optimizer.objectiveEvaluation(parameters);double step=2e-6;double maximum=0;
        for(int index=0;index<parameters.values().size();index++) {
            ParameterVector plus=perturb(parameters,index,step),minus=perturb(parameters,index,-step);
            double finite=(optimizer.objectiveEvaluation(plus).loss()-optimizer.objectiveEvaluation(minus).loss())/(2*step);
            maximum=Math.max(maximum,Math.abs(finite-analytic.rawGradient()[index]));
        }
        assertThat(maximum).isGreaterThan(3e-5);
    }

    @Test void optimizationIsDeterministicAndBounded() {
        var optimizer=new DerivativeAwarePesDiagnosticOptimizer(
                new DerivativeAwarePesDiagnosticOptimizer.Configuration(2,1,1,.05,1e-3,0,.10,100),RADII,REFERENCES);
        ParameterVector parameters=CubicChebyshevGeometryEncoder.coldStart();var first=optimizer.optimize(parameters);var second=optimizer.optimize(parameters);

        assertThat(first.parameters()).isEqualTo(second.parameters());
        assertThat(first.lossHistory()).isEqualTo(second.lossHistory());
        assertThat(first.stateEvaluations()).isEqualTo(3L*5*100);
        assertThat(first.localEnergyEvaluations()).isEqualTo(first.stateEvaluations());
        assertThat(parameters).isEqualTo(CubicChebyshevGeometryEncoder.coldStart());
    }

    private static DerivativeAwarePesDiagnosticOptimizer optimizer(int count) {
        return new DerivativeAwarePesDiagnosticOptimizer(
                new DerivativeAwarePesDiagnosticOptimizer.Configuration(1,1,1,.05,1e-3,0,.10,count),RADII,REFERENCES);
    }
    private static ParameterVector perturb(ParameterVector source,int index,double delta) {
        List<Double> values=new ArrayList<>(source.values());values.set(index,values.get(index)+delta);return new ParameterVector(values);
    }
}
