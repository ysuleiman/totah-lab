package totah.lab.euclid.linear;

import java.util.Arrays;

/** Immutable numerical vector. */
public final class Vector {

    private final double[] values;

    private Vector(double[] values) {
        this.values = values.clone();
    }

    public static Vector of(double... values) {
        return new Vector(values);
    }

    public int size() {
        return values.length;
    }

    public double get(int index) {
        return values[index];
    }

    public double[] toArray() {
        return values.clone();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Vector vector
                && Arrays.equals(values, vector.values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
