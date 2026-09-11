package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Canonizer;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.feature.StableAtomOrbitService;

import java.util.LinkedHashMap;

/** OpenChemLib canonical symmetry-rank adapter; source atom names never enter an orbit identity. */
public final class OclStableAtomOrbitService implements StableAtomOrbitService {
    private final OclGraphMapper mapper = new OclGraphMapper();

    @Override public Result canonicalOrbits(MolecularGraph graph) throws MolecularBackendException {
        try {
            var mapping = mapper.toOcl(graph);
            var molecule = mapping.molecule();
            Canonizer canonizer = new Canonizer(molecule, Canonizer.CREATE_SYMMETRY_RANK);
            var result = new LinkedHashMap<String, String>();
            for (int atom = 0; atom < molecule.getAllAtoms(); atom++) {
                String sourceId = mapping.idByMapNumber().get(molecule.getAtomMapNo(atom));
                if (sourceId == null) throw new MolecularBackendException("canonical orbit lost source atom identity");
                if (atom < molecule.getAtoms()) {
                    result.put(sourceId, "OCL_SYMMETRY_ORBIT:" + canonizer.getSymmetryRank(atom));
                } else {
                    if (molecule.getAllConnAtoms(atom) != 1) {
                        throw new MolecularBackendException("explicit hydrogen lacks exactly one parent");
                    }
                    int parent = molecule.getConnAtom(atom, 0);
                    result.put(sourceId, "OCL_SYMMETRY_ORBIT:H@"
                            + canonizer.getSymmetryRank(parent));
                }
            }
            return new Result(result, "OpenChemLib " + OclMolecularBackend.VERSION
                    + "; Canonizer symmetry ranks; idcode=" + canonizer.getIDCode());
        } catch (MolecularBackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MolecularBackendException("OCL canonical atom orbit perception failed", exception);
        }
    }
}
