package totah.lab.athena.design.backend;

public interface ConformerMinimizer {
    Result minimize(MolecularGraph conformer, Configuration configuration) throws MolecularBackendException;
    record Configuration(String forceField, int maximumIterations,
                         double gradientTolerance, double functionTolerance) {
        public Configuration {
            if (forceField == null || forceField.isBlank() || maximumIterations < 1) {
                throw new IllegalArgumentException("invalid minimization configuration");
            }
        }
    }
    record Result(MolecularGraph graph, boolean converged, double energy,
                  BackendEvidence evidence) { }
}
