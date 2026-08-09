package totah.lab.athena.ligand.pose;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;

public interface PocketPoseAnalyzer {

    PocketPose analyze(
            Pocket pocket,
            Ligand ligand
    );
}
