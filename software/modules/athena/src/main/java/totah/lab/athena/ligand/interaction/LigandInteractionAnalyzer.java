package totah.lab.athena.ligand.interaction;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Structure;

import java.util.List;

public interface LigandInteractionAnalyzer {

    List<LigandInteraction> analyze(Structure receptor, Ligand ligand);
}
