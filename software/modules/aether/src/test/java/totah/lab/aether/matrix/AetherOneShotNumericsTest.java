package totah.lab.aether.matrix;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.MatrixUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic fault inputs exercise guards unreachable through the already-immutable symmetric S/F producers. */
class AetherOneShotNumericsTest {
    @Test
    void rejectsEvenTinyOverlapAsymmetry() {
        var s = new Array2DRowRealMatrix(new double[][]{{1,0.1},{0.1+1e-14,1}});
        assertThrows(ArithmeticException.class,()->OneShotNumerics.solve(s,MatrixUtils.createRealIdentityMatrix(2)));
    }

    @Test
    void rejectsFockAsymmetryBeyondTolerance() {
        var f = new Array2DRowRealMatrix(new double[][]{{1,0.1},{0.11,2}});
        assertThrows(ArithmeticException.class,()->OneShotNumerics.solve(MatrixUtils.createRealIdentityMatrix(2),f));
    }

    @Test
    void explicitlyAveragesOnlyRoundoffLevelFockAsymmetry() {
        var f = new Array2DRowRealMatrix(new double[][]{{1,0.1},{0.1+1e-14,2}});
        var result = OneShotNumerics.solve(MatrixUtils.createRealIdentityMatrix(2),f);
        assertEquals(0.1/2+(0.1+1e-14)/2,result.fock().getEntry(0,1));
        assertEquals(result.fock().getEntry(0,1),result.fock().getEntry(1,0));
        assertEquals(0.1,f.getEntry(0,1));
    }

    @ParameterizedTest
    @ValueSource(doubles={-1,0,1e-12,1e-10})
    void rejectsIndefiniteSingularAndRankDeficientOverlap(double eigenvalue) {
        var s=MatrixUtils.createRealDiagonalMatrix(new double[]{1,eigenvalue});
        var error=assertThrows(ArithmeticException.class,()->OneShotNumerics.solve(s,MatrixUtils.createRealIdentityMatrix(2)));
        assertTrue(error.getMessage().contains("no regularization"));
    }

    @Test
    void retainsAllDirectionsWhenOverlapIsAboveCutoff() {
        var s=MatrixUtils.createRealDiagonalMatrix(new double[]{1,1e-8});
        var result=OneShotNumerics.solve(s,MatrixUtils.createRealDiagonalMatrix(new double[]{2,3}));
        assertEquals(2,result.energies().length);
        assertEquals(1e-8,result.overlapEigenvalues()[0]);
        assertEquals(10000,result.orthogonalization().getEntry(1,1));
        assertEquals(3e8,result.energies()[1],1e-6);
    }

    @ParameterizedTest
    @ValueSource(doubles={Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
    void rejectsNonfiniteMatricesEigenvaluesAndEigenvectors(double value) {
        var bad=MatrixUtils.createRealDiagonalMatrix(new double[]{1,value});
        var identity=MatrixUtils.createRealIdentityMatrix(2);
        assertThrows(ArithmeticException.class,()->OneShotNumerics.solve(bad,identity));
        assertThrows(ArithmeticException.class,()->OneShotNumerics.solve(identity,bad));
        assertThrows(ArithmeticException.class,()->OneShotNumerics.validateEigenpairs(identity,new OneShotNumerics.Eigenpairs(new double[]{1,value},identity)));
        assertThrows(ArithmeticException.class,()->OneShotNumerics.validateEigenpairs(identity,new OneShotNumerics.Eigenpairs(new double[]{1,1},bad)));
    }

    @Test
    void rejectsUnnormalizedAndNonOrthogonalEigenvectors() {
        var identity=MatrixUtils.createRealIdentityMatrix(2);
        for (var bad:new double[][][]{{{2,0},{0,1}},{{0,0},{0,1}},{{1,1},{0,0}}}) {
            var e=assertThrows(ArithmeticException.class,()->OneShotNumerics.validateEigenpairs(identity,
                    new OneShotNumerics.Eigenpairs(new double[]{1,1},new Array2DRowRealMatrix(bad))));
            assertTrue(e.getMessage().contains("normalization failure"));
        }
    }

    @Test
    void rejectsNormalizedButIncorrectEigenpairs() {
        assertThrows(ArithmeticException.class,()->OneShotNumerics.validateEigenpairs(
                MatrixUtils.createRealDiagonalMatrix(new double[]{1,2}),
                new OneShotNumerics.Eigenpairs(new double[]{2,1},MatrixUtils.createRealIdentityMatrix(2))));
    }

    @Test
    void canonicalSignUsesFirstIndexForExactMagnitudeTie() {
        var ao=new Array2DRowRealMatrix(new double[][]{{-1,1},{1,-1}}); var orthogonal=ao.copy();
        OneShotNumerics.canonicalize(ao,orthogonal);
        assertEquals(1,ao.getEntry(0,0)); assertEquals(-1,ao.getEntry(1,0));
        assertEquals(1,ao.getEntry(0,1)); assertEquals(-1,ao.getEntry(1,1));
        assertEquals(ao,orthogonal);
        var zero=new Array2DRowRealMatrix(2,2);
        assertThrows(ArithmeticException.class,()->OneShotNumerics.canonicalize(zero,zero.copy()));
    }

    @Test
    void exactDegeneracyRetainsNormalizedSubspaceAndDeterministicBackendOrder() {
        var s=MatrixUtils.createRealIdentityMatrix(3); var f=s.scalarMultiply(2);
        var first=OneShotNumerics.solve(s,f); var second=OneShotNumerics.solve(s,f);
        assertArrayEquals(new double[]{2,2,2},first.energies());
        assertEquals(first.coefficients(),second.coefficients());
        assertEquals(0,first.generalizedResidual());
        assertEquals(0,first.orthonormalityError());
    }
}
