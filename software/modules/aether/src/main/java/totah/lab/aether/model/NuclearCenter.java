package totah.lab.aether.model;

import java.util.Objects;
import totah.lab.gaia.geometry.Point3D;

/** Fixed point nucleus: bohr coordinates and positive charge in elementary-charge units.
 * This is an external Coulomb source, not an atom, formal charge or electronic state.
 */
public record NuclearCenter(Point3D centerBohr, double charge) {
    public NuclearCenter {
        Objects.requireNonNull(centerBohr, "centerBohr");
        if (!Double.isFinite(charge) || charge <= 0) {
            throw new IllegalArgumentException("Nuclear charge must be finite and positive");
        }
    }
}
