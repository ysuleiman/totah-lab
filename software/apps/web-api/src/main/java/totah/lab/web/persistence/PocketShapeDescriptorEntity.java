package totah.lab.web.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import totah.lab.athena.pocket.similar.PocketRetrievalDistance;
import totah.lab.athena.pocket.similar.PocketShapeDescriptor;

import java.util.Objects;

/**
 * The precomputed Stage 1 retrieval shape descriptor of one pocket,
 * computed by Athena's {@code PocketShapeDescriptorFactory} from the
 * pocket's persisted alpha-sphere centers (12 radial bins).
 *
 * elongation and flatness are the NORMALIZED forms the Stage 1 retrieval
 * distance uses (middle/major and minor/major, 0 when major is 0), not
 * the unbounded ratios on the Athena descriptor. h0..h11 are the
 * factory-normalized radial-histogram bins.
 *
 * Public (like the other entities) because the descriptor service in the
 * service package constructs rows; it is not used by the read-side
 * controllers.
 *
 * Table created by tools/scripts/sql/docking/pocket-shape-descriptor.sql.
 */
@Entity
@Table(name = "pocket_shape_descriptor")
public class PocketShapeDescriptorEntity {

    @Id
    @Column(name = "pocket_id", nullable = false)
    private Long pocketId;

    @Column(name = "point_count", nullable = false)
    private int pointCount;

    @Column(name = "radius_of_gyration", nullable = false)
    private double radiusOfGyration;

    @Column(name = "extent_major", nullable = false)
    private double extentMajor;

    @Column(name = "extent_middle", nullable = false)
    private double extentMiddle;

    @Column(name = "extent_minor", nullable = false)
    private double extentMinor;

    @Column(name = "elongation", nullable = false)
    private double elongation;

    @Column(name = "flatness", nullable = false)
    private double flatness;

    @Column(name = "h0", nullable = false)
    private double h0;

    @Column(name = "h1", nullable = false)
    private double h1;

    @Column(name = "h2", nullable = false)
    private double h2;

    @Column(name = "h3", nullable = false)
    private double h3;

    @Column(name = "h4", nullable = false)
    private double h4;

    @Column(name = "h5", nullable = false)
    private double h5;

    @Column(name = "h6", nullable = false)
    private double h6;

    @Column(name = "h7", nullable = false)
    private double h7;

    @Column(name = "h8", nullable = false)
    private double h8;

    @Column(name = "h9", nullable = false)
    private double h9;

    @Column(name = "h10", nullable = false)
    private double h10;

    @Column(name = "h11", nullable = false)
    private double h11;

    @Column(name = "descriptor_version", nullable = false)
    private int descriptorVersion;

    protected PocketShapeDescriptorEntity() {
    }

    /**
     * Builds a row from an Athena descriptor; elongation and flatness are
     * stored in the normalized forms (middle/major, minor/major — 0 when
     * the major extent is 0) the Stage 1 retrieval distance uses.
     */
    public static PocketShapeDescriptorEntity from(
            long pocketId,
            PocketShapeDescriptor descriptor
    ) {
        Objects.requireNonNull(descriptor, "descriptor");

        double[] histogram = Objects.requireNonNull(
                descriptor.radialHistogram(),
                "descriptor.radialHistogram"
        );
        if (histogram.length != 12) {
            throw new IllegalArgumentException(
                    "Descriptor radial histogram must have 12 bins: "
                            + histogram.length
            );
        }

        double major = descriptor.majorExtent();

        return new PocketShapeDescriptorEntity(
                pocketId,
                descriptor.pointCount(),
                descriptor.radiusOfGyration(),
                major,
                descriptor.middleExtent(),
                descriptor.minorExtent(),
                major == 0.0 ? 0.0 : descriptor.middleExtent() / major,
                major == 0.0 ? 0.0 : descriptor.minorExtent() / major,
                histogram,
                PocketRetrievalDistance.DESCRIPTOR_VERSION
        );
    }

    private PocketShapeDescriptorEntity(
            long pocketId,
            int pointCount,
            double radiusOfGyration,
            double extentMajor,
            double extentMiddle,
            double extentMinor,
            double elongation,
            double flatness,
            double[] radialHistogram,
            int descriptorVersion
    ) {
        this.pocketId = pocketId;
        this.pointCount = pointCount;
        this.radiusOfGyration = radiusOfGyration;
        this.extentMajor = extentMajor;
        this.extentMiddle = extentMiddle;
        this.extentMinor = extentMinor;
        this.elongation = elongation;
        this.flatness = flatness;
        this.h0 = radialHistogram[0];
        this.h1 = radialHistogram[1];
        this.h2 = radialHistogram[2];
        this.h3 = radialHistogram[3];
        this.h4 = radialHistogram[4];
        this.h5 = radialHistogram[5];
        this.h6 = radialHistogram[6];
        this.h7 = radialHistogram[7];
        this.h8 = radialHistogram[8];
        this.h9 = radialHistogram[9];
        this.h10 = radialHistogram[10];
        this.h11 = radialHistogram[11];
        this.descriptorVersion = descriptorVersion;
    }

    public Long getPocketId() {
        return pocketId;
    }

    public int getPointCount() {
        return pointCount;
    }

    public double getRadiusOfGyration() {
        return radiusOfGyration;
    }

    public double getExtentMajor() {
        return extentMajor;
    }

    public double getExtentMiddle() {
        return extentMiddle;
    }

    public double getExtentMinor() {
        return extentMinor;
    }

    public double getElongation() {
        return elongation;
    }

    public double getFlatness() {
        return flatness;
    }

    public double[] getRadialHistogram() {
        return new double[]{
                h0, h1, h2, h3, h4, h5, h6, h7, h8, h9, h10, h11
        };
    }

    public int getDescriptorVersion() {
        return descriptorVersion;
    }
}
