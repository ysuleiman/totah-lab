package totah.lab.athena.pocket.architecture;

/**
 * Thresholds for {@link WallGeometryAnalyzer}. Calibration-pending
 * geometric conventions, documented on the result record.
 */
public record WallGeometryOptions(
        int normalNeighbourCount
) {

    public WallGeometryOptions {
        if (normalNeighbourCount < 3) {
            throw new IllegalArgumentException(
                    "normalNeighbourCount must be at least 3"
            );
        }
    }

    public static WallGeometryOptions defaults() {
        return new WallGeometryOptions(6);
    }
}
