package totah.lab.athena.ligand.pose;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Structure;

import java.util.List;

/**
 * Compares a Vina pose docked against a query protein with a Vina pose
 * docked against a candidate protein: do the predicted poses occupy
 * structurally homologous sites, or genuinely different sites?
 *
 * <p>Correspondence comes from structural pocket alignment only; raw
 * pocket numbers are never compared as evidence.
 */
public interface CrossProteinPoseComparator {

    /**
     * Compares two docked poses. Both poses arrive with their phase-1
     * {@link PosePocketAssignment}: a pose that is not
     * {@link AssignmentStatus#ASSIGNED} cannot support a site verdict
     * and yields {@link PoseSiteRelationship#AMBIGUOUS}.
     */
    CrossProteinPoseComparison compare(
            String queryPoseLabel,
            Structure queryReceptor,
            PosePocketAssignment queryAssignment,
            Ligand queryPose,
            List<LigandContact> queryContacts,
            String candidatePoseLabel,
            Structure candidateReceptor,
            PosePocketAssignment candidateAssignment,
            Ligand candidatePose,
            List<LigandContact> candidateContacts
    );
}
