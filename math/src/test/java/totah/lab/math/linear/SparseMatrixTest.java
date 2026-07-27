package totah.lab.math.linear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SparseMatrix is the HashMap-CSR backing store for the QEq hardness matrix.
 */
public class SparseMatrixTest {

    @Test
    public void unsetEntriesReadAsZero() {
        SparseMatrix m = new SparseMatrix(3);
        assertEquals(0.0, m.get(1, 2), 0.0, "unset entry must read as zero");
    }

    @Test
    public void setGetRoundTripAndOverwrite() {
        SparseMatrix m = new SparseMatrix(3);
        m.set(1, 2, 4.5);
        assertEquals(4.5, m.get(1, 2), 1e-12, "set/get round trip failed");
        m.set(1, 2, -1.5);
        assertEquals(-1.5, m.get(1, 2), 1e-12, "overwrite failed");
    }

    @Test
    public void multiplyComputesMatVec() {
        SparseMatrix m = new SparseMatrix(3);
        m.set(0, 0, 2.0);
        m.set(0, 2, 1.0);
        m.set(1, 1, 3.0);
        m.set(2, 0, -1.0);
        m.set(2, 2, 2.0);

        double[] y = m.multiply(new double[]{1.0, 2.0, 3.0});
        assertArrayEquals(new double[]{5.0, 6.0, 5.0}, y, 1e-12,
                "sparse mat-vec mismatch");
    }

    @Test
    public void getDiagonalExtractsOnlyDiagonalEntries() {
        SparseMatrix m = new SparseMatrix(3);
        m.set(0, 0, 7.0);
        m.set(0, 1, 9.0);
        m.set(2, 2, -3.0);
        assertArrayEquals(new double[]{7.0, 0.0, -3.0}, m.getDiagonal(), 1e-12,
                "diagonal extraction mismatch");
    }

    @Test
    public void toDenseExpandsIntoRequestedSize() {
        SparseMatrix m = new SparseMatrix(2);
        m.set(0, 1, 5.0);
        m.set(1, 1, 2.0);

        double[][] dense = m.toDense(3);
        assertEquals(3, dense.length, "dense row count");
        assertEquals(3, dense[0].length, "dense column count");
        assertEquals(5.0, dense[0][1], 1e-12, "dense[0][1]");
        assertEquals(2.0, dense[1][1], 1e-12, "dense[1][1]");
        assertEquals(0.0, dense[2][2], 1e-12, "padding must be zero");
    }
}
