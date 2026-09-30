package totah.lab.aether.integral;

import java.util.List;
import java.util.function.ToDoubleBiFunction;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.basis.PrimitiveGaussian;

/** Ordered contraction arithmetic shared by overlap normalization, S and T. */
final class GaussianContraction {
    private GaussianContraction() {}

    static double normalized(ContractedGaussian a, ContractedGaussian b,
                             ToDoubleBiFunction<PrimitiveGaussian, PrimitiveGaussian> integral) {
        return requireFinite(sum(a.terms(), b.terms(), integral) * a.normalization() * b.normalization());
    }

    static double sum(List<GaussianTerm> a, List<GaussianTerm> b,
                      ToDoubleBiFunction<PrimitiveGaussian, PrimitiveGaussian> integral) {
        double sum = 0;
        for (GaussianTerm left : a) {
            for (GaussianTerm right : b) {
                sum += left.coefficient() * right.coefficient()
                        * integral.applyAsDouble(left.primitive(), right.primitive());
            }
        }
        return requireFinite(sum);
    }

    static double requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("NUMERICAL_FAILURE: nonfinite s integral arithmetic");
        }
        return value;
    }

    /** Ordered four-function expansion with Neumaier compensation for signed contractions. */
    static double normalized(ContractedGaussian a, ContractedGaussian b,
                             ContractedGaussian c, ContractedGaussian d, PrimitiveQuartet integral) {
        return normalizedIndexed(a,b,c,d,(i,j,k,l)->integral.evaluate(a.terms().get(i).primitive(),b.terms().get(j).primitive(),c.terms().get(k).primitive(),d.terms().get(l).primitive()));
    }

    static double normalizedIndexed(ContractedGaussian a,ContractedGaussian b,ContractedGaussian c,ContractedGaussian d,IndexedQuartet integral) {
        double sum = 0;
        double correction = 0;
        for (int i=0;i<a.terms().size();i++) {
            GaussianTerm ta=a.terms().get(i);
            for (int j=0;j<b.terms().size();j++) {
                GaussianTerm tb=b.terms().get(j);
                for (int k=0;k<c.terms().size();k++) {
                    GaussianTerm tc=c.terms().get(k);
                    for (int l=0;l<d.terms().size();l++) {
                        GaussianTerm td=d.terms().get(l);
                        // Reuse the already-validated contraction normalizations. Applying them
                        // to each coefficient avoids an unnecessarily large unnormalized sum.
                        double wa = requireFinite(ta.coefficient() * a.normalization());
                        double wb = requireFinite(tb.coefficient() * b.normalization());
                        double wc = requireFinite(tc.coefficient() * c.normalization());
                        double wd = requireFinite(td.coefficient() * d.normalization());
                        double primitive = integral.evaluate(i,j,k,l);
                        double term = wa == 0 || wb == 0 || wc == 0 || wd == 0 || primitive == 0
                                ? 0 : wa * wb * wc * wd * primitive;
                        if ((!Double.isFinite(term) || StrictMath.abs(term) < Double.MIN_NORMAL)
                                && wa != 0 && wb != 0 && wc != 0 && wd != 0 && primitive != 0) {
                            double sign = StrictMath.copySign(1, wa) * StrictMath.copySign(1, wb)
                                    * StrictMath.copySign(1, wc) * StrictMath.copySign(1, wd) * StrictMath.copySign(1, primitive);
                            term = sign * StrictMath.exp(StrictMath.log(StrictMath.abs(wa))
                                    + StrictMath.log(StrictMath.abs(wb)) + StrictMath.log(StrictMath.abs(wc))
                                    + StrictMath.log(StrictMath.abs(wd)) + StrictMath.log(StrictMath.abs(primitive)));
                        }
                        requireFinite(term);
                        double next = requireFinite(sum + term);
                        correction = requireFinite(correction + (StrictMath.abs(sum) >= StrictMath.abs(term)
                                ? (sum - next) + term : (term - next) + sum));
                        sum = next;
                    }
                }
            }
        }
        return requireFinite(sum + correction);
    }

    @FunctionalInterface
    interface IndexedQuartet { double evaluate(int i,int j,int k,int l); }

    @FunctionalInterface
    interface PrimitiveQuartet {
        double evaluate(PrimitiveGaussian a, PrimitiveGaussian b, PrimitiveGaussian c, PrimitiveGaussian d);
    }
}
