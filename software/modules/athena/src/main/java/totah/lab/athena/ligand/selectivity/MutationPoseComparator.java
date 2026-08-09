package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.contact.LigandContact;
import totah.lab.athena.ligand.pose.PosePocketAssignment;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Structure;

import java.util.List;

/**
 * Compares a mutant receptor's docked pose with a wild-type reference
 * pose of the same ligand.
 */
public interface MutationPoseComparator {

    /**
     * Same-frame comparison: the mutant receptor was built on the
     * wild-type coordinate frame, so NO transform is applied — frame
     * identity between {@code wtReceptor}/{@code wtPose} and
     * {@code mutantReceptor}/{@code mutantPose} is a caller contract
     * and is not verified here.
     *
     * <p>Contacts are passed in pre-computed. {@code confidenceBefore}/
     * {@code confidenceAfter} are caller-supplied docking confidences,
     * carried as data only. When the two poses differ in heavy-atom
     * count the atom correspondence is invalid and the RMSD (and the
     * ligand rotation angle) are reported as {@code null}.
     */
    MutationPoseComparison compareSameFrame(
            String mutationLabel,
            Structure wtReceptor,
            Ligand wtPose,
            List<LigandContact> wtContacts,
            Structure mutantReceptor,
            Ligand mutantPose,
            List<LigandContact> mutantContacts,
            PosePocketAssignment pocketAssignmentBefore,
            PosePocketAssignment pocketAssignmentAfter,
            Double confidenceBefore,
            Double confidenceAfter
    );
}
