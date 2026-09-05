package totah.lab.athena.interaction;

import totah.lab.gaia.structure.ResidueId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable per-residue typed fingerprint of an {@link InteractionProfile}:
 * each residue maps to the set of interaction types it participates in.
 * The underlying {@link Interaction} records stay fully accessible —
 * nothing is collapsed, weighted, or scored.
 *
 * <p>Residue iteration order is the order of first appearance in the
 * profile, which follows structure traversal order. Comparisons are
 * explicit Jaccard helpers only; there is deliberately no combined
 * "master score".
 */
public final class InteractionFingerprint {

    private final Map<ResidueId, Set<InteractionType>> byResidue;
    private final List<Interaction> interactions;

    private InteractionFingerprint(
            Map<ResidueId, Set<InteractionType>> byResidue,
            List<Interaction> interactions) {

        this.byResidue = byResidue;
        this.interactions = interactions;
    }

    /** Builds a fingerprint over the profile's refined interactions. */
    public static InteractionFingerprint of(InteractionProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return of(profile.interactions());
    }

    /** Builds a fingerprint over the given interactions. */
    public static InteractionFingerprint of(List<Interaction> interactions) {
        Objects.requireNonNull(interactions, "interactions");
        Map<ResidueId, Set<InteractionType>> map = new LinkedHashMap<>();
        for (Interaction interaction : interactions) {
            map.computeIfAbsent(interaction.residue(),
                    residue -> new LinkedHashSet<>())
                    .add(interaction.type());
        }
        Map<ResidueId, Set<InteractionType>> immutable =
                new LinkedHashMap<>();
        map.forEach((residue, types) ->
                immutable.put(residue, Set.copyOf(types)));
        return new InteractionFingerprint(
                Collections.unmodifiableMap(immutable),
                List.copyOf(interactions));
    }

    /** Residue-to-types view, in first-appearance order. */
    public Map<ResidueId, Set<InteractionType>> byResidue() {
        return byResidue;
    }

    /** The underlying interaction records, unfiltered. */
    public List<Interaction> interactions() {
        return interactions;
    }

    /** The underlying interaction records of one residue. */
    public List<Interaction> interactionsOf(ResidueId residue) {
        Objects.requireNonNull(residue, "residue");
        return interactions.stream()
                .filter(interaction -> interaction.residue().equals(residue))
                .toList();
    }

    /**
     * Jaccard similarity over typed {@code (residue, type)} pairs. Two
     * empty fingerprints are considered identical (1.0).
     */
    public double typedJaccard(InteractionFingerprint other) {
        Objects.requireNonNull(other, "other");
        Set<ResidueTypePair> mine = typedPairs();
        Set<ResidueTypePair> theirs = other.typedPairs();
        return jaccard(mine, theirs);
    }

    /**
     * Jaccard similarity over participating residues only, ignoring
     * interaction types. Two empty fingerprints are considered identical
     * (1.0).
     */
    public double residueJaccard(InteractionFingerprint other) {
        Objects.requireNonNull(other, "other");
        return jaccard(byResidue.keySet(), other.byResidue.keySet());
    }

    private Set<ResidueTypePair> typedPairs() {
        Set<ResidueTypePair> pairs = new LinkedHashSet<>();
        byResidue.forEach((residue, types) -> types.forEach(type ->
                pairs.add(new ResidueTypePair(residue, type))));
        return pairs;
    }

    private static <T> double jaccard(Set<T> first, Set<T> second) {
        if (first.isEmpty() && second.isEmpty()) {
            return 1.0;
        }
        Set<T> intersection = new LinkedHashSet<>(first);
        intersection.retainAll(second);
        Set<T> union = new LinkedHashSet<>(first);
        union.addAll(second);
        return (double) intersection.size() / union.size();
    }

    private record ResidueTypePair(ResidueId residue, InteractionType type) {
    }
}
