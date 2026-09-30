package totah.lab.athena.relaxation;

import totah.lab.athena.energy.EnergyEvaluationException;
import totah.lab.athena.energy.MolecularState;
import totah.lab.gaia.structure.Structure;

import java.util.List;
import java.util.Map;

/** External minimizer boundary for bounded relaxation; not MD production. */
public interface LocalRelaxationEngine {
    Result relax(MolecularState realizedState, LocalRelaxationProtocol protocol)
            throws EnergyEvaluationException;

    record Result(Structure relaxedReceptor, Structure relaxedLigand,
            Structure relaxedSam, boolean converged,
            RelaxationEvidence.Displacements displacements,
            List<String> physicalValidityFailures,
            Map<String, String> provenance) {
        public Result {
            physicalValidityFailures = List.copyOf(physicalValidityFailures);
            provenance = Map.copyOf(provenance);
        }
    }
}
