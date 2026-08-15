package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.numerics.FixedPreconditioners;
import totah.lab.prometheus.numerics.LinearOperator;

class SolverConvergencePcgTest {
    @Test void trueResidualAndCompensatedModesSolveSpdSystem(){
        double[][] matrix={{4,1},{1,3}};LinearOperator operator=new LinearOperator(){public int dimension(){return 2;}public double[] apply(double[] x){return new double[]{4*x[0]+x[1],x[0]+3*x[1]};}};
        for(var mode:SolverConvergencePcg.Mode.values()){
            var result=new SolverConvergencePcg().solve(operator,FixedPreconditioners.identity(2),new double[]{1,2},mode);
            assertThat(result.converged()).isTrue();assertThat(result.trueResidual()).isLessThanOrEqualTo(1e-12*Math.sqrt(5));
            assertThat(result.solution()[0]).isCloseTo(1.0/11.0,org.assertj.core.data.Offset.offset(1e-12));assertThat(result.solution()[1]).isCloseTo(7.0/11.0,org.assertj.core.data.Offset.offset(1e-12));
        }
    }

    @Test void compensatedDotImprovesCancellation(){
        double ordinary=SolverConvergencePcg.dot(new double[]{1e16,1,-1e16},new double[]{1,1,1},false);
        double compensated=SolverConvergencePcg.dot(new double[]{1e16,1,-1e16},new double[]{1,1,1},true);
        assertThat(ordinary).isZero();assertThat(compensated).isEqualTo(1);
    }
}
