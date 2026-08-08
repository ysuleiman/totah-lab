package totah.lab.hermes.file.mmcif;

/** Source-observed polymer chain in an expanded biological assembly. */
public record AssemblyChain(String entityId, String labelAsymId,
        String authAsymId, int modelNumber) {}
