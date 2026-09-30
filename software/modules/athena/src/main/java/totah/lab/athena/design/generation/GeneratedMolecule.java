package totah.lab.athena.design.generation;

import java.util.List;

/** One unique graph proposed by a grammar-driven generation backend. */
public record GeneratedMolecule(String candidateId, String parentId,
                                String canonicalRepresentation,
                                String editableVectorId,
                                String transformationClass,
                                String substituentClass,
                                String exactEdit,
                                boolean explorationCandidate,
                                List<String> provenance) {
    public GeneratedMolecule { provenance = List.copyOf(provenance); }
}
