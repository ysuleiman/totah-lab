package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** A bounded topology-changing edit; chemical-state changes deliberately do not belong here. */
public record TopologyEdit(String editId, Type type, Set<String> removedAtomIds,
                           MolecularGraph replacementFragment,
                           List<Attachment> attachments, Set<String> openedBondIds,
                           List<Closure> closures, Map<String,String> replacementAtomLineage,
                           StereoDisposition stereoDisposition) {
    public TopologyEdit {
        if (editId == null || editId.isBlank() || type == null) throw new IllegalArgumentException("edit identity required");
        removedAtomIds=Set.copyOf(removedAtomIds==null?Set.of():removedAtomIds);
        attachments=List.copyOf(attachments==null?List.of():attachments);
        openedBondIds=Set.copyOf(openedBondIds==null?Set.of():openedBondIds);
        closures=List.copyOf(closures==null?List.of():closures);
        replacementAtomLineage=Map.copyOf(replacementAtomLineage==null?Map.of():replacementAtomLineage);
        stereoDisposition=stereoDisposition==null?StereoDisposition.PRESERVE_UNAFFECTED:stereoDisposition;
    }
    public enum Type { INDEXED_SUBGRAPH_REPLACEMENT, RING_REPLACEMENT, LINKER_REPLACEMENT,
        BOUNDED_RING_CLOSURE, BOUNDED_RING_OPENING, SCAFFOLD_CORE_REPLACEMENT }
    public enum StereoDisposition { PRESERVE_UNAFFECTED, EXPLICITLY_REPLACED, ENUMERATION_REQUIRED }
    public record Attachment(String retainedParentAtomId,String fragmentAtomId,MolecularGraph.BondOrder order,String bondId) {}
    public record Closure(String firstAtomId,String secondAtomId,MolecularGraph.BondOrder order,String bondId) {}
}
