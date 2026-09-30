package totah.lab.athena.energy.openmm;

import totah.lab.athena.energy.EnergyEvaluationException;
import totah.lab.athena.energy.MolecularState;

import java.util.Map;

/** External OpenMM execution boundary; Athena does not reimplement a force field. */
public interface OpenMmBackend {
    BackendResult evaluate(MolecularState state, OpenMmForceGroupMap forceGroups)
            throws EnergyEvaluationException;

    record BackendResult(Map<Integer, Double> forceGroupKilojoulesPerMole,
            double totalKilojoulesPerMole, String openMmVersion, String platform,
            Map<String, String> platformProperties, String systemBuilderIdentity,
            Map<String, String> artifactHashes, boolean minimized,
            boolean converged) {
        public BackendResult {
            forceGroupKilojoulesPerMole = Map.copyOf(forceGroupKilojoulesPerMole);
            platformProperties = Map.copyOf(platformProperties);
            artifactHashes = Map.copyOf(artifactHashes);
        }
    }
}
