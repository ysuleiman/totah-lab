package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.selectivity.MutationCandidate.MutationDirection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Proposes and ranks single-point mutation candidates from a
 * {@link LigandContactAlignment}: at every mapped, non-identical
 * position with a ligand contact on at least one side, both the
 * A&rarr;B and the B&rarr;A single substitution are proposed.
 *
 * <p>Ranking uses interpretable tiers, never a collapsed score:</p>
 * <ul>
 *   <li>tier 1 — direct contact on the source side plus a
 *       charge or aromatic gain/loss;</li>
 *   <li>tier 2 — direct contact on the source side plus a moderate
 *       (non-conservative, non-gain/loss) chemistry change;</li>
 *   <li>tier 3 — conservative substitution or no direct contact on
 *       the source side.</li>
 * </ul>
 *
 * <p>Ordering is tier ascending, then alignment position, then
 * direction (A&rarr;B before B&rarr;A): fully deterministic. These
 * are mutation candidates; the ranker never claims a position is a
 * selectivity determinant.
 */
public final class MutationCandidateRanker {

    public List<MutationCandidate> rank(
            LigandContactAlignment alignment
    ) {
        Objects.requireNonNull(alignment, "alignment");

        List<MutationCandidate> candidates = new ArrayList<>();

        for (AlignedLigandContact row : alignment.contacts()) {
            if (row.differentialType()
                    == DifferentialContactType.UNMAPPED
                    || row.substitutionClass()
                            == SubstitutionClass.IDENTICAL
                    || (!row.contactA() && !row.contactB())) {
                continue;
            }

            SubstitutionChemistry chemistry =
                    SubstitutionChemistry.between(
                            row.residueA(),
                            row.residueB()
                    );

            candidates.add(new MutationCandidate(
                    "A",
                    MutationDirection.A_TO_B,
                    row.alignmentPosition(),
                    row.residueAId().residueNumber(),
                    row.residueA(),
                    row.residueB(),
                    row.contactA(),
                    row.contactB(),
                    row.minDistanceA(),
                    row.pocketMemberA(),
                    chemistry,
                    tier(row.contactA(), chemistry)
            ));

            candidates.add(new MutationCandidate(
                    "B",
                    MutationDirection.B_TO_A,
                    row.alignmentPosition(),
                    row.residueBId().residueNumber(),
                    row.residueB(),
                    row.residueA(),
                    row.contactB(),
                    row.contactA(),
                    row.minDistanceB(),
                    row.pocketMemberB(),
                    chemistry,
                    tier(row.contactB(), chemistry)
            ));
        }

        candidates.sort(Comparator
                .comparingInt(MutationCandidate::tier)
                .thenComparingInt(MutationCandidate::alignmentPosition)
                .thenComparing(candidate -> candidate.direction()
                        == MutationDirection.A_TO_B ? 0 : 1));

        return List.copyOf(candidates);
    }

    private static int tier(
            boolean contactOnSource,
            SubstitutionChemistry chemistry
    ) {
        if (contactOnSource
                && (chemistry.chargeGainLoss()
                || chemistry.aromaticGainLoss())) {
            return 1;
        }

        if (contactOnSource && !chemistry.conservative()) {
            return 2;
        }

        return 3;
    }
}
