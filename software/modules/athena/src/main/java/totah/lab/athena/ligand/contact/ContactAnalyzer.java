package totah.lab.athena.ligand.contact;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Structure;

import java.util.List;

public interface ContactAnalyzer {

    List<LigandContact> analyze(
            Structure receptor,
            Ligand ligand
    );
}
