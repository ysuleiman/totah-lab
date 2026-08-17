package totah.lab.athena.ligand.screening;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stage-3 scaffold-aware and global ECFP4 diversity reduction.
 * Chemistry is supplied by the caller; this class applies no target hypothesis
 * filters and never merges drug-like and fragment cohort quotas.
 */
public final class LigandDiversitySelector {

    public enum Cohort {
        DRUG_LIKE,
        FRAGMENT
    }

    public record Policy(
            double withinScaffoldCoverage,
            double globalCoverage,
            int rareScaffoldMaximum) {

        public Policy {
            requireSimilarity(withinScaffoldCoverage,
                    "withinScaffoldCoverage");
            requireSimilarity(globalCoverage, "globalCoverage");
            if (rareScaffoldMaximum < 1) {
                throw new IllegalArgumentException(
                        "rareScaffoldMaximum must be positive");
            }
        }

        public static Policy lockedMettl7b() {
            return new Policy(.35, .35, 3);
        }
    }

    public record Candidate(
            String identifier,
            Cohort cohort,
            String canonicalStructure,
            String exactMurckoScaffold,
            Set<Integer> ecfp4Bits,
            boolean lowFsp3PolarHeteroaromatic) {

        public Candidate {
            identifier = requireText(identifier, "identifier");
            Objects.requireNonNull(cohort, "cohort");
            canonicalStructure = requireText(
                    canonicalStructure, "canonicalStructure");
            exactMurckoScaffold = requireText(
                    exactMurckoScaffold, "exactMurckoScaffold");
            ecfp4Bits = Set.copyOf(Objects.requireNonNull(
                    ecfp4Bits, "ecfp4Bits"));
            if (ecfp4Bits.isEmpty()) {
                throw new IllegalArgumentException("ecfp4Bits must not be empty");
            }
            if (ecfp4Bits.stream().anyMatch(bit -> bit < 0)) {
                throw new IllegalArgumentException(
                        "ecfp4Bits must contain non-negative indices");
            }
        }
    }

    public record Representation(
            String discardedIdentifier,
            String representativeIdentifier,
            double ecfp4Tanimoto) {

        public Representation {
            discardedIdentifier = requireText(
                    discardedIdentifier, "discardedIdentifier");
            representativeIdentifier = requireText(
                    representativeIdentifier, "representativeIdentifier");
            requireSimilarity(ecfp4Tanimoto, "ecfp4Tanimoto");
        }
    }

    public record CohortResult(
            Cohort cohort,
            List<Candidate> selected,
            List<Representation> represented,
            Map<String, String> exactDuplicates,
            int uniqueStructures,
            int protectedStructures) {

        public CohortResult {
            Objects.requireNonNull(cohort, "cohort");
            selected = List.copyOf(Objects.requireNonNull(selected, "selected"));
            represented = List.copyOf(Objects.requireNonNull(
                    represented, "represented"));
            exactDuplicates = Map.copyOf(Objects.requireNonNull(
                    exactDuplicates, "exactDuplicates"));
            if (uniqueStructures < 0 || protectedStructures < 0
                    || protectedStructures > uniqueStructures) {
                throw new IllegalArgumentException("invalid structure counts");
            }
        }
    }

    public record Result(CohortResult drugLike, CohortResult fragment) {
        public Result {
            Objects.requireNonNull(drugLike, "drugLike");
            Objects.requireNonNull(fragment, "fragment");
            if (drugLike.cohort() != Cohort.DRUG_LIKE
                    || fragment.cohort() != Cohort.FRAGMENT) {
                throw new IllegalArgumentException("cohort result mismatch");
            }
        }
    }

    private static final Comparator<Candidate> ORDER =
            Comparator.comparing(Candidate::canonicalStructure)
                    .thenComparing(Candidate::identifier);

    private final Policy policy;

    public LigandDiversitySelector() {
        this(Policy.lockedMettl7b());
    }

    public LigandDiversitySelector(Policy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Result select(List<Candidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        return new Result(
                select(Cohort.DRUG_LIKE, candidates),
                select(Cohort.FRAGMENT, candidates));
    }

    private CohortResult select(Cohort cohort, List<Candidate> source) {
        Map<String, Candidate> unique = new LinkedHashMap<>();
        Map<String, String> duplicates = new LinkedHashMap<>();
        source.stream()
                .filter(candidate -> candidate.cohort() == cohort)
                .sorted(ORDER)
                .forEach(candidate -> {
                    Candidate retained = unique.putIfAbsent(
                            candidate.canonicalStructure(), candidate);
                    if (retained != null) {
                        duplicates.put(candidate.identifier(), retained.identifier());
                    }
                });
        List<Candidate> values = List.copyOf(unique.values());
        Map<String, List<Candidate>> scaffolds = new LinkedHashMap<>();
        for (Candidate candidate : values) {
            scaffolds.computeIfAbsent(candidate.exactMurckoScaffold(),
                    ignored -> new ArrayList<>()).add(candidate);
        }
        Set<Candidate> protectedCandidates = new LinkedHashSet<>();
        for (List<Candidate> family : scaffolds.values()) {
            if (family.size() <= policy.rareScaffoldMaximum()) {
                protectedCandidates.addAll(family);
            }
        }
        if (cohort == Cohort.FRAGMENT) {
            values.stream()
                    .filter(Candidate::lowFsp3PolarHeteroaromatic)
                    .forEach(protectedCandidates::add);
        }

        Set<Candidate> scaffoldReduced = new LinkedHashSet<>();
        for (List<Candidate> family : scaffolds.values()) {
            List<Candidate> seeds = family.stream()
                    .filter(protectedCandidates::contains).toList();
            scaffoldReduced.addAll(cover(family, seeds,
                    policy.withinScaffoldCoverage()));
        }
        List<Candidate> globalSeeds = scaffoldReduced.stream()
                .filter(protectedCandidates::contains).toList();
        List<Candidate> global = cover(
                List.copyOf(scaffoldReduced), globalSeeds,
                policy.globalCoverage());
        // Enforce the coverage statement against every Stage-2 survivor, not
        // merely the intermediate scaffold representatives.
        List<Candidate> selected = cover(values, global, policy.globalCoverage());
        Set<Candidate> selectedSet = Set.copyOf(selected);
        List<Representation> represented = values.stream()
                .filter(candidate -> !selectedSet.contains(candidate))
                .map(candidate -> representation(candidate, selected))
                .sorted(Comparator.comparing(
                        Representation::discardedIdentifier))
                .toList();
        return new CohortResult(cohort,
                selected.stream().sorted(ORDER).toList(), represented,
                duplicates, values.size(), protectedCandidates.size());
    }

    private static List<Candidate> cover(
            List<Candidate> population,
            List<Candidate> seeds,
            double threshold) {
        if (population.isEmpty()) {
            return List.of();
        }
        Set<Candidate> populationSet = Set.copyOf(population);
        LinkedHashSet<Candidate> selected = new LinkedHashSet<>();
        seeds.stream().filter(populationSet::contains).sorted(ORDER)
                .forEach(selected::add);
        if (selected.isEmpty()) {
            selected.add(population.stream().min(ORDER).orElseThrow());
        }
        List<Candidate> remaining = population.stream()
                .filter(candidate -> !selected.contains(candidate))
                .sorted(ORDER).collect(java.util.stream.Collectors.toCollection(
                        ArrayList::new));
        Map<Candidate, Double> nearest = new HashMap<>();
        for (Candidate candidate : remaining) {
            nearest.put(candidate, maximumSimilarity(candidate, selected));
        }
        while (!remaining.isEmpty()) {
            Candidate leastCovered = remaining.stream()
                    .min(Comparator.comparingDouble(
                                    (Candidate candidate) -> nearest.get(candidate))
                            .thenComparing(ORDER))
                    .orElseThrow();
            if (nearest.get(leastCovered) >= threshold) {
                break;
            }
            selected.add(leastCovered);
            remaining.remove(leastCovered);
            for (Candidate candidate : remaining) {
                nearest.compute(candidate, (ignored, previous) -> Math.max(
                        previous, tanimoto(candidate, leastCovered)));
            }
        }
        return List.copyOf(selected);
    }

    private static Representation representation(
            Candidate discarded, List<Candidate> selected) {
        Candidate representative = selected.stream()
                .max(Comparator.comparingDouble(
                                (Candidate candidate) -> tanimoto(
                                        discarded, candidate))
                        .thenComparing(ORDER.reversed()))
                .orElseThrow();
        return new Representation(discarded.identifier(),
                representative.identifier(), tanimoto(discarded, representative));
    }

    private static double maximumSimilarity(
            Candidate candidate, Set<Candidate> selected) {
        return selected.stream().mapToDouble(value -> tanimoto(candidate, value))
                .max().orElse(0.0);
    }

    private static double tanimoto(Candidate left, Candidate right) {
        Set<Integer> intersection = new HashSet<>(left.ecfp4Bits());
        intersection.retainAll(right.ecfp4Bits());
        int union = left.ecfp4Bits().size() + right.ecfp4Bits().size()
                - intersection.size();
        return union == 0 ? 1.0 : (double) intersection.size() / union;
    }

    private static void requireSimilarity(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
