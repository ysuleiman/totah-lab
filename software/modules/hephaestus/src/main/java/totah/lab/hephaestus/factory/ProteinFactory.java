package totah.lab.hephaestus.factory;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;

public class ProteinFactory {

    public ProteinFactory(){}
    public Protein create(String targetId, Structure structure){
        return new Protein(
                targetId,
                null,
                targetId,
                null,
                null,
                null,
                structure);
    }

    public Protein copyWithStructure(
            Protein protein,
            Structure structure) {
        return new Protein(
                protein.id(),
                protein.uniProtId().orElse(null),
                protein.name(),
                protein.gene().orElse(null),
                protein.organism().orElse(null),
                protein.function().orElse(null),
                structure);
    }
}
