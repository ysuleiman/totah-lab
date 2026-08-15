package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.CubicChebyshevGeometryEncoder;

class ExactFiniteObjectiveOptimizerTest {
    @Test void exactOptimizerIsDeterministicAndCountsOneGraphPerSample() {
        double[] radii={.8,1,1.2};double[] references={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281};
        var optimizer=new ExactFiniteObjectiveOptimizer(new ExactFiniteObjectiveOptimizer.Configuration(2,1,1,.05,1e-3,0,.10,100),radii,references);
        var first=optimizer.optimize(CubicChebyshevGeometryEncoder.coldStart());
        var second=optimizer.optimize(CubicChebyshevGeometryEncoder.coldStart());
        assertThat(first.parameters()).isEqualTo(second.parameters());
        assertThat(first.history()).isEqualTo(second.history());
        assertThat(first.stateEvaluations()).isEqualTo(3L*5*100);
        assertThat(first.localEnergyEvaluations()).isEqualTo(first.stateEvaluations());
    }
}
