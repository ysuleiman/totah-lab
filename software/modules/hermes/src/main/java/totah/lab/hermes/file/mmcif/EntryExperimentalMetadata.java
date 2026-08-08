package totah.lab.hermes.file.mmcif;

/** Source-reported experimental metadata from an RCSB entry mmCIF. */
public record EntryExperimentalMetadata(String method,
        Double resolutionAngstrom) {}
