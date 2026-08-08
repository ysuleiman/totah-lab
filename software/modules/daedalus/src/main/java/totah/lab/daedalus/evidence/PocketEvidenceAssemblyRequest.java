package totah.lab.daedalus.evidence;

import totah.lab.athena.pocket.evidence.ComponentChemistryEvidence;
import totah.lab.athena.pocket.evidence.EvidenceChannel;
import totah.lab.athena.pocket.evidence.EvidenceMethod;
import totah.lab.athena.pocket.evidence.EvidenceOrigin;
import totah.lab.athena.pocket.evidence.EvidenceProvenance;
import totah.lab.athena.pocket.evidence.StructureEvidence;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All source facts and already-computed optional channels for one assembly. */
public record PocketEvidenceAssemblyRequest(
        StructureEvidence structureEvidence,
        Structure structure,
        Pocket pocket,
        EvidenceChannel<List<BoundComponentOccurrence>> boundComponents,
        EvidenceChannel<Map<String, ComponentChemistryEvidence>> chemistry,
        EvidenceChannel<Map<String, Double>> shapeRepresentation,
        EvidenceMethod fpocketMethod,
        EvidenceMethod extractionMethod,
        EvidenceProvenance provenance
) {
    public PocketEvidenceAssemblyRequest {
        Objects.requireNonNull(structureEvidence, "structureEvidence");
        Objects.requireNonNull(structure, "structure");
        Objects.requireNonNull(pocket, "pocket");
        Objects.requireNonNull(boundComponents, "boundComponents");
        Objects.requireNonNull(chemistry, "chemistry");
        Objects.requireNonNull(shapeRepresentation, "shapeRepresentation");
        Objects.requireNonNull(fpocketMethod, "fpocketMethod");
        Objects.requireNonNull(extractionMethod, "extractionMethod");
        Objects.requireNonNull(provenance, "provenance");
        EvidenceChannel.requireOrigin(boundComponents,
                EvidenceOrigin.SOURCE_OBSERVED, "boundComponents");
        EvidenceChannel.requireOrigin(chemistry,
                EvidenceOrigin.SOURCE_REPORTED, "chemistry");
        EvidenceChannel.requireOrigin(shapeRepresentation,
                EvidenceOrigin.DERIVED, "shapeRepresentation");
    }
}
