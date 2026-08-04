package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import totah.lab.gaia.pocket.AlphaSphere;

import java.util.Objects;

/**
 * One fpocket alpha sphere (voronoi vertex) of a pocket, in parser order.
 *
 * sphere_index is the 0-based position in the pocketN_vert.pqr file, not
 * the PQR atom serial. Spheres come from fpocket output only and are
 * never regenerated from structure geometry.
 *
 * Public (like the other entities) because the alpha-sphere backfill
 * service in the service package constructs rows; it is not used by the
 * read-side controllers.
 *
 * Table created by tools/scripts/sql/docking/pocket-alpha-sphere.sql.
 */
@Entity
@Table(
        name = "pocket_alpha_sphere",
        uniqueConstraints = @UniqueConstraint(
                name = "pocket_alpha_sphere_order_unique",
                columnNames = {"pocket_id", "sphere_index"}
        )
)
public class PocketAlphaSphereEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pocket_id", nullable = false)
    private PocketEntity pocket;

    @Column(name = "sphere_index", nullable = false)
    private int sphereIndex;

    @Column(name = "center_x", nullable = false)
    private double centerX;

    @Column(name = "center_y", nullable = false)
    private double centerY;

    @Column(name = "center_z", nullable = false)
    private double centerZ;

    @Column(name = "radius", nullable = false)
    private double radius;

    protected PocketAlphaSphereEntity() {
    }

    /**
     * Builds a row from a parsed Gaia alpha sphere; {@code sphereIndex}
     * is the sphere's 0-based parser order within its pocket.
     */
    public static PocketAlphaSphereEntity from(
            AlphaSphere sphere,
            int sphereIndex
    ) {
        Objects.requireNonNull(sphere, "sphere");
        return new PocketAlphaSphereEntity(
                sphereIndex,
                sphere.center().x(),
                sphere.center().y(),
                sphere.center().z(),
                sphere.radius()
        );
    }

    public PocketAlphaSphereEntity(
            int sphereIndex,
            double centerX,
            double centerY,
            double centerZ,
            double radius
    ) {
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException(
                    "Alpha-sphere radius must be finite and positive: "
                            + radius
            );
        }
        if (!Double.isFinite(centerX)
                || !Double.isFinite(centerY)
                || !Double.isFinite(centerZ)) {
            throw new IllegalArgumentException(
                    "Alpha-sphere center must be finite"
            );
        }
        this.sphereIndex = sphereIndex;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    public Long getId() {
        return id;
    }

    public PocketEntity getPocket() {
        return pocket;
    }

    public void setPocket(PocketEntity pocket) {
        this.pocket = pocket;
    }

    public int getSphereIndex() {
        return sphereIndex;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public double getRadius() {
        return radius;
    }
}
