package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.PocketAlignmentResult;

import java.util.List;
import java.util.Objects;

/**
 * Alpha-sphere-level architecture comparison of two pockets after
 * structural pocket alignment (candidate/B transformed into the
 * query/A frame).
 *
 * <p>Definitions:</p>
 * <ul>
 *   <li>{@code nearestNeighborDistancesAtoB}/{@code ...BtoA}: per
 *       sphere (in pocket order), distance to the nearest OTHER-pocket
 *       sphere center in the aligned frame — the per-sphere
 *       displacement field.</li>
 *   <li>{@code componentsA}/{@code componentsB}: connected components
 *       of each sphere set under union-find, with an edge when two
 *       sphere surfaces are within
 *       {@code componentGapAngstroms}
 *       ({@code centerDistance - r1 - r2 <= gap}). Component structure
 *       is computed in each pocket's own frame (it is
 *       transform-invariant).</li>
 *   <li>{@code principalAxisAngleDegrees}: acute angle between the
 *       first principal axes after rotating B's axis into the A
 *       frame.</li>
 *   <li>{@code uniqueSpheresA}/{@code uniqueSpheresB}: sphere ids with
 *       no other-pocket sphere center within
 *       {@code uniqueSphereDistanceAngstroms} in the aligned
 *       frame.</li>
 *   <li>{@code sphereVolumeSum*}: plain sum of sphere volumes
 *       &Sigma;4/3&pi;r&sup3; — an empty-volume proxy that counts
 *       overlaps multiply (documented approximation, deterministic);
 *       {@code sphereVolumeSumDelta} = B &minus; A.</li>
 * </ul>
 */
public record AlphaSphereArchitectureComparison(
        PocketAlignmentResult alignment,
        List<Double> nearestNeighborDistancesAtoB,
        List<Double> nearestNeighborDistancesBtoA,
        SphereComponents componentsA,
        SphereComponents componentsB,
        double principalAxisAngleDegrees,
        List<Long> uniqueSpheresA,
        List<Long> uniqueSpheresB,
        double sphereVolumeSumA,
        double sphereVolumeSumB,
        double sphereVolumeSumDelta
) {

    /**
     * Connected-component structure of one alpha-sphere set.
     *
     * @param componentCount number of components
     * @param componentSizes component sizes, sorted descending
     * @param componentBySphereIndex component id (0-based, in order of
     *                               first discovery) per sphere index
     */
    public record SphereComponents(
            int componentCount,
            List<Integer> componentSizes,
            List<Integer> componentBySphereIndex
    ) {
        public SphereComponents {
            componentSizes = List.copyOf(
                    Objects.requireNonNull(
                            componentSizes,
                            "componentSizes"
                    )
            );
            componentBySphereIndex = List.copyOf(
                    Objects.requireNonNull(
                            componentBySphereIndex,
                            "componentBySphereIndex"
                    )
            );
        }
    }

    public AlphaSphereArchitectureComparison {
        Objects.requireNonNull(alignment, "alignment");
        nearestNeighborDistancesAtoB = List.copyOf(
                Objects.requireNonNull(nearestNeighborDistancesAtoB,
                        "nearestNeighborDistancesAtoB")
        );
        nearestNeighborDistancesBtoA = List.copyOf(
                Objects.requireNonNull(nearestNeighborDistancesBtoA,
                        "nearestNeighborDistancesBtoA")
        );
        Objects.requireNonNull(componentsA, "componentsA");
        Objects.requireNonNull(componentsB, "componentsB");
        uniqueSpheresA = List.copyOf(
                Objects.requireNonNull(
                        uniqueSpheresA,
                        "uniqueSpheresA"
                )
        );
        uniqueSpheresB = List.copyOf(
                Objects.requireNonNull(
                        uniqueSpheresB,
                        "uniqueSpheresB"
                )
        );
    }
}
