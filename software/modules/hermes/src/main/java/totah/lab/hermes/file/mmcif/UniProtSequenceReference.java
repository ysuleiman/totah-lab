package totah.lab.hermes.file.mmcif;

/** UniProt sequence and identity reported in an RCSB entry mmCIF. */
public record UniProtSequenceReference(String accession, String databaseCode,
        String sequence) {}
