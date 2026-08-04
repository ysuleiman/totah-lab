package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.Objects;

/**
 * Result of aligning a candidate pocket point cloud onto a query pocket.
 *
 * @param query            fixed query point cloud
 * @param alignedCandidate candidate point cloud after applying the returned
 *                         transform
 * @param transform        transform from the original candidate coordinate
 *                         system into the query coordinate system
 * @param rmsd             nearest-neighbor root mean squared distance
 * @param iterations       number of refinement iterations performed
 * @param converged        whether the alignment procedure completed its
 *                         convergence criterion
 */
public record PocketAlignment(
        PocketPointCloud query,
        PocketPointCloud alignedCandidate,
        RigidTransform transform,
        double rmsd,
        int iterations,
        boolean converged
) {

    public PocketAlignment {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(
                alignedCandidate,
                "alignedCandidate"
        );
        Objects.requireNonNull(transform, "transform");

        if (query.basis() != alignedCandidate.basis()) {
            throw new IllegalArgumentException(
                    "Aligned point clouds must use the same geometry basis: "
                            + query.basis()
                            + " vs "
                            + alignedCandidate.basis()
            );
        }

        if (!Double.isFinite(rmsd) || rmsd < 0.0) {
            throw new IllegalArgumentException(
                    "rmsd must be finite and non-negative"
            );
        }

        if (iterations < 0) {
            throw new IllegalArgumentException(
                    "iterations must be non-negative"
            );
        }
    }
}