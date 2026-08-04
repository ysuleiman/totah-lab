package totah.lab.athena.pocket.geometry;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/**
 * Builds comparable point-cloud representations for two pockets.
 *
 * <p>Alpha-sphere geometry is used only when both pockets originate from
 * fpocket. All other source combinations use resolved residue heavy atoms.</p>
 */
public final class PocketPointCloudFactory {

    public PocketPointCloudPair createComparablePair(
            Structure queryStructure,
            Pocket queryPocket,
            Structure candidateStructure,
            Pocket candidatePocket
    ) {
        Objects.requireNonNull(
                queryStructure,
                "queryStructure"
        );
        Objects.requireNonNull(
                queryPocket,
                "queryPocket"
        );
        Objects.requireNonNull(
                candidateStructure,
                "candidateStructure"
        );
        Objects.requireNonNull(
                candidatePocket,
                "candidatePocket"
        );

        PocketGeometryBasis basis =
                shouldUseAlphaSpheres(
                        queryPocket,
                        candidatePocket
                )
                        ? PocketGeometryBasis.ALPHA_SPHERES
                        : PocketGeometryBasis.RESIDUE_ATOMS;

        return new PocketPointCloudPair(
                PocketPointCloud.from(
                        queryStructure,
                        queryPocket
                ),
                PocketPointCloud.from(
                        candidateStructure,
                        candidatePocket
                )
        );
    }

    private static boolean shouldUseAlphaSpheres(
            Pocket queryPocket,
            Pocket candidatePocket
    ) {
        return queryPocket.source() == PocketSource.FPOCKET
                && candidatePocket.source() == PocketSource.FPOCKET;
    }
}
