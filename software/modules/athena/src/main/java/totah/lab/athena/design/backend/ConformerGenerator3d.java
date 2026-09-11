package totah.lab.athena.design.backend;

import java.util.List;

public interface ConformerGenerator3d {
    Result generate(MolecularGraph graph, Configuration configuration) throws MolecularBackendException;
    record Configuration(long seed, int maximumConformers, long timeoutMillis,
                         boolean optimizeRigidFragments) {
        public Configuration {
            if (seed == 0L) throw new IllegalArgumentException("A nonzero deterministic seed is required");
            if (maximumConformers < 1 || timeoutMillis < 1) throw new IllegalArgumentException("invalid bounds");
        }
    }
    record Conformer(String id, MolecularGraph graph) { }
    record Result(List<Conformer> conformers, BackendEvidence evidence) {
        public Result { conformers = List.copyOf(conformers); }
    }
}
