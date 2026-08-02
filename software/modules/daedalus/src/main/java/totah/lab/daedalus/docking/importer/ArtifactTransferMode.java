package totah.lab.docking.importer;

/**
 * Controls how source artifacts are retained when docking data is imported.
 */
public enum ArtifactTransferMode {
    /**
     * Keep the resolved source path. This is fast, but the source artifact store
     * must remain mounted and immutable.
     */
    LINK,

    /**
     * Copy the artifact into the destination archive and verify its checksum.
     */
    COPY
}
