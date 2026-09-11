package totah.lab.athena.design.backend;

import java.util.List;
import java.util.Map;

public interface SubstructureMatcher {
    Result match(String query, MolecularGraph graph) throws MolecularBackendException;
    record Result(List<Map<String, String>> queryToTargetAtomIds, BackendEvidence evidence) {
        public Result { queryToTargetAtomIds = queryToTargetAtomIds.stream().map(Map::copyOf).toList(); }
    }
}
