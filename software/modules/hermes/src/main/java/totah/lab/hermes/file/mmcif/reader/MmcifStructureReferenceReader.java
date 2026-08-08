package totah.lab.hermes.file.mmcif.reader;

import org.rcsb.cif.CifIO;
import org.rcsb.cif.schema.StandardSchemata;
import org.rcsb.cif.schema.mm.Entity;
import org.rcsb.cif.schema.mm.EntitySrcGen;
import org.rcsb.cif.schema.mm.EntitySrcNat;
import org.rcsb.cif.schema.mm.MmCifBlock;
import org.rcsb.cif.schema.mm.StructRef;
import org.rcsb.cif.schema.mm.StructRefSeq;
import totah.lab.hermes.file.mmcif.StructureReference;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads source-reported UniProt, organism and chain mappings from entry mmCIF. */
public final class MmcifStructureReferenceReader {
    public List<StructureReference> read(Path path) throws IOException {
        MmCifBlock block = CifIO.readFromPath(path).as(StandardSchemata.MMCIF)
                .getBlocks().getFirst();
        StructRef references = block.getStructRef();
        StructRefSeq sequences = block.getStructRefSeq();
        Map<String, List<String>> chainsByReference = new LinkedHashMap<>();
        for (int row = 0; row < sequences.getRowCount(); row++) {
            String refId = sequences.getRefId().get(row);
            for (String chain : sequences.getPdbxStrandId().get(row).split(",")) {
                String normalized = chain.trim();
                if (!normalized.isEmpty()) {
                    chainsByReference.computeIfAbsent(refId,
                            ignored -> new ArrayList<>()).add(normalized);
                }
            }
        }
        Map<String, Source> sources = sources(block);
        Map<String, String> descriptions = descriptions(block.getEntity());
        List<StructureReference> result = new ArrayList<>();
        for (int row = 0; row < references.getRowCount(); row++) {
            if (!"UNP".equalsIgnoreCase(references.getDbName().get(row))) {
                continue;
            }
            String entityId = references.getEntityId().get(row);
            String refId = references.getId().get(row);
            Source source = sources.getOrDefault(entityId,
                    new Source(null, null));
            result.add(new StructureReference(entityId,
                    references.getPdbxDbAccession().get(row),
                    references.getDbCode().get(row), source.organism(),
                    source.taxonomyId(), descriptions.get(entityId),
                    chainsByReference.getOrDefault(refId, List.of())));
        }
        return List.copyOf(result);
    }

    private static Map<String, Source> sources(MmCifBlock block) {
        Map<String, Source> result = new LinkedHashMap<>();
        EntitySrcGen generated = block.getEntitySrcGen();
        for (int row = 0; row < generated.getRowCount(); row++) {
            result.put(generated.getEntityId().get(row), new Source(
                    generated.getPdbxGeneSrcScientificName().get(row),
                    generated.getPdbxGeneSrcNcbiTaxonomyId().get(row)));
        }
        EntitySrcNat natural = block.getEntitySrcNat();
        for (int row = 0; row < natural.getRowCount(); row++) {
            result.put(natural.getEntityId().get(row), new Source(
                    natural.getPdbxOrganismScientific().get(row),
                    natural.getPdbxNcbiTaxonomyId().get(row)));
        }
        return result;
    }

    private static Map<String, String> descriptions(Entity entity) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int row = 0; row < entity.getRowCount(); row++) {
            result.put(entity.getId().get(row),
                    entity.getPdbxDescription().get(row));
        }
        return result;
    }

    private record Source(String organism, String taxonomyId) {}
}
