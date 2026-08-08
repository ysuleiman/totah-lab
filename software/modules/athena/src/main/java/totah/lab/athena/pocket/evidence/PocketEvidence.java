package totah.lab.athena.pocket.evidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Interpretation-ready facts and derived channels for one pocket. This is
 * evidence, not an assessment, and intentionally contains no master score.
 */
public record PocketEvidence(
        StructureEvidence structure,
        PocketGeometryEvidence pocket,
        ResidueContextEvidence residueContext,
        EvidenceChannel<List<LigandOccurrenceEvidence>> ligandEvidence,
        EvidenceChannel<Map<String, ComponentChemistryEvidence>> chemistry,
        EvidenceProvenance provenance
) {
    public PocketEvidence {
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(residueContext, "residueContext");
        Objects.requireNonNull(ligandEvidence, "ligandEvidence");
        Objects.requireNonNull(chemistry, "chemistry");
        Objects.requireNonNull(provenance, "provenance");
        EvidenceChannel.requireOrigin(ligandEvidence,
                EvidenceOrigin.SOURCE_OBSERVED, "ligandEvidence");
        EvidenceChannel.requireOrigin(chemistry,
                EvidenceOrigin.SOURCE_REPORTED, "chemistry");
        validateChemistryKeys(chemistry);
    }

    private static void validateChemistryKeys(
            EvidenceChannel<Map<String, ComponentChemistryEvidence>> channel) {
        if (!(channel instanceof EvidenceChannel.Present<Map<String,
                ComponentChemistryEvidence>> present)) {
            return;
        }
        Map<String, ComponentChemistryEvidence> chemistry = present.value();
        for (Map.Entry<String, ComponentChemistryEvidence> entry
                : chemistry.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().componentId())) {
                throw new IllegalArgumentException(
                        "Chemistry map key must equal its component ID");
            }
        }
    }

    /** Creates an immutable, component-ID-sorted chemistry map. */
    public static Map<String, ComponentChemistryEvidence> chemistryMap(
            Map<String, ComponentChemistryEvidence> values) {
        return Map.copyOf(new TreeMap<>(Objects.requireNonNull(values, "values")));
    }
}
