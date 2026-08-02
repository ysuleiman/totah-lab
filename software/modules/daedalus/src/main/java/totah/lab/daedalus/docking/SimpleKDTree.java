package totah.lab.daedalus.docking;

/**
 * @deprecated use {@link totah.lab.euclid.spatial.SimpleKDTree}.
 */
@Deprecated(forRemoval = false)
public class SimpleKDTree<T>
        extends totah.lab.euclid.spatial.SimpleKDTree<T> {

    public SimpleKDTree(int dimensions) {
        super(dimensions);
    }
}
