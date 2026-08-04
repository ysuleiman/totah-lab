package totah.lab.athena.pocket.geometry;

import java.util.Objects;

/**
 * Two pocket point clouds represented using the same geometry basis.
 */
public record PocketPointCloudPair(
        PocketPointCloud query,
        PocketPointCloud candidate
) {

    public PocketPointCloudPair {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");

        if (query.basis() != candidate.basis()) {
            throw new IllegalArgumentException(
                    "Pocket point clouds must use the same basis: "
                            + query.basis()
                            + " vs "
                            + candidate.basis()
            );
        }
    }
}