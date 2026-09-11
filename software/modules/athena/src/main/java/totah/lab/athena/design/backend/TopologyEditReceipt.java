package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete replay receipt for a topology-changing transaction. */
public record TopologyEditReceipt(String editId, String transformation,
        String parentCanonicalIdentity, String childCanonicalIdentity,
        Map<String,String> parentToChildAtomLineage, Map<String,String> parentToChildBondLineage,
        Map<String,String> attachmentPointMapping, Set<String> addedAtomIds, Set<String> deletedAtomIds,
        Set<String> addedBondIds, Set<String> deletedBondIds,
        TopologyEdit.StereoDisposition stereoDisposition, List<String> validations) {
    public TopologyEditReceipt { parentToChildAtomLineage=Map.copyOf(parentToChildAtomLineage);
        parentToChildBondLineage=Map.copyOf(parentToChildBondLineage);attachmentPointMapping=Map.copyOf(attachmentPointMapping);
        addedAtomIds=Set.copyOf(addedAtomIds);deletedAtomIds=Set.copyOf(deletedAtomIds);
        addedBondIds=Set.copyOf(addedBondIds);deletedBondIds=Set.copyOf(deletedBondIds);validations=List.copyOf(validations); }
}
