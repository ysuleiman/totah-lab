package totah.lab.prometheus.numerics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

final class TrueResidualPreconditionedConjugateGradientSolverTest {
    @Test void solvesSpdSystemAgainstIndependentlyRecomputedResidual(){
        LinearOperator a=new LinearOperator(){public int dimension(){return 2;}public double[] apply(double[] x){return new double[]{4*x[0]+x[1],x[0]+3*x[1]};}};
        var result=new TrueResidualPreconditionedConjugateGradientSolver().solve(a,FixedPreconditioners.diagonal(new double[]{4,3}),new double[]{1,2},new TrueResidualPreconditionedConjugateGradientSolver.Configuration(20,1e-12,1e-14));
        assertTrue(result.converged());assertEquals(1.0/11,result.solution()[0],1e-12);assertEquals(7.0/11,result.solution()[1],1e-12);assertTrue(result.relativeTrueResidual()<1e-12);
    }
}
