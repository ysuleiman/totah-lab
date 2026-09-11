package totah.lab.athena.design.feature;

import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;

import java.util.List;

/** Replaceable chemistry-backend boundary for target-independent ligand feature perception. */
public interface LigandFeaturePerceptionService {
    Result perceive(MolecularGraph conformer) throws MolecularBackendException;
    record Result(List<LigandFeature> features, String backend, String version, List<String> notes) {
        public Result { features = List.copyOf(features); notes = List.copyOf(notes); }
    }
}
