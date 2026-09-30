package totah.lab.athena.design.generation;

import totah.lab.athena.design.grammar.DesignGrammar;

import java.util.List;

/** Chemistry backend used by the generic grammar orchestrator. */
public interface MolecularTransformationBackend {
    List<TransformationProduct> apply(
            String parentRepresentation,
            DesignGrammar.EditableVector vector,
            String transformationClass,
            String substituentClass);

    record TransformationProduct(String canonicalRepresentation,
                                 String exactEdit,
                                 List<String> provenance) {
        public TransformationProduct { provenance = List.copyOf(provenance); }
    }
}
