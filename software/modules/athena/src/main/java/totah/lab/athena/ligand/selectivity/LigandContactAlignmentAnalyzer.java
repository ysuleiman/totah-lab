package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.List;

/**
 * Aligns the ligand-contact landscapes of one ligand docked against
 * two receptors: the residues are aligned by protein sequence, and
 * each side's ligand contacts, distances, pocket membership and
 * chemistry deltas are laid onto the aligned positions.
 */
public interface LigandContactAlignmentAnalyzer {

    /**
     * Builds the aligned contact table. Contacts are passed in
     * pre-computed (the caller runs a {@code ContactAnalyzer} once and
     * reuses the result). {@code pocketA}/{@code pocketB} may be
     * {@code null}; pocket membership and structural equivalence are
     * then reported as {@code null}. The poses are accepted for
     * context and validation; the contact lists are authoritative.
     */
    LigandContactAlignment align(
            Structure receptorA,
            Ligand poseA,
            List<LigandContact> contactsA,
            Structure receptorB,
            Ligand poseB,
            List<LigandContact> contactsB,
            Pocket pocketA,
            Pocket pocketB
    );
}
