package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;

public interface SubstructureMatcher {
    Result match(String query, MolecularGraph graph) throws MolecularBackendException;
    /** Explicit opt-in format; existing implementations retain their historical SMARTS behavior. */
    default Result match(String queryFormat, String query, MolecularGraph graph) throws MolecularBackendException {
        if ("ATHENA_SMARTS_ENVELOPE/1".equals(queryFormat)) return match(query, graph);
        throw new MolecularBackendException("unsupported query format: " + queryFormat);
    }

    record Result(List<Map<String, String>> queryToTargetAtomIds, BackendEvidence evidence) {
        public Result { queryToTargetAtomIds = queryToTargetAtomIds.stream().map(Map::copyOf).toList(); }
    }
}
