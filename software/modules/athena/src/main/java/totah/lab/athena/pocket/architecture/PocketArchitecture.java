package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.geometry.PocketGeometry;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Geometric descriptor of one pocket, derived from its alpha spheres.
 *
 * <p><b>All values are geometric estimates from the alpha-sphere
 * representation, not physical measurements.</b> Exact
 * definitions:</p>
 *
 * <ul>
 *   <li>{@code centroid}, {@code principalComponents}: mean and PCA of
 *       the alpha-sphere centers.</li>
 *   <li>{@code extentsAlongAxes}: max-minus-min projection of the
 *       sphere centers onto each principal axis.</li>
 *   <li>Mouth spheres: the spheres whose first-axis projection lies
 *       within one mean sphere radius of the maximum projection (the
 *       +u1 end of the longest axis; "solvent-facing" by
 *       convention).</li>
 *   <li>Mouth plane: the plane perpendicular to the first principal
 *       axis at the mean projection of the mouth spheres.
 *       {@code mouthCenter} is the centroid of the mouth sphere
 *       centers.</li>
 *   <li>{@code cavityDepth}: distance from the deepest (minimum
 *       projection) sphere center to the mouth plane.</li>
 *   <li>{@code mouthWidth}: maximum pairwise center distance among
 *       mouth spheres; {@code mouthArea}: disc approximation
 *       &pi;(width/2)&sup2;.</li>
 *   <li>{@code bottleneckRadius}: minimum sphere radius among the
 *       "neck" spheres — those in the third of the depth range
 *       adjacent to the mouth. fpocket places small spheres in narrow
 *       passages, so this is a proxy for the narrowest passage near
 *       the mouth.</li>
 *   <li>{@code reportedVolume}/{@code reportedTotalSasa}: the
 *       fpocket-reported metrics when the pocket carries them,
 *       {@code null} otherwise.</li>
 * </ul>
 */
public record PocketArchitecture(
        Pocket pocket,
        Point3D centroid,
        PrincipalComponents principalComponents,
        List<Double> extentsAlongAxes,
        int alphaSphereCount,
        double meanSphereRadius,
        Double reportedVolume,
        Double reportedTotalSasa,
        List<Long> mouthSphereIds,
        Point3D mouthCenter,
        double mouthPlaneProjection,
        double cavityDepth,
        double mouthWidth,
        double mouthArea,
        double bottleneckRadius
) {

    public PocketArchitecture {
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(centroid, "centroid");
        Objects.requireNonNull(principalComponents,
                "principalComponents");
        extentsAlongAxes = List.copyOf(
                Objects.requireNonNull(extentsAlongAxes,
                        "extentsAlongAxes")
        );
        mouthSphereIds = List.copyOf(
                Objects.requireNonNull(mouthSphereIds, "mouthSphereIds")
        );
        Objects.requireNonNull(mouthCenter, "mouthCenter");
    }

    /**
     * Describes {@code pocket} from its alpha spheres.
     *
     * @throws IllegalArgumentException if the pocket has fewer than 3
     *         alpha spheres
     */
    public static PocketArchitecture of(Pocket pocket) {
        Objects.requireNonNull(pocket, "pocket");

        List<AlphaSphere> spheres = pocket.alphaSphereSet()
                .map(AlphaSphereSet::spheres)
                .orElse(List.of());

        if (spheres.size() < 3) {
            throw new IllegalArgumentException(
                    "Pocket architecture requires at least 3 alpha "
                            + "spheres: " + pocket.id()
            );
        }

        List<Point3D> centers = spheres.stream()
                .map(AlphaSphere::center)
                .toList();

        PrincipalComponents pca = PrincipalComponents.of(centers);

        double[] minProjection = {
                Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] maxProjection = {
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (Point3D center : centers) {
            for (int axis = 0; axis < 3; axis++) {
                double projection = pca.projection(center, axis);
                minProjection[axis] =
                        Math.min(minProjection[axis], projection);
                maxProjection[axis] =
                        Math.max(maxProjection[axis], projection);
            }
        }

        List<Double> extents = new ArrayList<>(3);
        for (int axis = 0; axis < 3; axis++) {
            extents.add(maxProjection[axis] - minProjection[axis]);
        }

        double meanRadius = spheres.stream()
                .mapToDouble(AlphaSphere::radius)
                .average()
                .orElse(0.0);

        double pMax = maxProjection[0];
        double pMin = minProjection[0];

        List<AlphaSphere> mouthSpheres = spheres.stream()
                .filter(sphere -> pca.projection(sphere.center(), 0)
                        >= pMax - meanRadius)
                .toList();

        double mouthPlaneProjection = mouthSpheres.stream()
                .mapToDouble(sphere -> pca.projection(sphere.center(), 0))
                .average()
                .orElse(pMax);

        double cavityDepth = mouthPlaneProjection - pMin;

        double mouthWidth = 0.0;
        for (int first = 0; first < mouthSpheres.size(); first++) {
            for (int second = first + 1;
                    second < mouthSpheres.size(); second++) {
                mouthWidth = Math.max(
                        mouthWidth,
                        mouthSpheres.get(first).center().distance(
                                mouthSpheres.get(second).center())
                );
            }
        }

        double mouthArea =
                Math.PI * mouthWidth * mouthWidth / 4.0;

        double mouthCenterX = 0.0;
        double mouthCenterY = 0.0;
        double mouthCenterZ = 0.0;

        for (AlphaSphere mouthSphere : mouthSpheres) {
            mouthCenterX += mouthSphere.center().x();
            mouthCenterY += mouthSphere.center().y();
            mouthCenterZ += mouthSphere.center().z();
        }

        double mouthCount = mouthSpheres.size();
        Point3D mouthCenter = new Point3D(
                mouthCenterX / mouthCount,
                mouthCenterY / mouthCount,
                mouthCenterZ / mouthCount
        );

        double neckFloor = pMax - (pMax - pMin) / 3.0;
        double bottleneckRadius = spheres.stream()
                .filter(sphere -> pca.projection(sphere.center(), 0)
                        >= neckFloor)
                .mapToDouble(AlphaSphere::radius)
                .min()
                .orElse(meanRadius);

        return new PocketArchitecture(
                pocket,
                PocketGeometry.alphaSphereCentroid(
                        pocket.alphaSphereSet().orElseThrow()),
                pca,
                extents,
                spheres.size(),
                meanRadius,
                reportedMetric(pocket, PocketMetricType.VOLUME),
                reportedMetric(pocket, PocketMetricType.TOTAL_SASA),
                mouthSpheres.stream().map(AlphaSphere::id).toList(),
                mouthCenter,
                mouthPlaneProjection,
                cavityDepth,
                mouthWidth,
                mouthArea,
                bottleneckRadius
        );
    }

    private static Double reportedMetric(
            Pocket pocket,
            PocketMetricType type
    ) {
        var metric = pocket.metric(type);

        return metric.isPresent() ? metric.getAsDouble() : null;
    }
}
