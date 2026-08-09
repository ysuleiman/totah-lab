package totah.lab.athena.ligand.selectivity;

import org.junit.jupiter.api.Test;
import totah.lab.athena.ligand.selectivity.MutationCandidate.MutationDirection;
import totah.lab.gaia.structure.Structure;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.contact;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.pose;
import static totah.lab.athena.ligand.selectivity.DefaultLigandContactAlignmentAnalyzerTest.receptor;

class MutationCandidateRankerTest {

    private final DefaultLigandContactAlignmentAnalyzer analyzer =
            new DefaultLigandContactAlignmentAnalyzer();
    private final MutationCandidateRanker ranker =
            new MutationCandidateRanker();

    @Test
    void ranksByInterpretableTiers() {
        // F->L aromatic loss (tier 1), R->V charge loss (tier 1),
        // L->M conservative (tier 3), T->L polar-hydrophobic swap
        // (tier 2).
        Structure receptorA =
                receptor(10, "PHE", "ARG", "LEU", "THR", "SER");
        Structure receptorB =
                receptor(10, "LEU", "VAL", "MET", "LEU", "SER");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(),
                List.of(contact(10), contact(11), contact(12),
                        contact(13)),
                receptorB, pose(),
                List.of(contact(10), contact(11), contact(12),
                        contact(13)),
                null, null
        );

        List<MutationCandidate> aToB = ranker.rank(alignment).stream()
                .filter(candidate -> candidate.direction()
                        == MutationDirection.A_TO_B)
                .toList();

        assertThat(aToB.stream().map(MutationCandidate::label))
                .containsExactly("F10L", "R11V", "T13L", "L12M");
        assertThat(aToB.stream().map(MutationCandidate::tier))
                .containsExactly(1, 1, 2, 3);

        MutationCandidate first = aToB.get(0);
        assertThat(first.wildType()).isEqualTo("PHE");
        assertThat(first.mutant()).isEqualTo("LEU");
        assertThat(first.residueNumber()).isEqualTo(10);
        assertThat(first.contactOnSource()).isTrue();
        assertThat(first.chemistry().aromaticGainLoss()).isTrue();
        assertThat(first.minDistance()).isEqualTo(3.0);
    }

    @Test
    void proposesBothDirectionsPerDifferentialContactPosition() {
        Structure receptorA = receptor(10, "PHE");
        Structure receptorB = receptor(20, "LEU");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(10)),
                receptorB, pose(), List.of(contact(20)),
                null, null
        );

        List<MutationCandidate> candidates = ranker.rank(alignment);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).direction())
                .isEqualTo(MutationDirection.A_TO_B);
        assertThat(candidates.get(0).label()).isEqualTo("F10L");
        assertThat(candidates.get(1).direction())
                .isEqualTo(MutationDirection.B_TO_A);
        assertThat(candidates.get(1).label()).isEqualTo("L20F");
    }

    @Test
    void skipsIdenticalAndNonContactPositions() {
        // Only position 2 (PHE/LEU, contacts) yields candidates;
        // the conserved SER and the non-contact ARG/VAL difference
        // yield none.
        Structure receptorA = receptor(10, "SER", "PHE", "ARG");
        Structure receptorB = receptor(10, "SER", "LEU", "VAL");

        LigandContactAlignment alignment = analyzer.align(
                receptorA, pose(), List.of(contact(11)),
                receptorB, pose(), List.of(contact(11)),
                null, null
        );

        assertThat(ranker.rank(alignment)).hasSize(2);
        assertThat(ranker.rank(alignment).stream()
                .map(MutationCandidate::label))
                .containsExactlyInAnyOrder("F11L", "L11F");
    }
}
