package totah.lab.aether.matrix;

import java.util.List;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AetherDiisAlgebraTest {
    private static RealMatrix m(double[][] values) { return new Array2DRowRealMatrix(values); }
    private static RealMatrix e(double x,double y) { return m(new double[][]{{0,x,y},{-x,0,0},{-y,0,0}}); }
    private static RealMatrix f(double x) { return m(new double[][]{{x,0,0},{0,x,0},{0,0,x}}); }
    @Test void independentOrthogonalErrorsGiveInverseSquaredNormWeights() {
        var c=PulayDiis.coefficients(List.of(e(1,0),e(0,2))).values();
        assertEquals(.8,c.get(0),1e-14);assertEquals(.2,c.get(1),1e-14);
    }
    @Test void oppositeCollinearErrorsHaveExactCancellation() {
        var c=PulayDiis.coefficients(List.of(e(1,0),e(-2,0))).values();
        assertEquals(2.0/3,c.get(0),1e-14);assertEquals(1.0/3,c.get(1),1e-14);
    }
    @Test void extrapolationUsesAnalyticWeightsAndChronologicalHistory() {
        var d=new PulayDiis();assertFalse(d.update(PulayDiis.START_ITERATION-1,f(10),e(1,0)).extrapolated());
        var r=d.update(PulayDiis.START_ITERATION,f(20),e(0,2));assertTrue(r.extrapolated());assertEquals(12,r.fock().getEntry(0,0),1e-13);
        assertEquals(List.of(PulayDiis.START_ITERATION-1,PulayDiis.START_ITERATION),r.historyIterations());
    }
    @Test void commonScalingAndOverallSignLeaveWeightsUnchanged() {
        for(double scale:List.of(1e-200,1e200,-3.0)) {
            var c=PulayDiis.coefficients(List.of(e(scale,0),e(0,2*scale))).values();
            assertEquals(.8,c.get(0),1e-14);assertEquals(.2,c.get(1),1e-14);
        }
    }
    @Test void duplicateAndNearDuplicateErrorsFailClosed() {
        assertThrows(ArithmeticException.class,()->PulayDiis.coefficients(List.of(e(1,0),e(1,0))));
        assertThrows(ArithmeticException.class,()->PulayDiis.coefficients(List.of(e(1,0),e(1,1e-8))));
        var d=new PulayDiis();d.update(PulayDiis.START_ITERATION-1,f(1),e(1,0));var r=d.update(PulayDiis.START_ITERATION,f(2),e(1,0));
        assertFalse(r.extrapolated());assertEquals(List.of(PulayDiis.START_ITERATION),r.historyIterations());
        assertTrue(r.events().stream().anyMatch(x->x.contains("ILL_CONDITIONED")));
    }
    @Test void zeroErrorsExplicitlyFallBack() {
        var d=new PulayDiis();d.update(PulayDiis.START_ITERATION-1,f(1),e(0,0));var r=d.update(PulayDiis.START_ITERATION,f(2),e(0,0));
        assertFalse(r.extrapolated());assertTrue(r.events().stream().anyMatch(x->x.contains("ZERO_ERROR")));
    }
    @Test void catastrophicGrowthClearsHistory() {
        var d=new PulayDiis();d.update(PulayDiis.START_ITERATION-1,f(1),e(1e-9,0));var r=d.update(PulayDiis.START_ITERATION,f(2),e(1,0));
        assertFalse(r.extrapolated());assertEquals(List.of(PulayDiis.START_ITERATION),r.historyIterations());
        assertTrue(r.events().getFirst().contains("CATASTROPHIC"));
    }
    @Test void nonfiniteInputsAndBadDimensionsRejected() {
        assertThrows(ArithmeticException.class,()->new PulayDiis().update(PulayDiis.START_ITERATION-1,f(Double.NaN),e(1,0)));
        assertThrows(ArithmeticException.class,()->new PulayDiis().update(PulayDiis.START_ITERATION-1,f(1),e(Double.POSITIVE_INFINITY,0)));
        assertThrows(IllegalArgumentException.class,()->new PulayDiis().update(PulayDiis.START_ITERATION-1,f(1),m(new double[][]{{1}})));
    }
    @Test void nonfiniteExtrapolationExplicitlyFallsBack() {
        var d=new PulayDiis();d.update(PulayDiis.START_ITERATION-1,f(Double.MAX_VALUE),e(1,0));
        var r=d.update(PulayDiis.START_ITERATION,f(-Double.MAX_VALUE),e(2,0)); // c=(2,-1), overflow
        assertFalse(r.extrapolated());assertTrue(r.events().contains("NONFINITE_EXTRAPOLATION_CLEAR_AND_PLAIN"));
        assertEquals(-Double.MAX_VALUE,r.fock().getEntry(0,0));
    }
    @Test void aoCommutatorMatchesIndependentHandCalculation() {
        var r=PulayDiis.error(m(new double[][]{{2,1},{1,3}}),m(new double[][]{{1,.2},{.2,.5}}),m(new double[][]{{1,.1},{.1,1}}));
        assertEquals(0,r.getEntry(0,0),1e-15);assertEquals(-.65,r.getEntry(0,1),1e-15);
        assertEquals(.65,r.getEntry(1,0),1e-15);assertEquals(0,r.getEntry(1,1),1e-15);
    }
    @Test void fullIndependentHistoryEvictsOldestAtNine() {
        var d=new PulayDiis();PulayDiis.Step last=null;
        for(int k=1;k<=9;k++) {
            var error=new Array2DRowRealMatrix(10,10);error.setEntry(0,k,1);error.setEntry(k,0,-1);
            last=d.update(k+PulayDiis.START_ITERATION-9,new Array2DRowRealMatrix(10,10),error);
        }
        assertTrue(last.extrapolated());assertEquals(java.util.stream.IntStream.rangeClosed(PulayDiis.START_ITERATION-7,PulayDiis.START_ITERATION).boxed().toList(),last.historyIterations());
        assertTrue(last.events().contains("EVICT_OLDEST:"+(PulayDiis.START_ITERATION-8)));
        for(double c:last.coefficients())assertEquals(.125,c,1e-14);
    }
    @Test void warmupIsPlainAndHistoryRemainsBounded() {
        var d=new PulayDiis();
        for(int i=1;i<PulayDiis.START_ITERATION;i++) {
            var step=d.update(i,f(i),e(1.0/i,1.0/(i*i)));
            assertFalse(step.extrapolated());assertEquals(i,step.fock().getEntry(0,0));
            assertTrue(step.historyIterations().size()<=PulayDiis.HISTORY_SIZE);
        }
        assertEquals(32,PulayDiis.START_ITERATION);
    }
    @Test void boundedHistoryAndDeterministicReplay() {
        var a=new PulayDiis();var b=new PulayDiis();
        for(int k=1;k<20;k++) {
            var x=a.update(k+PulayDiis.START_ITERATION-2,f(k),e(1.0/k,1.0/(k*k)));var y=b.update(k+PulayDiis.START_ITERATION-2,f(k),e(1.0/k,1.0/(k*k)));
            assertTrue(x.historyIterations().size()<=8);assertEquals(x.coefficients(),y.coefficients());assertEquals(x.events(),y.events());
            assertEquals(PulayDiis.hash(x.fock()),PulayDiis.hash(y.fock()));
        }
        assertThrows(IllegalArgumentException.class,()->a.update(19+PulayDiis.START_ITERATION-2,f(1),e(1,0)));
    }
}
