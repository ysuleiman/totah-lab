package totah.lab.hephaestus.topology;

import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.receptor.residue.ResidueState;
import java.util.Map;

public interface TopologyBuilder {

    BuildResult build(
            Structure structure,
            Map<String, ResidueState> residueStates);

    record BuildResult(
            ProteinTopology topology,
            TopologyBuildReport report) {
    }
}
