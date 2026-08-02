package totah.lab.hermes.metadata;

public record ProteinMetadata(
        String accession,
        String name,
        String gene,
        String organism,
        String function) {
}
