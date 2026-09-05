package totah.lab.athena.interaction;

import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable result of an {@link InteractionProfiler} run.
 *
 * @param interactions refined interactions (PLIP precedence graph
 *                     applied via {@link InteractionRefinements});
 *                     receptor-side records first, then cofactor-side
 *                     records when a separate cofactor was profiled
 * @param rawInteractions unrefined detector output in detector pipeline
 *                        order (salt bridges, hydrogen bonds, pi-stacks,
 *                        pi-cations, hydrophobic contacts, halogen
 *                        bonds), for diagnostics
 * @param cofactorResidues residues of the separately passed cofactor
 *                         structure; empty when no cofactor was given or
 *                         the cofactor was merged into the profiled
 *                         structure (then its residues appear as ordinary
 *                         environment residues and the caller
 *                         distinguishes them by identity, e.g. residue
 *                         name SAM)
 * @param thresholds the threshold set every record was produced with
 * @param perception one {@link PerceptionSummary} per perceived side
 *                   (receptor, ligand, and cofactor when present)
 */
public record InteractionProfile(
        List<Interaction> interactions,
        List<Interaction> rawInteractions,
        Set<ResidueId> cofactorResidues,
        InteractionThresholds thresholds,
        List<PerceptionSummary> perception) {

    public InteractionProfile {
        interactions = List.copyOf(
                Objects.requireNonNull(interactions, "interactions"));
        rawInteractions = List.copyOf(
                Objects.requireNonNull(rawInteractions, "rawInteractions"));
        cofactorResidues = Set.copyOf(
                Objects.requireNonNull(cofactorResidues, "cofactorResidues"));
        Objects.requireNonNull(thresholds, "thresholds");
        perception = List.copyOf(
                Objects.requireNonNull(perception, "perception"));
        if (perception.isEmpty()) {
            throw new IllegalArgumentException(
                    "perception must contain at least one summary");
        }
    }

    /** Returns the refined interactions of the given type. */
    public List<Interaction> interactions(InteractionType type) {
        Objects.requireNonNull(type, "type");
        return interactions.stream()
                .filter(interaction -> interaction.type() == type)
                .toList();
    }

    /**
     * Returns {@code true} when any side's perception used a degraded
     * fallback; results should then be read with the corresponding
     * provenance caveats.
     */
    public boolean anyPerceptionDegraded() {
        return perception.stream().anyMatch(PerceptionSummary::degraded);
    }
}
