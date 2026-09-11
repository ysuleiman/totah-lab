package totah.lab.athena.design.backend;

import java.util.Set;

public interface MolecularSanitizer {
    Result sanitize(MolecularGraph graph, SanitizationPolicy policy) throws MolecularBackendException;
    record SanitizationPolicy(Set<String> allowedBenignNormalizations, boolean failOnMeaningfulChange) {
        public SanitizationPolicy { allowedBenignNormalizations = Set.copyOf(allowedBenignNormalizations); }
    }
    record Result(MolecularGraph graph, boolean valid, BackendEvidence evidence) { }
}
