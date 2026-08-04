package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.geometry.PocketPointCloud;

/**
 * Aligns a candidate pocket point cloud onto a fixed query pocket point cloud.
 */
public interface PocketAligner {

    /**
     * Aligns {@code candidate} onto {@code query}.
     *
     * @param query     fixed target pocket
     * @param candidate pocket transformed into the query coordinate system
     * @return completed pocket alignment
     */
    PocketAlignment align(
            PocketPointCloud query,
            PocketPointCloud candidate
    );
}