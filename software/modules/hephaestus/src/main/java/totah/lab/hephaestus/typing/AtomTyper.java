package totah.lab.hephaestus.typing;

import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.Map;

public interface AtomTyper {
    TypingResult assign(
            Structure structure,
            ProteinTopology topology,
            Map<String, ResidueState> residueStates);

    record TypingResult(
            Structure structure,
            AtomTypeAssignment assignment,
            AD4AtomTypingReport report) {
    }
}
