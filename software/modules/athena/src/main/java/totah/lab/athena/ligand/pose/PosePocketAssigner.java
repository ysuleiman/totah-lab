package totah.lab.athena.ligand.pose;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.List;

/**
 * Assigns one predicted Vina pose to the best-matching candidate
 * pocket, or reports it as ambiguous / not assigned when the evidence
 * does not support a match.
 */
public interface PosePocketAssigner {

    /**
     * Assigns {@code pose} using pre-computed contacts. The caller runs
     * a {@code ContactAnalyzer} once and reuses the result for both the
     * contact table and the assignment, so no work is duplicated.
     */
    PosePocketAssignment assign(
            Structure receptor,
            List<Pocket> candidatePockets,
            Ligand pose,
            List<LigandContact> contacts
    );

    /**
     * Assigns {@code pose}, computing the contacts internally with the
     * assigner's configured {@code ContactAnalyzer}.
     */
    PosePocketAssignment assign(
            Structure receptor,
            List<Pocket> candidatePockets,
            Ligand pose
    );
}
