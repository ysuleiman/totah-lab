package totah.lab.athena.sequence;

import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Objects;

/**
 * Maps a gaia {@link Structure} onto the {@link SequenceResidue} view
 * used by the sequence aligners: every residue of every chain, in
 * chain/structure order. Shared by all alignment-driven analyses so
 * the structure-to-sequence mapping exists exactly once.
 */
public final class StructureSequences {

    private StructureSequences() {
    }

    public static List<SequenceResidue> sequenceResidues(
            Structure structure
    ) {
        Objects.requireNonNull(structure, "structure");

        return structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .map(residue -> new SequenceResidue(
                        residue.getNumber(),
                        residue.getName()
                ))
                .toList();
    }
}
