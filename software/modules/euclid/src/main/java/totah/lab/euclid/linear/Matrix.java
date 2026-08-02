package totah.lab.euclid.linear;

/** A square numerical matrix. */
public interface Matrix {

    int size();

    double get(int row, int column);

    double[] multiply(double[] vector);
}
