package totah.lab.topology;

public record TopologyConfig(
        double voxelGridSize,      // Default: 4.0
        double hydrogenClashCutoff,// Default: 1.1
        double plddtCutoff         // Default: 50.0 (Perfect for AlphaFold!)
) {
    public static TopologyConfig createDefault() {
        return new TopologyConfig(4.0, 1.1, 50.0);
    }
}
