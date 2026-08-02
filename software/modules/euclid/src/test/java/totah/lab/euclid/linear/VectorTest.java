package totah.lab.euclid.linear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VectorTest {

    @Test
    void defensivelyCopiesInputAndOutputArrays() {
        double[] source = {1.0, 2.0};
        Vector vector = Vector.of(source);
        source[0] = 3.0;

        double[] copy = vector.toArray();
        copy[1] = 4.0;

        assertEquals(1.0, vector.get(0));
        assertArrayEquals(new double[]{1.0, 2.0}, vector.toArray());
    }
}
