package totah.lab.athena.ligand.pose;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

public interface PoseInteractionAnalyzer {

    PoseAnalysis analyze(
            Structure receptor,
            Pocket pocket,
            Ligand ligand
    );
}
