package totah.lab.athena.design.feature;

import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;

import java.util.Map;

/** Chemistry-backend boundary for atom-name-independent canonical symmetry orbits. */
public interface StableAtomOrbitService {
    Result canonicalOrbits(MolecularGraph graph) throws MolecularBackendException;
    record Result(Map<String, String> sourceAtomToCanonicalOrbit, String provenance) {
        public Result { sourceAtomToCanonicalOrbit = Map.copyOf(sourceAtomToCanonicalOrbit); }
    }
}
