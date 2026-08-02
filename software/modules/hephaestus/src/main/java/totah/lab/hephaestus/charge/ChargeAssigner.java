package totah.lab.hephaestus.charge;

import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import totah.lab.hephaestus.topology.ProteinTopology;

import java.util.Map;

public interface ChargeAssigner {
    AssignmentResult assign(
            Structure structure,
            ProteinTopology topology,
            Map<String, ResidueState> residueStates);

    record AssignmentResult(
            Structure structure,
            ChargeAssignment assignment,
            ChargeAssignmentReport report) {
    }
}
