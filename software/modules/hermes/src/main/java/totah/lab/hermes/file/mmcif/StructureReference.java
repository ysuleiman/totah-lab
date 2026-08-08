package totah.lab.hermes.file.mmcif;

import java.util.List;

/** Source-reported polymer identity from an RCSB entry mmCIF. */
public record StructureReference(String entityId, String uniProtId,
        String databaseCode, String organism, String taxonomyId,
        String description, List<String> chains) {
    public StructureReference {
        chains = List.copyOf(chains);
    }
}
