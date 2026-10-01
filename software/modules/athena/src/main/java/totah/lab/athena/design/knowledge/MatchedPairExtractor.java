package totah.lab.athena.design.knowledge;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import totah.lab.athena.design.backend.CanonicalIdentityService;
import totah.lab.athena.design.backend.MolecularGraph;

/** Structural observations only: neither a design state, an effect claim nor an edit authorization. */
public interface MatchedPairExtractor {
    Extraction extract(List<Source> sources, int maximumVariableAtoms);

    record Source(String id, String dataset, MolecularGraph graph, List<String> observationReferences) {
        public Source {
            require(id); require(dataset); Objects.requireNonNull(graph);
            observationReferences = List.copyOf(observationReferences);
            observationReferences.forEach(MatchedPairExtractor::require);
        }
    }
    record Fragment(String constant, String variable, String cutBond, String constantAnchor,
                    String variableAnchor, Set<String> constantAtoms, Set<String> variableAtoms,
                    String chemicalContext) {
        public Fragment {
            require(constant); require(variable); require(cutBond); require(constantAnchor);
            require(variableAnchor); require(chemicalContext);
            constantAtoms = Set.copyOf(constantAtoms); variableAtoms = Set.copyOf(variableAtoms);
            if (constantAtoms.isEmpty() || variableAtoms.isEmpty()
                    || !constantAtoms.contains(constantAnchor) || !variableAtoms.contains(variableAnchor)
                    || constantAtoms.stream().anyMatch(variableAtoms::contains))
                throw new IllegalArgumentException("invalid fragment partition");
        }
    }
    /** Each cut occurrence remains separate; equivalent sites are not silently collapsed. */
    record Pair(Source left, Source right, String leftIdentity, String rightIdentity,
                Fragment leftFragment, Fragment rightFragment,
                CanonicalIdentityService.Correspondence coreCorrespondence, String algorithm) {
        public Pair {
            Objects.requireNonNull(left); Objects.requireNonNull(right);
            Objects.requireNonNull(leftFragment); Objects.requireNonNull(rightFragment);
            Objects.requireNonNull(coreCorrespondence); require(algorithm);
            require(leftIdentity); require(rightIdentity);
            if (leftIdentity.equals(rightIdentity) || !leftFragment.constant().equals(rightFragment.constant())
                    || leftFragment.variable().equals(rightFragment.variable())
                    || !coreCorrespondence.exhaustive() || coreCorrespondence.alternatives().isEmpty())
                throw new IllegalArgumentException("unproved or identical matched pair");
        }
        public String transformation() { return leftFragment.variable() + ">>" + rightFragment.variable(); }
    }
    record Issue(String source, String reason) { public Issue { require(source); require(reason); } }
    record Extraction(List<Pair> pairs, List<Issue> issues, String algorithm) {
        public Extraction { pairs = List.copyOf(pairs); issues = List.copyOf(issues); require(algorithm); }
    }
    private static void require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("nonblank reference required");
    }
}
