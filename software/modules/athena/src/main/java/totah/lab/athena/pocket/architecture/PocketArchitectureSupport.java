package totah.lab.athena.pocket.architecture;

import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.residue.PocketResiduePointFactory;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.sequence.NeedlemanWunschSequenceAligner;
import totah.lab.athena.sequence.StructureSequences;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/**
 * Shared pocket-alignment wiring for the architecture analyzers:
 * alpha-sphere (or residue-atom) point clouds, residue points and a
 * Needleman-Wunsch sequence alignment feed the multi-hypothesis
 * aligner exactly once per pocket pair.
 */
final class PocketArchitectureSupport {

    private PocketArchitectureSupport() {
    }

    static PocketAlignmentResult alignPockets(
            Structure receptorA,
            Pocket pocketA,
            Structure receptorB,
            Pocket pocketB
    ) {
        Objects.requireNonNull(receptorA, "receptorA");
        Objects.requireNonNull(pocketA, "pocketA");
        Objects.requireNonNull(receptorB, "receptorB");
        Objects.requireNonNull(pocketB, "pocketB");

        PocketResiduePointFactory residuePoints =
                new PocketResiduePointFactory();

        return new MultiHypothesisPocketAligner().align(
                PocketPointCloud.from(receptorA, pocketA),
                PocketPointCloud.from(receptorB, pocketB),
                residuePoints.create(receptorA, pocketA),
                residuePoints.create(receptorB, pocketB),
                new NeedlemanWunschSequenceAligner().align(
                        StructureSequences.sequenceResidues(receptorA),
                        StructureSequences.sequenceResidues(receptorB)
                )
        );
    }
}
