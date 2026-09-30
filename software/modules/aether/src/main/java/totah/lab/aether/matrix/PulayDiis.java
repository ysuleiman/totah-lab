package totah.lab.aether.matrix;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import totah.lab.aether.provenance.ContentHash;

/** Bounded serial commutator DIIS. Backend matrices never escape the package. */
final class PulayDiis {
    static final int START_ITERATION = 32, HISTORY_SIZE = 8;
    static final double LINEAR_SOLVE_TOLERANCE = 1e-12;
    static final String PROTOCOL = "AO-error=FPS-SPF;row-major-dot;start=32;history=8;oldest-first;"
            + "common-error-max-scaling;Gram-max-diagonal-scaling;augmented-minus-one;"
            + "CommonsMath3.6.1;SVD-rcond>1e-12;LU-partial-pivot;sum-and-solve-residual<=1e-10;"
            + "reject-and-drop-oldest;no-pseudoinverse;no-regularization;fallback=physical-F;"
            + "growth=maxAbs(e)>1e6*max(previous,1e-12):clear-and-plain;nonfinite-extrapolation:clear-and-plain";
    private final List<History> history = new ArrayList<>();
    private double previousError = Double.NaN;
    private int previousIteration;
    record History(int iteration, RealMatrix fock, RealMatrix error, String fockHash, String errorHash) {}
    record Step(RealMatrix fock, List<Integer> historyIterations, List<String> fockHashes,
                List<String> errorHashes, List<Double> coefficients, List<String> events,
                double errorMaximum, double reciprocalCondition, boolean extrapolated) {
        Step { historyIterations=List.copyOf(historyIterations); fockHashes=List.copyOf(fockHashes);
            errorHashes=List.copyOf(errorHashes); coefficients=List.copyOf(coefficients); events=List.copyOf(events); }
    }
    static RealMatrix error(RealMatrix f, RealMatrix p, RealMatrix s) {
        return f.multiply(p).multiply(s).subtract(s.multiply(p).multiply(f));
    }
    Step update(int iteration, RealMatrix fock, RealMatrix error) {
        if (iteration <= previousIteration) throw new IllegalArgumentException("DIIS history must be strictly chronological");
        previousIteration = iteration;
        var f = OneShotNumerics.checkedSymmetric(fock, true);
        OneShotNumerics.finite(error);
        if (error.getRowDimension()!=f.getRowDimension() || error.getColumnDimension()!=f.getColumnDimension())
            throw new IllegalArgumentException("DIIS F/error dimensions differ");
        if (!history.isEmpty() && history.getLast().fock().getRowDimension()!=f.getRowDimension())
            throw new IllegalArgumentException("DIIS history dimension changed");
        double maximum = OneShotNumerics.maxAbs(error);
        var events = new ArrayList<String>();
        boolean growth = Double.isFinite(previousError) && maximum > 1e6 * Math.max(previousError, 1e-12);
        previousError = maximum;
        if (growth) { history.clear(); events.add("CATASTROPHIC_RESIDUAL_GROWTH_CLEAR_AND_PLAIN"); }
        history.add(new History(iteration, f, error.copy(), hash(f), hash(error)));
        if (history.size()>HISTORY_SIZE) { events.add("EVICT_OLDEST:"+history.getFirst().iteration()); history.removeFirst(); }
        if (growth || iteration<START_ITERATION) return fallback(f, maximum, events, "PLAIN_START_OR_GROWTH");
        while (history.size()>=2) {
            try {
                var solve = coefficients(history.stream().map(History::error).toList());
                var extrapolated = new Array2DRowRealMatrix(f.getRowDimension(), f.getColumnDimension());
                for (int i=0;i<f.getRowDimension();i++) for(int j=0;j<f.getColumnDimension();j++) {
                    double value=0;
                    for(int h=0;h<history.size();h++) value+=solve.values().get(h)*history.get(h).fock().getEntry(i,j);
                    extrapolated.setEntry(i,j,value);
                }
                try { OneShotNumerics.finite(extrapolated); }
                catch (ArithmeticException invalid) {
                    history.clear(); return fallback(f,maximum,events,"NONFINITE_EXTRAPOLATION_CLEAR_AND_PLAIN");
                }
                return step(extrapolated,solve.values(),events,maximum,solve.reciprocalCondition(),true);
            } catch (ArithmeticException rejected) {
                events.add("REJECT:"+rejected.getMessage()+":DROP_OLDEST:"+history.getFirst().iteration());
                history.removeFirst();
            }
        }
        return fallback(f,maximum,events,"INSUFFICIENT_VALID_HISTORY_PLAIN");
    }
    private Step fallback(RealMatrix f, double maximum, List<String> events, String reason) {
        events.add(reason); return step(f.copy(),List.of(),events,maximum,0,false);
    }
    private Step step(RealMatrix f,List<Double> c,List<String> events,double maximum,double condition,boolean applied) {
        return new Step(f,history.stream().map(History::iteration).toList(),history.stream().map(History::fockHash).toList(),
                history.stream().map(History::errorHash).toList(),c,events,maximum,condition,applied);
    }
    record Coefficients(List<Double> values,double reciprocalCondition) { Coefficients { values=List.copyOf(values); } }
    /** Full-rank augmented solve; no least-squares/pseudoinverse fallback. */
    static Coefficients coefficients(List<RealMatrix> errors) {
        int m=errors.size();
        if(m<2 || m>HISTORY_SIZE) throw new IllegalArgumentException("DIIS requires 2..8 error histories");
        double scale=0;
        for(var e:errors) scale=Math.max(scale,OneShotNumerics.maxAbs(e));
        if(scale==0) throw new ArithmeticException("ZERO_ERROR_GRAM");
        var a=new Array2DRowRealMatrix(m+1,m+1);
        double diagonal=0;
        for(int i=0;i<m;i++) for(int j=0;j<=i;j++) {
            var x=errors.get(i);var y=errors.get(j);
            if(x.getRowDimension()!=y.getRowDimension() || x.getColumnDimension()!=y.getColumnDimension())
                throw new IllegalArgumentException("DIIS error dimensions differ");
            double dot=0;
            for(int r=0;r<x.getRowDimension();r++)for(int c=0;c<x.getColumnDimension();c++)
                dot+=(x.getEntry(r,c)/scale)*(y.getEntry(r,c)/scale);
            if(!Double.isFinite(dot))throw new ArithmeticException("NONFINITE_GRAM");
            a.setEntry(i,j,dot);a.setEntry(j,i,dot);if(i==j)diagonal=Math.max(diagonal,dot);
        }
        if(!(diagonal>0))throw new ArithmeticException("ZERO_ERROR_GRAM");
        for(int i=0;i<m;i++) {
            for(int j=0;j<m;j++)a.setEntry(i,j,a.getEntry(i,j)/diagonal);
            a.setEntry(i,m,-1);a.setEntry(m,i,-1);
        }
        double[] singular=new SingularValueDecomposition(a).getSingularValues();
        double rcond=singular[m]/singular[0];
        if(!Double.isFinite(rcond) || rcond<=LINEAR_SOLVE_TOLERANCE)throw new ArithmeticException("ILL_CONDITIONED_PULAY");
        var solver=new LUDecomposition(a,LINEAR_SOLVE_TOLERANCE).getSolver();
        if(!solver.isNonSingular())throw new ArithmeticException("SINGULAR_PULAY");
        var rhs=new ArrayRealVector(m+1);rhs.setEntry(m,-1);
        var solution=solver.solve(rhs);double sum=0;var values=new ArrayList<Double>();
        for(int i=0;i<=m;i++) {
            double v=solution.getEntry(i);if(!Double.isFinite(v))throw new ArithmeticException("NONFINITE_COEFFICIENT");
            if(i<m){sum+=v;values.add(v);}
        }
        double residual=a.operate(solution).subtract(rhs).getLInfNorm();
        if(!Double.isFinite(residual) || residual>1e-10 || Math.abs(sum-1)>1e-10)
            throw new ArithmeticException("PULAY_SOLVE_VALIDATION");
        return new Coefficients(values,rcond);
    }
    static String hash(RealMatrix matrix) {
        var canonical=ContentHash.accumulator().line("aether-diis-matrix-v1").line(Integer.toString(matrix.getRowDimension())).line(Integer.toString(matrix.getColumnDimension()));
        for(int i=0;i<matrix.getRowDimension();i++)for(int j=0;j<matrix.getColumnDimension();j++)
            canonical.line(ContentHash.number(matrix.getEntry(i,j)));
        return canonical.finish();
    }
}
